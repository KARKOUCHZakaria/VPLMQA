"""State graph for part 1."""

from langgraph.graph import StateGraph, START, END

from part1_figma_to_design.state import FigmaToDesignState
from part1_figma_to_design.agents import comparison, ml_prediction


def build_graph():
    """Builds the part 1 graph."""
    builder = StateGraph(FigmaToDesignState)

    builder.add_node("predict_tokens", ml_prediction.run)
    builder.add_node("compare_images", comparison.run)

    builder.add_edge(START, "predict_tokens")
    builder.add_edge("predict_tokens", "compare_images")
    builder.add_edge("compare_images", END)

    return builder.compile()
