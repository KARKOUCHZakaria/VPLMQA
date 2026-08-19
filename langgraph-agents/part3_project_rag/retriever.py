import hashlib
import json
import math
import os
import re
from typing import Any


DIMENSIONS = 384


def embed(text: str) -> list[float]:
    """Small, deterministic local embedding with no model download or cloud call."""
    vector = [0.0] * DIMENSIONS
    tokens = re.findall(r"[a-z0-9_/-]+", text.lower())
    for token in tokens:
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        index = int.from_bytes(digest[:4], "big") % DIMENSIONS
        vector[index] += -1.0 if digest[4] & 1 else 1.0
    norm = math.sqrt(sum(value * value for value in vector)) or 1.0
    return [value / norm for value in vector]


def _vector_literal(values: list[float]) -> str:
    return "[" + ",".join(f"{value:.8f}" for value in values) + "]"


class ProjectKnowledgeStore:
    def __init__(self) -> None:
        self.database_url = os.getenv("RAG_DATABASE_URL", "").strip()
        self._fallback: dict[str, list[dict[str, Any]]] = {}

    def index(self, project_id: str, documents: list[dict[str, Any]]) -> int:
        prepared = [self._prepare(project_id, item) for item in documents if item]
        self._fallback[project_id] = prepared
        if not self.database_url or not prepared:
            return len(prepared)
        try:
            import psycopg
            with psycopg.connect(self.database_url) as connection:
                with connection.cursor() as cursor:
                    self._ensure_schema(cursor)
                    for item in prepared:
                        cursor.execute(
                            """INSERT INTO project_knowledge
                               (project_id, document_key, kind, content, metadata, embedding)
                               VALUES (%s, %s, %s, %s, %s::jsonb, %s::vector)
                               ON CONFLICT (project_id, document_key) DO UPDATE SET
                                 kind=EXCLUDED.kind, content=EXCLUDED.content,
                                 metadata=EXCLUDED.metadata, embedding=EXCLUDED.embedding,
                                 updated_at=NOW()""",
                            (project_id, item["key"], item["kind"], item["content"],
                             json.dumps(item["metadata"]), _vector_literal(item["embedding"])),
                        )
                connection.commit()
        except Exception:
            pass
        return len(prepared)

    def retrieve(self, project_id: str, query: str, limit: int = 8) -> list[dict[str, Any]]:
        query_vector = embed(query)
        if self.database_url:
            try:
                import psycopg
                with psycopg.connect(self.database_url) as connection:
                    with connection.cursor() as cursor:
                        self._ensure_schema(cursor)
                        cursor.execute(
                            """SELECT document_key, kind, content, metadata,
                                      1 - (embedding <=> %s::vector) AS score
                               FROM project_knowledge WHERE project_id = %s
                               ORDER BY embedding <=> %s::vector LIMIT %s""",
                            (_vector_literal(query_vector), project_id, _vector_literal(query_vector), limit),
                        )
                        return [
                            {"key": row[0], "kind": row[1], "content": row[2],
                             "metadata": row[3], "score": float(row[4])}
                            for row in cursor.fetchall()
                        ]
            except Exception:
                pass
        scored = []
        for item in self._fallback.get(project_id, []):
            score = sum(a * b for a, b in zip(query_vector, item["embedding"]))
            scored.append({**item, "score": score})
        return sorted(scored, key=lambda item: item["score"], reverse=True)[:limit]

    def _prepare(self, project_id: str, item: dict[str, Any]) -> dict[str, Any]:
        kind = str(item.get("kind") or "component")
        key = str(item.get("key") or item.get("id") or hashlib.sha256(json.dumps(item, sort_keys=True).encode()).hexdigest())
        content = str(item.get("content") or json.dumps(item, ensure_ascii=True, sort_keys=True))
        return {"projectId": project_id, "key": key, "kind": kind, "content": content,
                "metadata": item.get("metadata") or item, "embedding": embed(content)}

    def _ensure_schema(self, cursor: Any) -> None:
        cursor.execute("CREATE EXTENSION IF NOT EXISTS vector")
        cursor.execute(f"""CREATE TABLE IF NOT EXISTS project_knowledge (
            project_id UUID NOT NULL, document_key VARCHAR(500) NOT NULL,
            kind VARCHAR(50) NOT NULL, content TEXT NOT NULL, metadata JSONB NOT NULL,
            embedding vector({DIMENSIONS}) NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            PRIMARY KEY(project_id, document_key))""")


store = ProjectKnowledgeStore()


def documents_from_context(project_context: dict[str, Any], catalog: list[dict[str, Any]]) -> list[dict[str, Any]]:
    documents = []
    for page in project_context.get("pages", []) or []:
        documents.append({"kind": "page", "key": f"page:{page.get('id')}", "metadata": page})
    for component in catalog:
        key = component.get("id") or component.get("canonicalName") or component.get("uniqueName")
        documents.append({"kind": "component", "key": f"component:{key}", "metadata": component})
    return documents
