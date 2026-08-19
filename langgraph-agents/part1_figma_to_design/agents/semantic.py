"""Semantic organization agent for important Figma/Web components."""

import json
import os
import re
from typing import Any

from shared.llm_provider import generate_json, model_name, provider_name


IMPORTANT_TERMS = (
    "button", "submit", "login", "sign in", "input", "field", "email", "password",
    "search", "select", "checkbox", "radio", "form", "link", "navigation", "navbar",
    "menu", "header", "save", "delete", "create", "edit", "upload", "download",
    "next", "previous", "confirm", "cancel", "title", "heading", "label", "card",
    "icon", "mat-icon", "material-icons", "loupe", "recherche", "refresh", "filter",
)

TOKEN_FIELDS = (
    "color", "backgroundColor", "borderColor", "spacing", "padding", "gap",
    "fontSize", "fontWeight", "fontFamily", "lineHeight", "borderRadius",
    "width", "height", "opacity",
)
COLOR_FIELDS = {"color", "backgroundColor", "borderColor"}
NUMBER_FIELDS = {
    "spacing", "padding", "gap", "fontSize", "lineHeight", "borderRadius",
    "width", "height", "opacity",
}


def _number(value: Any) -> float | int | None:
    if isinstance(value, (int, float)):
        number = float(value)
    else:
        match = re.search(r"-?\d+(?:\.\d+)?", str(value or ""))
        if not match:
            return None
        number = float(match.group())
    return int(number) if number.is_integer() else round(number, 3)


def _color(value: Any) -> str | None:
    text = str(value or "").strip().lower()
    if not text:
        return None
    if text == "transparent":
        return "transparent"
    if re.fullmatch(r"#[0-9a-f]{3}", text):
        return "#" + "".join(character * 2 for character in text[1:]).upper()
    if re.fullmatch(r"#[0-9a-f]{6}", text):
        return text.upper()
    match = re.fullmatch(
        r"rgba?\(\s*(\d+(?:\.\d+)?)\s*,\s*(\d+(?:\.\d+)?)\s*,\s*(\d+(?:\.\d+)?)"
        r"(?:\s*,\s*(\d+(?:\.\d+)?%?))?\s*\)",
        text,
    )
    if not match:
        return None
    if match.group(4) in ("0", "0.0", "0%"):
        return "transparent"
    channels = [max(0, min(255, round(float(match.group(index))))) for index in range(1, 4)]
    return "#" + "".join(f"{channel:02X}" for channel in channels)


def _font_weight(value: Any) -> int | None:
    number = _number(value)
    if number is not None:
        return int(number)
    names = {
        "thin": 100, "extralight": 200, "light": 300, "normal": 400,
        "regular": 400, "medium": 500, "semibold": 600, "demibold": 600,
        "bold": 700, "extrabold": 800, "black": 900,
    }
    return names.get(re.sub(r"[^a-z]", "", str(value or "").lower()))


def _raw_token(component: dict[str, Any], field: str) -> Any:
    css = component.get("cssProperties") or {}
    aliases = {
        "color": ("color", "foregroundColor", "textColor"),
        "backgroundColor": ("backgroundColor", "fillColor"),
        "borderColor": ("borderColor", "strokeColor"),
        "spacing": ("spacing", "padding", "gap"),
        "padding": ("padding", "spacing"),
    }
    for key in aliases.get(field, (field,)):
        value = css.get(key)
        if value not in (None, ""):
            return value
    if field in ("width", "height"):
        return (component.get("boundingBox") or {}).get(field)
    return None


def _normalized_value(field: str, value: Any) -> Any:
    if field in COLOR_FIELDS:
        return _color(value)
    if field == "fontWeight":
        return _font_weight(value)
    if field in NUMBER_FIELDS:
        return _number(value)
    text = str(value or "").strip()
    return text or None


def _normalized_design_tokens(
    component: dict[str, Any], token_hints: dict[str, Any] | None = None
) -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    hints = token_hints or {}
    for field in TOKEN_FIELDS:
        extracted = _normalized_value(field, _raw_token(component, field))
        inferred = _normalized_value(field, hints.get(field)) if extracted is None else None
        value = extracted if extracted is not None else inferred
        source = "extracted" if extracted is not None else ("llm_visual_inference" if inferred is not None else "missing")
        result[field] = {
            "value": value,
            "unit": "px" if field in NUMBER_FIELDS - {"opacity"} else None,
            "source": source,
            "confidence": 1.0 if source == "extracted" else (0.6 if source == "llm_visual_inference" else 0.0),
        }
    return result


