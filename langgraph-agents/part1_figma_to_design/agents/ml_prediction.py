"""Design-token ML prediction agent."""

import os
import re
from pathlib import Path

import joblib
import numpy as np
import pandas as pd


MODEL_DIR = Path(os.getenv("PART1_MODEL_DIR", "/app/ml"))
FONT_WEIGHTS = {"normal": 0, "medium": 1, "bold": 2}
NUMERIC_TOLERANCES = {
    "spacing": 2,
    "font_size": 1,
    "border_radius": 2,
    "width": 6,
    "height": 6,
}
REQUIRED_COLUMNS = [
    "component", "figma_color", "code_color", "figma_spacing", "code_spacing",
    "figma_font_size", "code_font_size", "figma_font_weight", "code_font_weight",
    "figma_border_radius", "code_border_radius", "figma_width", "code_width",
    "figma_height", "code_height",
]

MODEL = joblib.load(MODEL_DIR / "model_xgb.pkl")
SCALER = joblib.load(MODEL_DIR / "scaler.pkl")
FEATURE_NAMES = joblib.load(MODEL_DIR / "features.pkl")
COMPONENT_ENCODER = joblib.load(MODEL_DIR / "component_encoder.pkl")
MODEL_COMPONENTS = [str(value) for value in COMPONENT_ENCODER.classes_]


def _number(value):
    match = re.search(r"-?\d+(?:\.\d+)?", str(value or ""))
    return float(match.group()) if match else 0.0


def _color(value):
    text = str(value or "").strip().lower()
    if text.startswith("#"):
        hexadecimal = text[1:]
        if len(hexadecimal) in (3, 4):
            hexadecimal = "".join(character * 2 for character in hexadecimal)
        if len(hexadecimal) >= 6 and re.fullmatch(r"[0-9a-f]+", hexadecimal):
            return tuple(int(hexadecimal[index:index + 2], 16) for index in (0, 2, 4))
    match = re.match(r"rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)", text)
    return tuple(int(round(float(channel))) for channel in match.groups()) if match else (0, 0, 0)


def _color_text(value) -> str:
    rgb = _color(value)
    return f"#{rgb[0]:02X}{rgb[1]:02X}{rgb[2]:02X}"


def _component(value):
    normalized = re.sub(r"[^a-z0-9]+", " ", str(value or "").lower()).strip()
    compact = normalized.replace(" ", "")
    for model_component in MODEL_COMPONENTS:
        candidate = re.sub(r"[^a-z0-9]+", "", model_component.lower())
        if candidate == compact or candidate in compact:
            return model_component
    aliases = {
        "button": ("button", "submit", "action", "cta", "trigger"),
        "input": ("input", "field", "textbox", "search", "email", "password", "credential"),
        "navbar": ("navbar", "navigation", "header", "menu", "sidebar"),
        "card": ("card", "panel", "container", "form", "modal", "dialog"),
        "title": ("text", "label", "title", "heading", "caption", "paragraph", "message"),
    }
    for target, keywords in aliases.items():
        if target in MODEL_COMPONENTS and any(keyword in normalized for keyword in keywords):
            return target
    return "title" if "title" in MODEL_COMPONENTS else MODEL_COMPONENTS[0]


def _font_weight(value):
    text = str(value or "normal").strip().lower()
    if text in FONT_WEIGHTS:
        return FONT_WEIGHTS[text]
    numeric = _number(text)
    return 2 if numeric >= 700 else 1 if numeric >= 500 else 0


def _missing(value) -> bool:
    text = str(value or "").strip()
    return text == "" or text.lower() in {"none", "null", "nan", "undefined"}


def _numeric_difference(category: str, left, right):
    if _missing(left) or _missing(right):
        return None
    left_number = _number(left)
    right_number = _number(right)
    tolerance = max(
        NUMERIC_TOLERANCES.get(category, 0),
        abs(left_number) * 0.03 if category in {"width", "height"} else 0,
    )
    delta = right_number - left_number
    if abs(delta) <= tolerance:
        return None
    return {
        "category": category,
        "figma": left,
        "web": right,
        "delta": round(delta, 3),
        "tolerance": round(tolerance, 3),
    }


