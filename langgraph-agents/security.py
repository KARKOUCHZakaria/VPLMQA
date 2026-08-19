"""Credential access for agents. Secret values never enter graph state."""

import json
import os
import urllib.request
from pathlib import Path


def vault_secret(path: str, field: str = "value") -> str:
    token_file = Path(os.getenv("VAULT_TOKEN_FILE", "/run/secrets/vault_token"))
    if not token_file.exists():
        return ""
    address = os.getenv("VAULT_ADDR", "http://vault:8200").rstrip("/")
    request = urllib.request.Request(
        f"{address}/v1/secret/data/{path.strip('/')}",
        headers={"X-Vault-Token": token_file.read_text(encoding="utf-8").strip()},
    )
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            payload = json.loads(response.read().decode("utf-8"))
        return str(payload["data"]["data"].get(field, ""))
    except Exception:
        return ""


def gemini_api_key() -> str:
    return vault_secret("vplmqa/platform/gemini", "api_key").strip()


def mistral_api_key() -> str:
    return vault_secret("vplmqa/platform/mistral", "api_key").strip()