def _text(component: dict[str, Any]) -> str:
    css = component.get("cssProperties") or {}
    return " ".join(str(component.get(key) or "") for key in (
        "canonicalName", "semanticRole", "functionalMeaning", "htmlId", "testIdentifier",
        "cssSelector", "xpath", "tag", "type", "name", "ariaLabel", "text",
        "className", "title", "placeholder",
    )).lower() + " " + " ".join(str(css.get(key) or "") for key in ("type", "text", "role", "className"))


def _important(component: dict[str, Any]) -> bool:
    signals = _text(component)
    return any(term in signals for term in IMPORTANT_TERMS) or bool(
        component.get("htmlId") or component.get("testIdentifier")
    )


def _comparable(component: dict[str, Any]) -> bool:
    box = component.get("boundingBox") or {}
    has_geometry = _number(box.get("width")) not in (None, 0) and _number(box.get("height")) not in (None, 0)
    has_tokens = any(_raw_token(component, field) not in (None, "") for field in TOKEN_FIELDS)
    has_identity = bool(
        component.get("id") or component.get("figmaNodeId") or component.get("htmlId")
        or component.get("canonicalName")
    )
    return has_identity and (has_geometry or has_tokens or _important(component))


def _comparison_priority(component: dict[str, Any]) -> str:
    return "e2e" if _important(component) else "visual"


def _locator(component: dict[str, Any]) -> str:
    for key in ("testIdentifier", "htmlId", "cssSelector", "xpath"):
        value = str(component.get(key) or "").strip()
        if value:
            return value
    return ""


def _locator_candidates(component: dict[str, Any]) -> list[dict[str, str]]:
    candidates: list[dict[str, str]] = []
    for strategy, key in (
        ("testId", "testIdentifier"),
        ("id", "htmlId"),
        ("css", "cssSelector"),
        ("xpath", "xpath"),
        ("aria", "ariaLabel"),
        ("text", "text"),
        ("name", "name"),
        ("title", "title"),
    ):
        value = str(component.get(key) or "").strip()
        if value:
            candidates.append({"strategy": strategy, "value": value})

    signals = _text(component)
    if _is_search_control(component):
        candidates.extend([
            {"strategy": "role", "value": "button[name=/search|recherche/i]"},
            {"strategy": "css", "value": "button[aria-label*='search' i], [role='button'][aria-label*='search' i]"},
            {"strategy": "xpath", "value": "//*[self::mat-icon or contains(@class,'mat-icon') or contains(@class,'material-icons')][normalize-space()='search']/ancestor::*[self::button or @role='button'][1]"},
            {"strategy": "visual", "value": "top-right-toolbar-search-icon"},
        ])
    elif "refresh" in signals or "actualiser" in signals or "rafraich" in signals:
        candidates.extend([
            {"strategy": "css", "value": "button[aria-label*='refresh' i], button[title*='refresh' i]"},
            {"strategy": "xpath", "value": "//*[self::mat-icon or contains(@class,'mat-icon')][normalize-space()='refresh' or normalize-space()='cached' or normalize-space()='sync']/ancestor::*[self::button or @role='button'][1]"},
        ])
    return _dedupe_candidates(candidates)


def _dedupe_candidates(candidates: list[dict[str, str]]) -> list[dict[str, str]]:
    seen: set[tuple[str, str]] = set()
    result: list[dict[str, str]] = []
    for item in candidates:
        key = (str(item.get("strategy") or ""), str(item.get("value") or ""))
        if not key[0] or not key[1] or key in seen:
            continue
        seen.add(key)
        result.append({"strategy": key[0], "value": key[1]})
    return result


def _is_search_control(component: dict[str, Any]) -> bool:
    signals = _text(component)
    role = _role(component)
    return "search" in signals or "recherche" in signals or (
        role == "button" and any(term in signals for term in ("loupe", "magnifier"))
    )


def _visual_position(component: dict[str, Any]) -> dict[str, Any]:
    box = component.get("boundingBox") or {}
    x = _box_number(component, "x")
    y = _box_number(component, "y")
    width = _box_number(component, "width")
    height = _box_number(component, "height")
    center_x = x + width / 2
    center_y = y + height / 2
    region_y = "top" if center_y < 180 else ("bottom" if center_y > 720 else "middle")
    region_x = "left" if center_x < 320 else ("right" if center_x > 1000 else "center")
    return {
        "x": _number(box.get("x")) or 0,
        "y": _number(box.get("y")) or 0,
        "width": _number(box.get("width")) or 0,
        "height": _number(box.get("height")) or 0,
        "region": f"{region_y}-{region_x}",
        "center": {"x": round(center_x, 2), "y": round(center_y, 2)},
    }


