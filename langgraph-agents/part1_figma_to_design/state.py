"""Typed state for the Part 1 visual comparison workflow."""

from typing import Any, Optional, TypedDict

class FigmaToDesignState(TypedDict, total=False):
    page_name: str
    rows: list[dict[str, Any]]
    figma_image_base64: str
    web_image_base64: str
    predictions: list[dict[str, Any]]
    comparison_report: Optional[dict[str, Any]]
    error: Optional[str]
