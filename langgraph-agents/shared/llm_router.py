"""LLM routing helpers."""


def route(agent_type: str) -> str:
    """Routes an agent type to a backend."""
    return "gemini-flash"