def _action_aliases(component: dict[str, Any]) -> list[str]:
    signals = _text(component)
    role = _role(component)
    aliases: list[str] = []
    if _is_search_control(component):
        aliases.extend([
            "search icon", "search button", "global search", "open search",
            "click the search icon", "loupe", "icone recherche", "recherche",
        ])
    if role == "input" and ("search" in signals or "recherche" in signals):
        aliases.extend(["search field", "search input", "search bar", "champ recherche", "barre recherche"])
    if "profile" in signals or "avatar" in signals or "account" in signals or "vplm" in signals:
        aliases.extend(["profile menu", "user menu", "account menu", "avatar"])
    if "logout" in signals or "deconnexion" in signals or "déconnexion" in signals:
        aliases.extend(["logout", "deconnexion", "déconnexion", "sign out"])
    if "save" in signals or "enregistrer" in signals:
        aliases.extend(["save", "save button", "enregistrer"])
    if "refresh" in signals or "actualiser" in signals or "rafraich" in signals:
        aliases.extend(["refresh icon", "refresh button", "actualiser", "rafraichir"])
    raw_text = str(component.get("text") or component.get("ariaLabel") or component.get("canonicalName") or "").strip()
    if raw_text:
        aliases.append(raw_text)
    return sorted({alias for alias in aliases if alias})


def _e2e_hints(page_name: str, component: dict[str, Any]) -> dict[str, Any]:
    role = _role(component)
    aliases = _action_aliases(component)
    if _is_search_control(component) and role != "input":
        primary_intent = "click"
        natural_steps = ["I click the search icon", "I open global search"]
    elif role == "input" and any("search" in alias or "recherche" in alias for alias in aliases):
        primary_intent = "fill"
        natural_steps = ["I enter a search text in the search field", "I type a query in the search bar"]
    elif role == "button":
        primary_intent = "click"
        natural_steps = [f"I click {aliases[0]}"] if aliases else ["I click this button"]
    elif role == "input":
        primary_intent = "fill"
        natural_steps = [f"I enter a value in {aliases[0]}"] if aliases else ["I enter a value in this field"]
    else:
        primary_intent = "assertVisible"
        natural_steps = [f"I should see {aliases[0]}"] if aliases else ["I should see this component"]
    return {
        "page": page_name,
        "primaryIntent": primary_intent,
        "actionAliases": aliases,
        "naturalStepExamples": natural_steps,
        "visualPosition": _visual_position(component),
        "locatorCandidates": _locator_candidates(component),
    }


def _tokens(value: str) -> set[str]:
    return {part for part in re.sub(r"[^a-z0-9]+", " ", value.lower()).split() if len(part) > 1}


def _score(figma: dict[str, Any], web: dict[str, Any]) -> float:
    figma_tokens = _tokens(_text(figma))
    web_tokens = _tokens(_text(web))
    union = figma_tokens | web_tokens
    lexical = len(figma_tokens & web_tokens) / len(union) if union else 0.0
    locator_bonus = 0.2 if _locator(web) and any(token in _locator(web).lower() for token in figma_tokens) else 0.0
    return min(1.0, lexical + locator_bonus)


def _box_number(component: dict[str, Any], key: str) -> float:
    try:
        return float((component.get("boundingBox") or {}).get(key) or 0)
    except (TypeError, ValueError):
        return 0.0


def _collapse_vertical_controls(components: list[dict[str, Any]]) -> list[dict[str, Any]]:
    selected: list[dict[str, Any]] = []
    for component in sorted(components, key=lambda item: (_box_number(item, "y"), _box_number(item, "height"))):
        center = _box_number(component, "y") + _box_number(component, "height") / 2
        match_index = next(
            (
                index for index, existing in enumerate(selected)
                if abs(
                    center
                    - (_box_number(existing, "y") + _box_number(existing, "height") / 2)
                ) <= 12
            ),
            None,
        )
        if match_index is None:
            selected.append(component)
        elif _box_number(component, "height") * _box_number(component, "width") < (
            _box_number(selected[match_index], "height") * _box_number(selected[match_index], "width")
        ):
            selected[match_index] = component
    return sorted(selected, key=lambda item: _box_number(item, "y"))


