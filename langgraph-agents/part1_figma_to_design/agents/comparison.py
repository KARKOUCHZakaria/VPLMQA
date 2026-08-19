"""Multimodal visual explanation agent for Figma and Web PNGs."""

import asyncio
import json
import base64
import io
import struct
import re

import numpy as np
from PIL import Image
from scipy import ndimage

from part1_figma_to_design.state import FigmaToDesignState
from shared.llm_provider import generate_json


TOKEN_PAIRS = (
    ("color", "figma_color", "code_color"),
    ("spacing", "figma_spacing", "code_spacing"),
    ("fontSize", "figma_font_size", "code_font_size"),
    ("fontWeight", "figma_font_weight", "code_font_weight"),
    ("borderRadius", "figma_border_radius", "code_border_radius"),
    ("width", "figma_width", "code_width"),
    ("height", "figma_height", "code_height"),
)


NUMERIC_TOLERANCES = {
    "spacing": 2,
    "fontSize": 1,
    "borderRadius": 2,
    "width": 6,
    "height": 6,
}

MAX_VISUAL_REGIONS = 8
VISUAL_COMPARE_WIDTH = 900
PIXEL_DELTA_THRESHOLD = 40
MIN_REGION_RATIO = 0.0012
MIN_CHANGED_RATIO = 0.006


def _image_size(encoded: str) -> dict[str, int] | None:
    try:
        data = base64.b64decode(encoded)
        if data[:8] != b"\x89PNG\r\n\x1a\n":
            return None
        width, height = struct.unpack(">II", data[16:24])
        return {"width": width, "height": height}
    except Exception:
        return None


def _image_bytes(encoded: str) -> bytes:
    text = (encoded or "").strip()
    if text.startswith("data:image"):
        text = text.split(",", 1)[-1]
    return base64.b64decode(text)


def _decode_image(encoded: str) -> Image.Image | None:
    try:
        image = Image.open(io.BytesIO(_image_bytes(encoded)))
        if image.mode in {"RGBA", "LA"}:
            background = Image.new("RGBA", image.size, (255, 255, 255, 255))
            background.alpha_composite(image.convert("RGBA"))
            return background.convert("RGB")
        return image.convert("RGB")
    except Exception:
        return None


def _resize_to_width(image: Image.Image, target_width: int) -> Image.Image:
    if image.width == target_width:
        return image
    ratio = target_width / max(1, image.width)
    target_height = max(1, int(round(image.height * ratio)))
    return image.resize((target_width, target_height), Image.Resampling.LANCZOS)


def _common_canvas(left: Image.Image, right: Image.Image) -> tuple[Image.Image, Image.Image]:
    target_width = min(VISUAL_COMPARE_WIDTH, left.width, right.width)
    left = _resize_to_width(left, target_width)
    right = _resize_to_width(right, target_width)
    target_height = min(max(left.height, right.height), max(1, int(target_width * 3.2)))

    def paste_on_white(image: Image.Image) -> Image.Image:
        canvas = Image.new("RGB", (target_width, target_height), (255, 255, 255))
        canvas.paste(image.crop((0, 0, target_width, min(image.height, target_height))), (0, 0))
        return canvas

    return paste_on_white(left), paste_on_white(right)


