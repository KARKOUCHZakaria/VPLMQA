from typing import Any
from .retriever import documents_from_context, store


def retrieve_project_knowledge(state: dict[str, Any]) -> dict[str, Any]:
    context = state.get("project_context", {})
    project_id = str(context.get("projectId") or "")
    if not project_id:
        return {**state, "rag_context": []}
    documents = documents_from_context(context, state.get("component_catalog", []))
    store.index(project_id, documents)
    query = "\n".join(step.get("normalized_text", "") for step in state.get("normalized_steps", []))
    return {**state, "rag_context": store.retrieve(project_id, query)}