def _geometry_mappings(page_name: str, figma: list[dict], web: list[dict]) -> list[dict]:
    result: list[dict] = []
    for role in ("input", "checkbox", "radio"):
        figma_controls = _collapse_vertical_controls([item for item in figma if _role(item) == role])
        web_controls = sorted(
            [item for item in web if _role(item) == role],
            key=lambda item: (
                0 if item.get("htmlId") or item.get("testIdentifier") else 1,
                _box_number(item, "y"),
            ),
        )
        if role in ("checkbox", "radio") and web_controls:
            web_controls = web_controls[:1]
            figma_controls = figma_controls[:1]
        for figma_component, web_component in zip(figma_controls, web_controls):
            result.append({
                "uniqueName": _unique_name(page_name, web_component, 1),
                "role": role,
                "usage": _usage(page_name, web_component),
                "importance": _comparison_priority(web_component),
                "figmaComponentId": str(figma_component.get("id")),
                "webComponentId": str(web_component.get("id")),
                "figmaNodeId": str(figma_component.get("figmaNodeId") or ""),
                "webElementId": str(web_component.get("htmlId") or ""),
                "preferredLocator": _locator(web_component),
                "confidence": 0.95,
            })
    return result


def _role(component: dict[str, Any]) -> str:
    signals = _text(component)
    explicit_type = str((component.get("cssProperties") or {}).get("type") or component.get("type") or "").lower()
    tag = str(component.get("tag") or "").lower()
    is_icon = tag in ("mat-icon", "svg", "i") or any(term in signals for term in ("mat-icon", "material-icons", "icon-"))
    if "checkbox" in signals:
        return "checkbox"
    if "radio" in signals:
        return "radio"
    if tag == "a" or explicit_type == "a":
        return "link"
    if tag == "form" or explicit_type == "form":
        return "form"
    if tag == "label" or explicit_type == "label":
        return "text"
    if tag in ("h1", "h2", "h3", "h4", "h5", "h6") or explicit_type in (
        "h1", "h2", "h3", "h4", "h5", "h6"
    ):
        return "heading"
    if tag in ("input", "textarea", "select") or explicit_type in (
        "input", "textarea", "select", "text", "email", "password", "search", "number", "tel", "url"
    ):
        return "input"
    if tag == "button" or explicit_type == "button" or is_icon or any(
        term in signals for term in ("button", "submit", "save", "confirm", "cancel", "delete", "search", "refresh")
    ):
        return "button"
    if any(term in signals for term in ("password", "email", "search", "input", "field")):
        return "input"
    if any(term in signals for term in ("nav", "menu", "header", "link")):
        return "navigation"
    if any(term in signals for term in ("title", "heading", "label")):
        return "content"
    return "container"


def _validated_unique_name(page_name: str, proposed: str, component: dict[str, Any]) -> str:
    page = re.sub(r"[^a-z0-9]+", "_", page_name.lower()).strip("_") or "page"
    role = _role(component)
    candidate = re.sub(r"[^a-z0-9]+", "_", proposed.lower()).strip("_")
    if "." in proposed:
        candidate = re.sub(r"[^a-z0-9]+", "_", proposed.split(".", 1)[1].lower()).strip("_")
    if candidate.startswith(page + "_"):
        candidate = candidate[len(page) + 1:]
    role_words = {
        "input", "button", "checkbox", "radio", "link", "navigation", "heading",
        "text", "image", "form", "container", "label", "field",
    }
    parts = [part for part in candidate.split("_") if part and part not in role_words]
    if not parts:
        raw = str(
            component.get("htmlId") or component.get("testIdentifier")
            or (component.get("cssProperties") or {}).get("text")
            or component.get("canonicalName") or "component"
        )
        parts = [
            part for part in re.sub(r"[^a-z0-9]+", "_", raw.lower()).strip("_").split("_")
            if part and part != page and part not in role_words
        ]
    purpose = "_".join(parts[:6]) or "component"
    return f"{page}.{purpose}_{role}"[:80].rstrip("_")


def _unique_name(page_name: str, web: dict[str, Any], occurrence: int) -> str:
    base = _validated_unique_name(page_name, "", web)
    return base + (f"_{occurrence}" if occurrence > 1 else "")