def _corner_background(array: np.ndarray) -> np.ndarray:
    height, width = array.shape[:2]
    patch = max(8, min(32, height // 12, width // 12))
    samples = np.concatenate(
        [
            array[:patch, :patch],
            array[:patch, width - patch:],
            array[height - patch:, :patch],
            array[height - patch:, width - patch:],
        ],
        axis=0,
    ).reshape(-1, 3)
    return np.median(samples, axis=0)


def _foreground_crop(image: Image.Image) -> tuple[Image.Image, tuple[int, int, int, int]]:
    array = np.asarray(image.convert("RGB"), dtype=np.int16)
    height, width = array.shape[:2]
    background = _corner_background(array)
    color_distance = np.sqrt(((array - background) ** 2).sum(axis=2))

    gray = array.mean(axis=2)
    edge = np.zeros((height, width), dtype=np.float32)
    edge[:, 1:] = np.maximum(edge[:, 1:], np.abs(gray[:, 1:] - gray[:, :-1]))
    edge[1:, :] = np.maximum(edge[1:, :], np.abs(gray[1:, :] - gray[:-1, :]))

    mask = (color_distance > 12) | (edge > 10)
    mask = ndimage.binary_opening(mask, structure=np.ones((2, 2), dtype=bool))
    mask = ndimage.binary_closing(mask, structure=np.ones((5, 5), dtype=bool))

    ys, xs = np.where(mask)
    if xs.size < 50:
        return image, (0, 0, width, height)

    padding = max(16, int(max(width, height) * 0.035))
    x1 = max(0, int(xs.min()) - padding)
    y1 = max(0, int(ys.min()) - padding)
    x2 = min(width, int(xs.max()) + padding + 1)
    y2 = min(height, int(ys.max()) + padding + 1)

    if (x2 - x1) >= width * 0.97 and (y2 - y1) >= height * 0.97:
        return image, (0, 0, width, height)
    return image.crop((x1, y1, x2, y2)), (x1, y1, x2, y2)


def _fit_to_comparison_canvas(
    image: Image.Image,
    target_width: int,
    target_height: int,
) -> tuple[Image.Image, dict[str, float]]:
    scale = target_width / max(1, image.width)
    resized_height = max(1, int(round(image.height * scale)))
    resized = image.resize((target_width, resized_height), Image.Resampling.LANCZOS)
    canvas = Image.new("RGB", (target_width, target_height), (255, 255, 255))
    y_offset = max(0, (target_height - resized_height) // 2)
    canvas.paste(resized.crop((0, 0, target_width, min(resized_height, target_height))), (0, y_offset))
    return canvas, {"scale": scale, "xOffset": 0.0, "yOffset": float(y_offset)}


def _aligned_canvases(
    figma_image: Image.Image,
    web_image: Image.Image,
) -> tuple[Image.Image, Image.Image, dict, dict]:
    figma_crop, figma_box = _foreground_crop(figma_image)
    web_crop, web_box = _foreground_crop(web_image)
    target_width = min(VISUAL_COMPARE_WIDTH, max(360, min(figma_crop.width, web_crop.width)))
    figma_ratio = figma_crop.height / max(1, figma_crop.width)
    web_ratio = web_crop.height / max(1, web_crop.width)
    target_height = max(1, int(round(target_width * max(figma_ratio, web_ratio))))
    target_height = min(target_height, max(1, int(target_width * 3.2)))

    figma_canvas, figma_transform = _fit_to_comparison_canvas(figma_crop, target_width, target_height)
    web_canvas, web_transform = _fit_to_comparison_canvas(web_crop, target_width, target_height)
    figma_transform["cropBox"] = figma_box
    web_transform["cropBox"] = web_box
    return figma_canvas, web_canvas, figma_transform, web_transform


def _normalized_region(box: tuple[int, int, int, int], width: int, height: int) -> dict[str, float]:
    x1, y1, x2, y2 = box
    return {
        "x": round(max(0, x1) / max(1, width), 6),
        "y": round(max(0, y1) / max(1, height), 6),
        "width": round(max(1, x2 - x1) / max(1, width), 6),
        "height": round(max(1, y2 - y1) / max(1, height), 6),
    }


def _normalized_region_from_aligned_box(
    box: tuple[int, int, int, int],
    transform: dict,
    original_width: int,
    original_height: int,
) -> dict[str, float] | None:
    x1, y1, x2, y2 = box
    scale = max(0.000001, float(transform.get("scale") or 1.0))
    x_offset = float(transform.get("xOffset") or 0.0)
    y_offset = float(transform.get("yOffset") or 0.0)
    crop_x1, crop_y1, crop_x2, crop_y2 = transform.get("cropBox") or (0, 0, original_width, original_height)

    mapped_x1 = crop_x1 + (x1 - x_offset) / scale
    mapped_y1 = crop_y1 + (y1 - y_offset) / scale
    mapped_x2 = crop_x1 + (x2 - x_offset) / scale
    mapped_y2 = crop_y1 + (y2 - y_offset) / scale

    mapped_x1 = max(crop_x1, min(crop_x2, mapped_x1))
    mapped_y1 = max(crop_y1, min(crop_y2, mapped_y1))
    mapped_x2 = max(crop_x1, min(crop_x2, mapped_x2))
    mapped_y2 = max(crop_y1, min(crop_y2, mapped_y2))
    if mapped_x2 - mapped_x1 < 2 or mapped_y2 - mapped_y1 < 2:
        return None
    return _normalized_region(
        (int(mapped_x1), int(mapped_y1), int(mapped_x2), int(mapped_y2)),
        original_width,
        original_height,
    )


def _component_region(row: dict, side: str, image_size: dict[str, int] | None) -> dict[str, float] | None:
    if not image_size:
        return None
    prefix = "figma" if side == "figma" else "code"
    x = _number(row.get(f"{prefix}_x"))
    y = _number(row.get(f"{prefix}_y"))
    width = _number(row.get(f"{prefix}_width"))
    height = _number(row.get(f"{prefix}_height"))
    if x is None or y is None or width is None or height is None or width <= 0 or height <= 0:
        return None
    return {
        "x": round(max(0.0, x / max(1, image_size["width"])), 6),
        "y": round(max(0.0, y / max(1, image_size["height"])), 6),
        "width": round(min(1.0, width / max(1, image_size["width"])), 6),
        "height": round(min(1.0, height / max(1, image_size["height"])), 6),
    }


def _region_overlap(a: dict[str, float] | None, b: dict[str, float] | None) -> float:
    if not a or not b:
        return 0.0
    ax2 = a["x"] + a["width"]
    ay2 = a["y"] + a["height"]
    bx2 = b["x"] + b["width"]
    by2 = b["y"] + b["height"]
    overlap_w = max(0.0, min(ax2, bx2) - max(a["x"], b["x"]))
    overlap_h = max(0.0, min(ay2, by2) - max(a["y"], b["y"]))
    overlap = overlap_w * overlap_h
    area = max(0.000001, min(a["width"] * a["height"], b["width"] * b["height"]))
    return overlap / area


def _component_for_region(region: dict[str, float], rows: list[dict], side: str, image_size: dict[str, int] | None) -> str:
    best_name = ""
    best_score = 0.0
    for row in rows:
        component_region = _component_region(row, side, image_size)
        score = _region_overlap(region, component_region)
        if score > best_score:
            best_score = score
            best_name = str(row.get("component") or row.get("uniqueName") or "")
    return best_name if best_score >= 0.15 else "visual area"


def _deterministic_visual_differences(state) -> list[dict]:
    figma_image = _decode_image(state.get("figma_image_base64", ""))
    web_image = _decode_image(state.get("web_image_base64", ""))
    if figma_image is None or web_image is None:
        return []

    figma_size = {"width": figma_image.width, "height": figma_image.height}
    web_size = {"width": web_image.width, "height": web_image.height}
    figma_canvas, web_canvas, figma_transform, web_transform = _aligned_canvases(figma_image, web_image)
    figma_array = np.asarray(figma_canvas, dtype=np.int16)
    web_array = np.asarray(web_canvas, dtype=np.int16)

    delta = np.abs(figma_array - web_array).mean(axis=2)
    mask = delta > PIXEL_DELTA_THRESHOLD
    if not mask.any():
        return []

    structure = np.ones((5, 5), dtype=bool)
    mask = ndimage.binary_opening(mask, structure=np.ones((2, 2), dtype=bool))
    mask = ndimage.binary_dilation(mask, structure=structure, iterations=2)
    mask = ndimage.binary_fill_holes(mask)
    changed_ratio = float(mask.mean())
    if changed_ratio < MIN_CHANGED_RATIO:
        return []
    if changed_ratio > 0.18:
        return [{
            "component": "Page visual appearance",
            "category": "visual",
            "figma": "Figma screenshot",
            "web": "Web screenshot",
            "explanation": "Large visual difference detected between the reference and implementation screenshots after content alignment.",
            "figmaRegion": {"x": 0, "y": 0, "width": 1, "height": 1},
            "webRegion": {"x": 0, "y": 0, "width": 1, "height": 1},
        }]

    labels, count = ndimage.label(mask)
    height, width = mask.shape
    min_area = max(45, int(width * height * MIN_REGION_RATIO))
    rows = state.get("rows", [])
    candidates = []

    for label_index in range(1, count + 1):
        ys, xs = np.where(labels == label_index)
        if xs.size < min_area:
            continue
        x1 = max(0, int(xs.min()) - 6)
        y1 = max(0, int(ys.min()) - 6)
        x2 = min(width, int(xs.max()) + 7)
        y2 = min(height, int(ys.max()) + 7)
        if (x2 - x1) < max(24, int(width * 0.025)) or (y2 - y1) < max(14, int(height * 0.012)):
            continue
        area = (x2 - x1) * (y2 - y1)
        if area < min_area:
            continue
        figma_region = _normalized_region_from_aligned_box((x1, y1, x2, y2), figma_transform, figma_size["width"], figma_size["height"])
        web_region = _normalized_region_from_aligned_box((x1, y1, x2, y2), web_transform, web_size["width"], web_size["height"])
        if not figma_region and not web_region:
            continue
        component = _component_for_region(figma_region, rows, "figma", figma_size)
        candidates.append({
            "component": component,
            "category": "visual",
            "figma": "reference screenshot",
            "web": "implemented screenshot",
            "explanation": "Visible screenshot difference detected after content alignment and noise filtering.",
            "figmaRegion": figma_region,
            "webRegion": web_region,
            "area": area,
        })

    candidates.sort(key=lambda item: item["area"], reverse=True)
    return [{key: value for key, value in item.items() if key != "area"} for item in candidates[:MAX_VISUAL_REGIONS]]


def _missing(value) -> bool:
    if value is None:
        return True
    text = str(value).strip()
    return text == "" or text.lower() in {"none", "null", "nan", "undefined"}


def _number(value):
    if isinstance(value, (int, float)):
        return float(value)
    match = re.search(r"-?\d+(?:\.\d+)?", str(value or ""))
    return float(match.group()) if match else None


def _normalized_color(value):
    text = str(value or "").strip().lower()
    if not text:
        return ""
    if text == "transparent":
        return "transparent"
    if text.startswith("#"):
        hex_value = re.sub(r"[^0-9a-f]", "", text[1:])
        if len(hex_value) == 3:
            hex_value = "".join(channel * 2 for channel in hex_value)
        return f"#{hex_value[:6].upper()}" if len(hex_value) >= 6 else text.upper()
    match = re.match(r"rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)", text)
    if match:
        return "#" + "".join(f"{max(0, min(255, round(float(channel)))):02X}" for channel in match.groups())
    return text


def _significant_difference(category: str, figma_value, web_value):
    if _missing(figma_value) or _missing(web_value):
        return None
    if category == "color":
        figma_color = _normalized_color(figma_value)
        web_color = _normalized_color(web_value)
        return None if figma_color == web_color else {
            "category": category,
            "figma": figma_color,
            "web": web_color,
            "delta": None,
        }
    if category == "fontWeight":
        figma_weight = str(figma_value).strip().lower()
        web_weight = str(web_value).strip().lower()
        aliases = {"normal": "400", "regular": "400", "medium": "500", "bold": "700"}
        figma_weight = aliases.get(figma_weight, figma_weight)
        web_weight = aliases.get(web_weight, web_weight)
        return None if figma_weight == web_weight else {
            "category": category,
            "figma": figma_weight,
            "web": web_weight,
            "delta": None,
        }
    figma_number = _number(figma_value)
    web_number = _number(web_value)
    if figma_number is not None and web_number is not None:
        tolerance = max(NUMERIC_TOLERANCES.get(category, 0), abs(figma_number) * 0.03 if category in {"width", "height"} else 0)
        delta = web_number - figma_number
        if abs(delta) <= tolerance:
            return None
        return {
            "category": category,
            "figma": figma_value,
            "web": web_value,
            "delta": round(delta, 3),
            "tolerance": round(tolerance, 3),
        }
    return None if str(figma_value).strip().lower() == str(web_value).strip().lower() else {
        "category": category,
        "figma": figma_value,
        "web": web_value,
        "delta": None,
    }


def _measured_differences(state):
    predictions = {item.get("index"): item for item in state.get("predictions", [])}
    differences = []
    for index, row in enumerate(state.get("rows", [])):
        token_differences = []
        for category, figma_key, web_key in TOKEN_PAIRS:
            difference = _significant_difference(category, row.get(figma_key), row.get(web_key))
            if difference:
                token_differences.append(difference)
        prediction = predictions.get(index, {})
        confidence = float(row.get("mapping_confidence") or 0)
        if prediction.get("decision") == "token-aware-calibrated":
            measured_match = bool(prediction.get("match"))
            measured_probability = float(prediction.get("matchProbability") or 0.0)
        else:
            measured_match = not token_differences and confidence >= 0.45
            measured_probability = 0.95 if measured_match else max(0.05, 0.75 - len(token_differences) * 0.12)
        differences.append({
            "index": index,
            "component": row.get("component", f"component-{index + 1}"),
            "canonicalName": row.get("canonicalName") or row.get("canonical_name"),
            "locator": row.get("web_locator") or row.get("locator") or row.get("selector"),
            "match": measured_match,
            "matchProbability": round(measured_probability, 6),
            "modelComponent": prediction.get("modelComponent"),
            "modelMatch": prediction.get("match"),
            "modelMatchProbability": prediction.get("matchProbability"),
            "rawModelMatch": prediction.get("rawModelMatch"),
            "rawModelProbability": prediction.get("rawModelProbability"),
            "mappingConfidence": confidence,
            "mappingReason": row.get("mapping_reason"),
            "tokenDifferences": token_differences,
            "decisionReason": prediction.get("decisionReason"),
            "provenance": prediction.get("decision") or "measured-extraction-with-tolerances",
        })
    return differences


def _fallback_report(state):
    measured = _measured_differences(state)
    visual = _deterministic_visual_differences(state)
    mismatches = [item for item in measured if not item.get("match")]
    severity = "high" if len(mismatches) > 3 or visual else "medium" if mismatches else "none"
    visual_text = f" and {len(visual)} screenshot-level visual difference" if visual else ""
    return {
        "summary": f"{len(mismatches)} component token mismatches{visual_text} were detected.",
        "severity": severity,
        "componentDifferences": measured,
        "componentPredictions": [
            {
                "index": item.get("index"),
                "component": item.get("component"),
                "modelComponent": str(item.get("modelComponent") or item.get("canonicalName") or item.get("component") or ""),
                "match": bool(item.get("match")),
                "matchProbability": item.get("matchProbability"),
                "rawModelMatch": item.get("rawModelMatch"),
                "rawModelProbability": item.get("rawModelProbability"),
                "mappingConfidence": item.get("mappingConfidence"),
            }
            for item in measured
        ],
        "visualDifferences": visual,
        "imageMetrics": {
            "figma": _image_size(state.get("figma_image_base64", "")),
            "web": _image_size(state.get("web_image_base64", "")),
        },
        "recommendations": ["Review the mapped token values and visual regions across the complete page."],
        "provider": "deterministic",
        "provenance": {"tokens": "extracted", "prediction": "local-ml", "explanation": "deterministic"},
    }


def _validated_region(value):
    if not isinstance(value, dict):
        return None
    try:
        region = {key: float(value[key]) for key in ("x", "y", "width", "height")}
    except (KeyError, TypeError, ValueError):
        return None
    region["x"] = max(0.0, min(1.0, region["x"]))
    region["y"] = max(0.0, min(1.0, region["y"]))
    region["width"] = max(0.02, min(1.0 - region["x"], region["width"]))
    region["height"] = max(0.02, min(1.0 - region["y"], region["height"]))
    return region


def _validated_visual_differences(value):
    result = []
    for item in value if isinstance(value, list) else []:
        if not isinstance(item, dict):
            continue
        figma_region = _validated_region(item.get("figmaRegion"))
        web_region = _validated_region(item.get("webRegion"))
        if not figma_region and not web_region:
            continue
        result.append({
            "component": str(item.get("component") or "visual difference"),
            "category": str(item.get("category") or "appearance"),
            "figma": str(item.get("figma") or ""),
            "web": str(item.get("web") or ""),
            "explanation": str(item.get("explanation") or ""),
            "figmaRegion": figma_region,
            "webRegion": web_region,
        })
    return result[:12]


def _generate(state):
    figma_image = state.get("figma_image_base64")
    web_image = state.get("web_image_base64")
    if not figma_image or not web_image:
        return _fallback_report(state)

    rows = state.get("rows", [])[:25]
    predictions = state.get("predictions", [])[:25]
    measured = _measured_differences(state)
    measured_visual = _deterministic_visual_differences(state)
    prompt = f"""You are a senior visual QA engineer. Compare the first PNG (Figma reference)
with the second PNG (implemented web page) for page {state.get('page_name', 'unknown')}.
Use the screenshots as page-level evidence and the rows as component-level token evidence.
Do not invent visual rectangles. The measured visual regions are authoritative and are used only
as table evidence, not as boxes drawn over the images.

Token rows: {json.dumps(rows, ensure_ascii=True)}
ML predictions: {json.dumps(predictions, ensure_ascii=True)}
Measured component differences: {json.dumps(measured, ensure_ascii=True)}
Measured visual regions: {json.dumps(measured_visual, ensure_ascii=True)}

Return JSON with keys: summary (string), severity (none|low|medium|high),
visualDifferences (array of objects with component, category, figma, web, explanation,
figmaRegion, webRegion). Use the measured visual regions exactly as provided.
and recommendations (array of short strings). Explain only measured evidence. Never
invent token values, selectors, dimensions, component names, or mappings. Do not use markdown."""
    report, response = generate_json(prompt, [figma_image, web_image])
    report["visualDifferences"] = measured_visual
    mismatches = [item for item in measured if not item.get("match")]
    if not measured_visual and not mismatches:
        report["summary"] = "No significant visual or extracted-token differences were detected after content alignment."
        report["severity"] = "none"
    if report["visualDifferences"]:
        has_page_level_difference = any(
            str(item.get("component", "")).lower() == "page visual appearance"
            for item in report["visualDifferences"]
        )
        if str(report.get("severity", "")).lower() in {"", "none", "low"}:
            report["severity"] = "high" if has_page_level_difference else "medium"
        report["summary"] = (
            f"{len(mismatches)} component token mismatches and "
            f"{len(report['visualDifferences'])} screenshot-level visual difference"
            f"{'' if len(report['visualDifferences']) == 1 else 's'} were detected."
        )
    report["componentDifferences"] = measured
    report["componentPredictions"] = [
        {
            "index": item.get("index"),
            "component": item.get("component"),
            "modelComponent": str(item.get("modelComponent") or item.get("canonicalName") or item.get("component") or ""),
            "match": bool(item.get("match")),
            "matchProbability": item.get("matchProbability"),
            "rawModelMatch": item.get("rawModelMatch"),
            "rawModelProbability": item.get("rawModelProbability"),
            "mappingConfidence": item.get("mappingConfidence"),
        }
        for item in measured
    ]
    report["imageMetrics"] = {
        "figma": _image_size(figma_image),
        "web": _image_size(web_image),
    }
    report["provider"] = response.provider
    report["model"] = response.model
    report["provenance"] = {
        "tokens": "extracted", "prediction": "local-ml", "explanation": response.provider
    }
    return report

async def run(state: FigmaToDesignState) -> FigmaToDesignState:
    try:
        report = await asyncio.to_thread(_generate, state)
        predictions = report.get("componentPredictions") or state.get("predictions", [])
        return {**state, "predictions": predictions, "comparison_report": report}
    except Exception as exception:
        fallback = _fallback_report(state)
        fallback["warning"] = f"Visual comparison failed: {exception}"
        predictions = fallback.get("componentPredictions") or [
            {
                "index": item.get("index"),
                "component": item.get("component"),
                "modelComponent": str(item.get("modelComponent") or item.get("canonicalName") or item.get("component") or ""),
                "match": bool(item.get("match")),
                "matchProbability": item.get("matchProbability"),
                "rawModelMatch": item.get("rawModelMatch"),
                "rawModelProbability": item.get("rawModelProbability"),
                "mappingConfidence": item.get("mappingConfidence"),
            }
            for item in fallback.get("componentDifferences", [])
        ]
        return {**state, "predictions": predictions, "comparison_report": fallback}