def _token_differences(row: dict) -> list[dict]:
    differences = []
    color_delta = sum(abs(a - b) for a, b in zip(_color(row.get("figma_color")), _color(row.get("code_color"))))
    if not _missing(row.get("figma_color")) and not _missing(row.get("code_color")) and color_delta > 10:
        differences.append({
            "category": "color",
            "figma": _color_text(row.get("figma_color")),
            "web": _color_text(row.get("code_color")),
            "delta": color_delta,
        })
    for category, figma_key, code_key in (
        ("spacing", "figma_spacing", "code_spacing"),
        ("font_size", "figma_font_size", "code_font_size"),
        ("border_radius", "figma_border_radius", "code_border_radius"),
        ("width", "figma_width", "code_width"),
        ("height", "figma_height", "code_height"),
    ):
        difference = _numeric_difference(category, row.get(figma_key), row.get(code_key))
        if difference:
            differences.append(difference)
    if _font_weight(row.get("figma_font_weight")) != _font_weight(row.get("code_font_weight")):
        differences.append({
            "category": "font_weight",
            "figma": row.get("figma_font_weight"),
            "web": row.get("code_font_weight"),
            "delta": None,
        })
    return differences


def _mapping_confidence(row: dict) -> float:
    try:
        return max(0.0, min(1.0, float(row.get("mapping_confidence") or 0.0)))
    except (TypeError, ValueError):
        return 0.0


def _calibrated_prediction(row: dict, raw_probability: float, raw_match: bool) -> tuple[bool, float, list[dict], str]:
    token_differences = _token_differences(row)
    mapping_confidence = _mapping_confidence(row)
    if mapping_confidence < 0.45:
        probability = min(raw_probability, max(0.05, mapping_confidence * 0.7))
        return False, round(probability, 6), token_differences, "low mapping confidence"
    if token_differences:
        penalty = min(0.62, 0.16 * len(token_differences))
        probability = min(raw_probability, max(0.05, 0.72 - penalty))
        return False, round(probability, 6), token_differences, "measured token differences"
    probability = max(raw_probability, 0.86 + mapping_confidence * 0.1)
    if not raw_match:
        probability = min(0.9, probability)
    return True, round(min(0.99, probability), 6), token_differences, "tokens match within tolerance"


def _features(rows):
    frame = pd.DataFrame(rows)
    missing = [column for column in REQUIRED_COLUMNS if column not in frame.columns]
    if missing:
        raise ValueError(f"Missing model columns: {', '.join(missing)}")
    resolved = frame["component"].map(_component)
    features = pd.DataFrame(index=frame.index)
    features["component"] = COMPONENT_ENCODER.transform(resolved)
    for column in (
        "figma_spacing", "code_spacing", "figma_font_size", "code_font_size",
        "figma_border_radius", "code_border_radius", "figma_width", "code_width",
        "figma_height", "code_height",
    ):
        features[column] = frame[column].map(_number)
    features["figma_font_weight"] = frame["figma_font_weight"].map(_font_weight)
    features["code_font_weight"] = frame["code_font_weight"].map(_font_weight)
    figma_rgb = np.asarray(frame["figma_color"].map(_color).tolist(), dtype=float)
    code_rgb = np.asarray(frame["code_color"].map(_color).tolist(), dtype=float)
    for index, channel in enumerate(("r", "g", "b")):
        features[f"figma_{channel}"] = figma_rgb[:, index]
        features[f"code_{channel}"] = code_rgb[:, index]
    features["color_diff"] = np.abs(figma_rgb - code_rgb).sum(axis=1)
    features["spacing_diff"] = np.abs(features["figma_spacing"] - features["code_spacing"])
    features["font_size_diff"] = np.abs(features["figma_font_size"] - features["code_font_size"])
    features["width_diff"] = np.abs(features["figma_width"] - features["code_width"])
    features["height_diff"] = np.abs(features["figma_height"] - features["code_height"])
    return features.loc[:, FEATURE_NAMES], resolved


def predict(rows):
    if not rows:
        return []
    features, resolved = _features(rows)
    scaled = SCALER.transform(features)
    probabilities = MODEL.predict_proba(scaled)[:, 1]
    predictions = MODEL.predict(scaled)
    results = []
    for index in range(len(rows)):
        raw_probability = float(probabilities[index])
        raw_match = bool(predictions[index])
        match, probability, token_differences, reason = _calibrated_prediction(rows[index], raw_probability, raw_match)
        results.append({
            "index": index,
            "component": str(rows[index].get("component", "")),
            "modelComponent": str(resolved.iloc[index]),
            "match": match,
            "matchProbability": probability,
            "rawModelMatch": raw_match,
            "rawModelProbability": round(raw_probability, 6),
            "tokenDifferences": token_differences,
            "decision": "token-aware-calibrated",
            "decisionReason": reason,
            "mappingConfidence": _mapping_confidence(rows[index]),
        })
    return results


def run(state):
    try:
        return {**state, "predictions": predict(state.get("rows", []))}
    except Exception as exception:
        return {**state, "error": f"ML prediction failed: {exception}"}