def _usage(page_name: str, component: dict[str, Any]) -> str:
    signals = _text(component)
    locator = _locator(component)
    if "password" in signals:
        action = "Enter the user's password after the account identifier has been provided"
    elif "email" in signals:
        action = "Enter the user's email address before submitting the form"
    elif "search" in signals and _role(component) == "input":
        action = "Enter the search query before triggering the search action"
    elif any(term in signals for term in ("submit", "login", "sign in")):
        action = "Click after the required credentials are entered to submit the login workflow"
    elif "save" in signals:
        action = "Click after editing the form to persist the changes"
    elif _role(component) == "button":
        action = "Click when the preceding required fields for this workflow are complete"
    elif _role(component) == "navigation":
        action = "Use to navigate to the related application destination"
    else:
        action = "Use as a visual comparison or visible-state assertion target"
    return f"On {page_name}, {action}. Preferred E2E locator: {locator or 'semantic role and text'}."


def _fallback(page_name: str, figma_components: list[dict], web_components: list[dict]) -> list[dict]:
    figma = list(figma_components)
    web = list(web_components)
    used_figma: set[str] = set()
    names: dict[str, int] = {}
    mappings = []
    for web_component in web:
        candidates = [item for item in figma if str(item.get("id")) not in used_figma]
        match = max(candidates, key=lambda item: _score(item, web_component), default=None)
        confidence = _score(match, web_component) if match else 0.0
        if match and confidence < 0.08:
            match = None
        base = _unique_name(page_name, web_component, 1)
        names[base] = names.get(base, 0) + 1
        unique_name = _unique_name(page_name, web_component, names[base])
        if match:
            used_figma.add(str(match.get("id")))
        mappings.append({
            "uniqueName": unique_name,
            "role": _role(web_component),
            "usage": _usage(page_name, web_component),
            "importance": _comparison_priority(web_component),
            "figmaComponentId": str(match.get("id")) if match else None,
            "webComponentId": str(web_component.get("id")),
            "figmaNodeId": str(match.get("figmaNodeId") or "") if match else "",
            "webElementId": str(web_component.get("htmlId") or ""),
            "preferredLocator": _locator(web_component),
            "confidence": round(confidence, 3),
        })
    return mappings


def _llm_mappings(page_name: str, figma: list[dict], web: list[dict], fallback: list[dict], workflow_context: str) -> list[dict]:
    prompt = f"""You map visible UI components for complete design comparison and Playwright E2E automation.
Page: {page_name}
Workflow context: {workflow_context or 'Infer the normal user workflow from names, labels, roles, and locators.'}

Important Figma components: {json.dumps(figma[:40], ensure_ascii=True)}
Important Web components: {json.dumps(web[:40], ensure_ascii=True)}
Deterministic candidate mappings: {json.dumps(fallback, ensure_ascii=True)}

Return only JSON with a `mappings` array. Map every supplied visible/measurable component, including
text, images, cards, sections, containers, navigation, and interactive controls. Every mapping needs:
uniqueName (stable page.role_purpose name shared by Figma and Web), role, usage (workflow-aware,
for example click after credentials are entered), importance (`e2e` for automation targets or
`visual` for design-only elements), figmaComponentId or null,
webComponentId, figmaNodeId, webElementId, preferredLocator, confidence from 0 to 1.
Use only IDs present in the input. Never invent a locator. Prefer Web id/data-testid, then CSS, then XPath.
Each component ID may appear at most once."""
    payload, _ = generate_json(
        prompt,
        model_override=os.getenv("SEMANTIC_MAPPING_MODEL") or os.getenv("SEMANTIC_AGENT_MODEL") or None,
    )
    return payload.get("mappings", fallback)


