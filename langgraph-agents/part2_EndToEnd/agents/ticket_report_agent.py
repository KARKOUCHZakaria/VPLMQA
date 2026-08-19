import json
import os
import re
from typing import Any

from shared.llm_provider import generate_json, model_name, provider_name


def _compact(value: Any, limit: int = 12000) -> str:
    text = json.dumps(value, ensure_ascii=False, indent=2) if not isinstance(value, str) else value
    return text[:limit]


def _fallback_title(payload: dict[str, Any]) -> str:
    failed_step = payload.get("failedStep") or {}
    error = str(failed_step.get("error") or payload.get("errorMessage") or payload.get("logs") or "")
    step_text = str(failed_step.get("text") or payload.get("scenarioName") or "scenario")
    page = str(payload.get("pageName") or "").strip()
    location = f"{page} page" if page else "page"

    visible_match = re.search(r"(?:should\s+see|see)\s+['\"]?(.+?)['\"]?$", step_text, flags=re.IGNORECASE)
    if visible_match:
        expected = visible_match.group(1).strip(" '\".")
        return f"{location.capitalize()} does not display {expected[:75]}"

    click_match = re.search(r"(?:click|select|press)\s+(?:on\s+)?['\"]?(.+?)['\"]?$", step_text, flags=re.IGNORECASE)
    if click_match:
        control = click_match.group(1).strip(" '\".")
        return f"{control[:65].capitalize()} cannot be selected on the {location}"

    navigation_match = re.search(r"(?:go|navigate|open)\s+to\s+['\"]?(.+?)['\"]?$", step_text, flags=re.IGNORECASE)
    if navigation_match:
        destination = navigation_match.group(1).strip(" '\".")
        return f"Unable to open {destination[:80]}"

    input_match = re.search(r"(?:enter|type|fill)\s+(.+)", step_text, flags=re.IGNORECASE)
    if input_match:
        field = input_match.group(1).strip(" '\".")
        return f"Unable to enter {field[:75]}"

    scenario = str(payload.get("scenarioName") or "").strip()
    if scenario:
        return f"{scenario[:90]} cannot be completed"
    return "User workflow cannot be completed"


def _expected_visible_in_evidence(payload: dict[str, Any]) -> bool:
    failed_step = payload.get("failedStep") or {}
    step_text = str(failed_step.get("text") or "")
    expected_match = re.search(r"(?:should\s+see|see)\s+['\"]?(.+?)['\"]?$", step_text, flags=re.IGNORECASE)
    if not expected_match:
        expected_match = re.search(r"Expected\s+(?:search\s+result|text)\s+['\"](.+?)['\"]", str(failed_step.get("error") or payload.get("errorMessage") or payload.get("logs") or ""), flags=re.IGNORECASE)
    if not expected_match:
        return False
    expected = re.sub(r"[^a-z0-9]+", "", expected_match.group(1).lower())
    if not expected:
        return False
    evidence = " ".join(
        str(value or "")
        for value in (
            payload.get("logs"),
            payload.get("errorMessage"),
            failed_step.get("error"),
            payload.get("visibleText"),
            payload.get("visibleTextSample"),
        )
    )
    normalized_evidence = re.sub(r"[^a-z0-9]+", "", evidence.lower())
    return expected in normalized_evidence


def _automation_validation_description(payload: dict[str, Any]) -> str:
    failed_step = payload.get("failedStep") or {}
    step_text = failed_step.get("text") or payload.get("scenarioName") or "the scenario"
    error = failed_step.get("error") or payload.get("errorMessage") or "The automated validation stopped before completion."
    steps = payload.get("steps") or []
    reproduction = "\n".join(
        f"{index + 1}. {step.get('text', '').strip()}".strip()
        for index, step in enumerate(steps)
    )
    evidence = next((line.strip() for line in str(error).splitlines() if line.strip()), "")
    return "\n".join(
        part
        for part in [
            "Summary",
            "The tested workflow reaches the results page, but the automated validation could not reliably read the expected value from the results table.",
            "",
            "Impact",
            "The application behavior should be reviewed with the screenshot before creating a product bug, because the visible evidence may already contain the expected result.",
            "",
            "Expected result",
            "The automation should validate the expected value inside the visible result rows of the table.",
            "",
            "Actual result",
            f'The workflow stops at "{step_text}" even though the page may display matching result data.',
            "",
            "How to reproduce",
            reproduction,
            "",
            "Context",
            f"Feature: {payload.get('featureName', 'Unknown feature')}",
            f"Scenario: {payload.get('scenarioName', 'Unknown scenario')}",
            f"Execution: {payload.get('executionId', '')}",
            "",
            "Technical evidence",
            evidence[:500],
        ]
        if part
    )


def _fallback_description(payload: dict[str, Any]) -> str:
    failed_step = payload.get("failedStep") or {}
    step_text = failed_step.get("text") or payload.get("scenarioName") or "the scenario"
    error = failed_step.get("error") or payload.get("errorMessage") or "The workflow stopped before completion."
    title = _fallback_title(payload)
    evidence = next((line.strip() for line in str(error).splitlines() if line.strip()), "")
    steps = payload.get("steps") or []
    reproduction = "\n".join(
        f"{index + 1}. {step.get('text', '').strip()}".strip()
        for index, step in enumerate(steps)
    )
    expected_content = re.sub(
        r"^.*(?:should see|see)\s+",
        "",
        str(step_text),
        flags=re.IGNORECASE,
    ).strip(" '\".")
    expected = (
        f"The page should display {expected_content}."
        if expected_content != str(step_text)
        else f'The tester should be able to complete "{step_text}" and continue the workflow.'
    )
    return "\n".join(
        part
        for part in [
            "Summary",
            f"A tester cannot complete the expected workflow because the {title[0].lower() + title[1:]}.",
            "",
            "Impact",
            "This blocks validation of the tested user journey and needs developer review.",
            "",
            "Expected result",
            expected,
            "",
            "Actual result",
            f'The workflow stops at "{step_text}".',
            "",
            "How to reproduce",
            reproduction,
            "",
            "Context",
            f"Feature: {payload.get('featureName', 'Unknown feature')}",
            f"Scenario: {payload.get('scenarioName', 'Unknown scenario')}",
            f"Execution: {payload.get('executionId', '')}",
            "",
            "Technical evidence",
            evidence[:500],
        ]
        if part
    )


