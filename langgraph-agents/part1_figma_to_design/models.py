"""Part 1 models."""

from pydantic import BaseModel


class FigmaComponentModel(BaseModel):
    """Figma component model."""

    name: str