def organize(page_name: str, figma_components: list[dict], web_components: list[dict], workflow_context: str = "") -> dict:
    comparable_figma = [item for item in figma_components if _comparable(item)]
    comparable_web = [item for item in web_components if _comparable(item)]
    fallback = _fallback(page_name, comparable_figma, comparable_web)
    try:
        proposed = _llm_mappings(page_name, comparable_figma, comparable_web, fallback, workflow_context)
    except Exception:
        proposed = fallback
    geometry = _geometry_mappings(page_name, comparable_figma, comparable_web)
    geometry_by_web = {str(item["webComponentId"]): item for item in geometry}
    proposed_by_web = {str(item.get("webComponentId") or ""): item for item in proposed}
    proposed_by_web.update(geometry_by_web)
    proposed = list(proposed_by_web.values())

    figma_ids = {str(item.get("id")) for item in comparable_figma}
    web_ids = {str(item.get("id")) for item in comparable_web}
    used_figma: set[str] = set()
    used_web: set[str] = set()
    used_names: set[str] = set()
    validated = []
    for item in proposed:
        figma_id = str(item.get("figmaComponentId") or "")
        web_id = str(item.get("webComponentId") or "")
        unique_name = re.sub(r"[^a-z0-9._]+", "_", str(item.get("uniqueName") or "").lower()).strip("_")
        if not web_id or web_id not in web_ids or web_id in used_web or not unique_name or unique_name in used_names:
            continue
        if figma_id and (figma_id not in figma_ids or figma_id in used_figma):
            figma_id = ""
        web_component = next(component for component in comparable_web if str(component.get("id")) == web_id)
        figma_component = next((component for component in comparable_figma if str(component.get("id")) == figma_id), None)
        web_role = _role(web_component)
        figma_role = _role(figma_component) if figma_component else ""
        compatible_roles = (
            not figma_component
            or figma_role == web_role
            or "container" in (figma_role, web_role)
            or {figma_role, web_role} <= {"text", "heading"}
        )
        confidence = max(0.0, min(1.0, float(item.get("confidence") or 0.0)))
        if not compatible_roles or confidence < 0.25:
            figma_id = ""
            figma_component = None
        locator = _locator(web_component)
        validated.append({
            "uniqueName": unique_name,
            "role": web_role,
            "usage": str(item.get("usage") or _usage(page_name, web_component)),
            "importance": _comparison_priority(web_component),
            "figmaComponentId": figma_id or None,
            "webComponentId": web_id,
            "figmaNodeId": str((figma_component or {}).get("figmaNodeId") or ""),
            "webElementId": str(web_component.get("htmlId") or ""),
            "preferredLocator": locator,
            "locatorCandidates": _locator_candidates(web_component),
            "actionAliases": _action_aliases(web_component),
            "e2eHints": _e2e_hints(page_name, web_component),
            "visualPosition": _visual_position(web_component),
            "confidence": confidence if figma_id else 0.0,
        })
        used_web.add(web_id)
        used_names.add(unique_name)
        if figma_id:
            used_figma.add(figma_id)
    return {
        "pageName": page_name,
        "comparableFigmaCount": len(comparable_figma),
        "comparableWebCount": len(comparable_web),
        "mappingCount": len(validated),
        "mappings": validated,
    }


def _locator_details(component: dict[str, Any]) -> dict[str, str]:
    candidates = (
        ("testId", component.get("testIdentifier")),
        ("id", component.get("htmlId")),
        ("css", component.get("cssSelector")),
        ("xpath", component.get("xpath")),
    )
    for strategy, value in candidates:
        if value:
            return {"strategy": strategy, "value": str(value)}
    return {"strategy": "semantic", "value": str(component.get("semanticRole") or component.get("canonicalName") or "")}


def _actions(component: dict[str, Any]) -> list[str]:
    role = _role(component)
    signals = _text(component)
    if role == "input":
        return ["fill", "clear", "assertValue"]
    if role == "button":
        return ["click", "assertVisible", "assertEnabled"]
    if role == "navigation":
        return ["click", "assertDestination"]
    if "checkbox" in signals or "radio" in signals:
        return ["check", "uncheck", "assertChecked"]
    return ["assertVisible", "assertText"]


def _annotation_fallback(page_name: str, component: dict[str, Any]) -> dict[str, Any]:
    usage = _usage(page_name, component)
    return {
        "purpose": str(component.get("functionalMeaning") or component.get("semanticRole") or _role(component)),
        "workflow": page_name.lower().replace(" ", "_"),
        "usage": usage,
        "preconditions": [],
        "expectedOutcome": "The workflow advances or the expected visible state is confirmed.",
        "navigationDestination": "",
        "importance": "critical" if _role(component) in ("input", "button", "navigation") else "supporting",
        "tokenHints": {},
        "uniqueName": "",
        "role": _role(component),
    }