def _sanitize_report(report: dict[str, Any], payload: dict[str, Any]) -> dict[str, Any]:
    title = str(report.get("title") or "").strip()
    description = str(report.get("description") or "").strip()
    severity = str(report.get("severity") or payload.get("severity") or "MEDIUM").strip().upper()
    if severity not in {"LOW", "MEDIUM", "HIGH", "CRITICAL"}:
        severity = "MEDIUM"
    title_is_technical = bool(re.search(
        r"(status:\s*failed|locator\.|timeout\s*\d*ms|traceback|exception:|http error|scenario failed|test failed)",
        title,
        flags=re.IGNORECASE,
    ))
    if not title or title_is_technical:
        title = _fallback_title(payload)
    description_is_technical = bool(re.match(r"\s*(status:|exception:|traceback)", description, flags=re.IGNORECASE))
    if not description or description_is_technical:
        description = _fallback_description(payload)
    unsupported_product_claim = bool(re.search(
        r"(column|colonne|classe).{0,80}(missing|not\s+show|not\s+display|absent|manquante|n.?est\s+pas\s+visible)",
        f"{title}\n{description}",
        flags=re.IGNORECASE | re.DOTALL,
    ))
    if unsupported_product_claim and _expected_visible_in_evidence(payload):
        title = "Automated validation cannot read the expected value from the results table"
        description = _automation_validation_description(payload)
        severity = "LOW" if severity in {"MEDIUM", "HIGH", "CRITICAL"} else severity
    return {
        "title": title[:140],
        "description": description,
        "severity": severity,
        "expectedResult": str(report.get("expectedResult") or "").strip(),
        "actualResult": str(report.get("actualResult") or "").strip(),
        "rootCauseHint": str(report.get("rootCauseHint") or "").strip(),
        "provider": report.get("provider") or provider_name(),
        "model": report.get("model") or model_name(False),
    }


def _report_models() -> list[str | None]:
    configured = os.getenv("TICKET_REPORT_MODELS", "").strip()
    if configured:
        return [item.strip() for item in configured.split(",") if item.strip()]
    single = os.getenv("TICKET_REPORT_MODEL", "").strip()
    if single:
        return [single]
    return [None]


def generate_ticket_report(payload: dict[str, Any]) -> dict[str, Any]:
    prompt = f"""
You are a senior QA report agent. Analyze this E2E test failure and write an Azure DevOps bug ticket draft.

Rules:
- Return only valid JSON.
- Do not use generic titles like "Status: FAILED", "Test failed", or "Scenario failed".
- Write as a QA engineer reporting an observable product problem, not as a test framework.
- The title must describe what the user cannot do or what the page displays incorrectly.
- Never put Playwright/Selenium terminology, exception classes, HTTP codes, timeouts, selectors, IDs, logs, or Gherkin keywords in the title.
- Prefer titles such as "Login page does not display the expected error message", "Create ticket button does not respond", or "User is redirected to the wrong page after signing in".
- Do not claim a product defect that the evidence cannot support. For deliberately impossible assertions, describe the missing expected content neutrally.
- If the screenshot or visible text sample already contains the expected value, report an automation validation issue, not a product defect.
- Never say a column or value is missing when the evidence text contains it.
- The description must use natural sentences and explain the user journey. Avoid repeating the same failed step in every section.
- "Expected result" and "Actual result" must describe visible behavior, not pass/fail status.
- Reproduction steps must be short user actions without Given/When/Then prefixes.
- Put raw logs only as a very short "Technical evidence" line at the bottom, never as the main description.
- Do not invent credentials, secrets, stack traces, or business facts not present in the evidence.
- Use this JSON shape:
{{
  "title": "specific problem found",
  "description": "Summary\\n...\\n\\nImpact\\n...\\n\\nExpected result\\n...\\n\\nActual result\\n...\\n\\nHow to reproduce\\n1. ...\\n\\nContext\\nFeature: ...",
  "severity": "LOW|MEDIUM|HIGH|CRITICAL",
  "expectedResult": "...",
  "actualResult": "...",
  "rootCauseHint": "short technical hint if obvious"
}}

Evidence:
{_compact(payload)}
""".strip()
    errors: list[str] = []
    for model in _report_models():
        try:
            report, response = generate_json(prompt, model_override=model)
            report["provider"] = response.provider
            report["model"] = response.model
            return _sanitize_report(report, payload)
        except Exception as exception:
            errors.append(f"{model or 'default'}: {exception}")

    try:
        fallback = _sanitize_report({}, payload)
        fallback["provider"] = "deterministic-fallback"
        fallback["model"] = "none"
        fallback["error"] = " | ".join(errors)
        return fallback
    except Exception as exception:
        return {
            "title": _fallback_title(payload),
            "description": _fallback_description(payload),
            "severity": str(payload.get("severity") or "MEDIUM").upper(),
            "provider": "deterministic-fallback",
            "model": "none",
            "error": str(exception),
        }