def _hierarchy_summary(page: dict[str, Any], source: str, limit: int = 300) -> list[dict[str, Any]]:
    root = page.get("dom") if source == "WEB" and isinstance(page.get("dom"), dict) else page
    result: list[dict[str, Any]] = []

    def visit(node: Any, depth: int, parent_id: str) -> None:
        if not isinstance(node, dict) or len(result) >= limit:
            return
        node_id = str(node.get("id") or node.get("figmaNodeId") or node.get("cssSelector") or "")
        result.append({
            "id": node_id,
            "parentId": parent_id,
            "depth": depth,
            "type": node.get("type") or node.get("tag") or "",
            "name": node.get("name") or node.get("ariaLabel") or node.get("text") or "",
            "visible": node.get("visible", True),
            "childCount": len(node.get("children") or []),
        })
        for child in node.get("children") or []:
            visit(child, depth + 1, node_id)

    visit(root, 0, "")
    return result


def _llm_annotations(
    page_name: str,
    figma_page: dict[str, Any],
    web_page: dict[str, Any],
    figma_components: list[dict],
    web_components: list[dict],
    mappings: list[dict],
    figma_image_base64: str,
    web_image_base64: str,
    workflow_context: str,
) -> list[dict]:
    compact_figma = [{k: item.get(k) for k in ("id", "canonicalName", "semanticRole", "figmaNodeId", "cssProperties", "boundingBox")} for item in figma_components]
    compact_web = []
    for item in web_components:
        compact = {
            k: item.get(k)
            for k in (
                "id", "canonicalName", "semanticRole", "htmlId", "testIdentifier",
                "cssSelector", "xpath", "cssProperties", "boundingBox", "tag", "type",
                "name", "ariaLabel", "text", "className",
            )
        }
        compact["deterministicE2eHints"] = _e2e_hints(page_name, item)
        compact_web.append(compact)
    page_context = {
        "figma": {
            "name": figma_page.get("name"), "type": figma_page.get("type"),
            "hierarchy": _hierarchy_summary(figma_page, "FIGMA"),
        },
        "web": {
            "url": web_page.get("url"), "pageName": web_page.get("pageName"),
            "hierarchy": _hierarchy_summary(web_page, "WEB"),
        },
    }
    prompt = f"""Analyze this mapped Figma/Web page for Playwright E2E automation and design comparison.
Page: {page_name}
Workflow context: {workflow_context or 'Infer the normal user workflow.'}
Page context: {json.dumps(page_context, ensure_ascii=True)}
Validated mappings: {json.dumps(mappings, ensure_ascii=True)}
Figma component facts: {json.dumps(compact_figma[:40], ensure_ascii=True)}
Web component facts: {json.dumps(compact_web[:40], ensure_ascii=True)}

    Return JSON with `annotations`, one per supplied component. Each item must contain source
(`FIGMA` or `WEB`), componentId, purpose, workflow, usage, preconditions (array), expectedOutcome,
navigationDestination, importance (`critical` or `supporting`), uniqueName, role, and tokenHints
(object). Use a short stable uniqueName shared by the mapped Figma/Web pair in the exact form
`{re.sub(r'[^a-z0-9]+', '_', page_name.lower()).strip('_') or 'page'}.purpose_role`, for example
`login.email_input`, `login.password_input`, `login.submit_button`, or `login.forgot_password_link`.
Never use a workflow sentence as uniqueName. Distinguish controls by visible label, HTML attributes,
Figma layer name, position, and the supplied screenshots. role must be one of input, button,
checkbox, radio, link, navigation, heading, text, image, form, or container. tokenHints
may contain only {json.dumps(TOKEN_FIELDS)} and only when that fact is absent from the component JSON.
Normalize colors to #RRGGBB and dimensions to numeric px values. Explain real workflow order,
for example submit only after credentials are filled. Never invent IDs, locators, colors, sizes,
or style values when they cannot be visually supported. Images are context for visual grouping and
missing-value recovery; extracted JSON facts always remain authoritative."""
    images = [image for image in (figma_image_base64, web_image_base64) if image]
    model_override = (
        os.getenv("SEMANTIC_VISION_MODEL")
        or os.getenv("SEMANTIC_AGENT_VISION_MODEL")
        or os.getenv("SEMANTIC_AGENT_MODEL")
        or None
    )
    payload, _ = generate_json(prompt, images, model_override=model_override)
    return payload.get("annotations", [])


def enrich_pages(
    page_name: str,
    figma_page: dict[str, Any],
    web_page: dict[str, Any],
    figma_components: list[dict],
    web_components: list[dict],
    figma_image_base64: str = "",
    web_image_base64: str = "",
    workflow_context: str = "",
) -> dict:
    organized = organize(page_name, figma_components, web_components, workflow_context)
    mappings = organized["mappings"]
    comparable_figma = [item for item in figma_components if _comparable(item)]
    comparable_web = [item for item in web_components if _comparable(item)]
    try:
        proposed = _llm_annotations(
            page_name, figma_page, web_page, comparable_figma[:80], comparable_web[:80], mappings,
            figma_image_base64, web_image_base64, workflow_context,
        )
    except Exception:
        proposed = []

    valid_ids = {
        "FIGMA": {str(item.get("id")) for item in comparable_figma},
        "WEB": {str(item.get("id")) for item in comparable_web},
    }
    annotations: dict[tuple[str, str], dict[str, Any]] = {}
    for item in proposed:
        source = str(item.get("source") or "").upper()
        component_id = str(item.get("componentId") or "")
        if source in valid_ids and component_id in valid_ids[source]:
            annotations[(source, component_id)] = item

    used_semantic_names: set[str] = set()
    allowed_roles = {
        "input", "button", "checkbox", "radio", "link", "navigation",
        "heading", "text", "image", "form", "container",
    }
    for mapping in mappings:
        web_id = str(mapping.get("webComponentId") or "")
        figma_id = str(mapping.get("figmaComponentId") or "")
        proposal = annotations.get(("WEB", web_id)) or annotations.get(("FIGMA", figma_id)) or {}
        web_component = next(
            (component for component in comparable_web if str(component.get("id")) == web_id), {}
        )
        proven_role = _role(web_component)
        proposed_name = _validated_unique_name(
            page_name, str(proposal.get("uniqueName") or mapping.get("uniqueName") or ""), web_component
        )
        if proposed_name in used_semantic_names:
            occurrence = 2
            while f"{proposed_name}_{occurrence}" in used_semantic_names:
                occurrence += 1
            proposed_name = f"{proposed_name}_{occurrence}"
        if proposed_name:
            mapping["uniqueName"] = proposed_name
        used_semantic_names.add(str(mapping.get("uniqueName") or ""))
        if proven_role in allowed_roles:
            mapping["role"] = proven_role
        if proposal.get("usage"):
            mapping["usage"] = str(proposal["usage"])

    mapping_by_id: dict[str, dict] = {}
    for mapping in mappings:
        for key in ("figmaComponentId", "webComponentId"):
            if mapping.get(key):
                mapping_by_id[str(mapping[key])] = mapping

    def enrich(source: str, component: dict[str, Any]) -> dict[str, Any]:
        component_id = str(component.get("id"))
        annotation = _annotation_fallback(page_name, component)
        annotation.update({k: v for k, v in annotations.get((source, component_id), {}).items() if k in annotation})
        mapping = mapping_by_id.get(component_id, {})
        result = {
            "componentId": component_id,
            "uniqueName": mapping.get("uniqueName") or component.get("canonicalName"),
            "source": source,
            "role": mapping.get("role") or _role(component),
            "purpose": annotation["purpose"],
            "workflow": annotation["workflow"],
            "usage": annotation["usage"],
            "preconditions": annotation["preconditions"],
            "expectedOutcome": annotation["expectedOutcome"],
            "navigationDestination": annotation["navigationDestination"],
            "importance": annotation["importance"],
            "actions": _actions(component),
            "actionAliases": _action_aliases(component),
            "e2eHints": _e2e_hints(page_name, component),
            "facts": {
                "designTokens": _normalized_design_tokens(component, annotation.get("tokenHints")),
                "rawDesignTokens": component.get("cssProperties") or {},
                "boundingBox": component.get("boundingBox") or {},
                "visualPosition": _visual_position(component),
            },
        }
        if source == "FIGMA":
            result["figmaNodeId"] = component.get("figmaNodeId") or ""
        else:
            result["webElementId"] = component.get("htmlId") or ""
            result["preferredLocator"] = _locator_details(component)
            result["locatorAlternatives"] = _locator_candidates(component)
        return result

    enriched_figma = [enrich("FIGMA", item) for item in comparable_figma]
    enriched_web = [enrich("WEB", item) for item in comparable_web]
    return {
        "schemaVersion": "2.0",
        "pageName": page_name,
        "generatedBy": f"{provider_name()}:{model_name(True)}" if proposed else "deterministic-fallback",
        "figmaPage": {"name": figma_page.get("name") or page_name, "components": enriched_figma},
        "webPage": {"name": web_page.get("pageName") or page_name, "url": web_page.get("url") or "", "components": enriched_web},
        "mappings": mappings,
        "e2eCatalog": [item for item in enriched_web if item.get("importance") == "critical"],
    }
