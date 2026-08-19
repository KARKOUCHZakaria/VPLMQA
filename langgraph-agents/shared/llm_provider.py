"""Vault-backed LLM access shared by all LangGraph agents."""

import base64
import json
import os
import time
from dataclasses import dataclass
from typing import Any

import httpx
from google import genai
from google.genai import types

from security import gemini_api_key, mistral_api_key


@dataclass
class LlmResponse:
    text: str
    provider: str
    model: str


def provider_name() -> str:
    return os.getenv("LLM_PROVIDER", "mistral").strip().lower()


def model_name(vision: bool = False) -> str:
    provider = provider_name()
    if provider == "mistral":
        variable = "MISTRAL_VISION_MODEL" if vision else "MISTRAL_MODEL"
        return os.getenv(variable, "mistral-small-latest")
    variable = "GEMINI_ENRICHMENT_MODEL" if vision else "GEMINI_MODEL"
    return os.getenv(variable, "gemini-2.5-flash")


def _mistral_content(prompt: str, images: list[str]) -> list[dict[str, Any]] | str:
    if not images:
        return prompt
    content: list[dict[str, Any]] = [{"type": "text", "text": prompt}]
    for encoded in images:
        if not encoded:
            continue
        base64.b64decode(encoded, validate=True)
        content.append({
            "type": "image_url",
            "image_url": f"data:image/png;base64,{encoded}",
        })
    return content


def _mistral_generate(prompt: str, images: list[str], json_mode: bool, model_override: str | None = None) -> LlmResponse:
    key = mistral_api_key()
    if not key:
        raise RuntimeError("Mistral credential is not available from Vault")
    model = model_override or model_name(bool(images))
    payload: dict[str, Any] = {
        "model": model,
        "messages": [{"role": "user", "content": _mistral_content(prompt, images)}],
        "temperature": float(os.getenv("LLM_TEMPERATURE", "0.1")),
        "max_tokens": int(os.getenv("LLM_MAX_OUTPUT_TOKENS", "4096")),
    }
    if json_mode:
        payload["response_format"] = {"type": "json_object"}
    attempts = max(1, int(os.getenv("MISTRAL_RETRY_ATTEMPTS", "3")))
    base_delay = max(0.1, float(os.getenv("MISTRAL_RETRY_BASE_DELAY_SECONDS", "1.5")))
    last_exception: Exception | None = None
    for attempt in range(attempts):
        try:
            response = httpx.post(
                os.getenv("MISTRAL_API_URL", "https://api.mistral.ai/v1/chat/completions"),
                headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"},
                json=payload,
                timeout=float(os.getenv("LLM_TIMEOUT_SECONDS", "90")),
            )
            response.raise_for_status()
            content = response.json()["choices"][0]["message"]["content"]
            if isinstance(content, list):
                content = "".join(str(chunk.get("text") or "") for chunk in content if isinstance(chunk, dict))
            return LlmResponse(str(content), "mistral", model)
        except httpx.HTTPStatusError as exception:
            last_exception = exception
            status = exception.response.status_code
            if status != 429 or attempt == attempts - 1:
                break
            retry_after = exception.response.headers.get("retry-after")
            delay = float(retry_after) if retry_after and retry_after.isdigit() else base_delay * (2 ** attempt)
            time.sleep(min(delay, 12.0))
        except Exception as exception:
            last_exception = exception
            break

    message = str(last_exception).replace(key, "***") if last_exception else "unknown error"
    raise RuntimeError(f"Mistral generation failed: {message}") from last_exception


def _gemini_generate(prompt: str, images: list[str], json_mode: bool, model_override: str | None = None) -> LlmResponse:
    key = gemini_api_key()
    if not key:
        raise RuntimeError("Gemini credential is not available from Vault")
    model = model_override or model_name(bool(images))
    contents: list[Any] = [prompt]
    for encoded in images:
        if encoded:
            contents.append(types.Part.from_bytes(data=base64.b64decode(encoded), mime_type="image/png"))
    config = types.GenerateContentConfig(
        temperature=float(os.getenv("LLM_TEMPERATURE", "0.1")),
        max_output_tokens=int(os.getenv("LLM_MAX_OUTPUT_TOKENS", "4096")),
        response_mime_type="application/json" if json_mode else None,
    )
    response = genai.Client(api_key=key).models.generate_content(
        model=model, contents=contents, config=config
    )
    return LlmResponse(response.text, "gemini", model)


def generate(
    prompt: str,
    images: list[str] | None = None,
    json_mode: bool = False,
    model_override: str | None = None,
) -> LlmResponse:
    selected = provider_name()
    if selected == "mistral":
        return _mistral_generate(prompt, images or [], json_mode, model_override)
    if selected == "gemini":
        return _gemini_generate(prompt, images or [], json_mode, model_override)
    raise RuntimeError(f"Unsupported LLM_PROVIDER: {selected}")


def generate_json(
    prompt: str,
    images: list[str] | None = None,
    model_override: str | None = None,
) -> tuple[dict[str, Any], LlmResponse]:
    response = generate(prompt, images, json_mode=True, model_override=model_override)
    parsed = json.loads(response.text)
    if not isinstance(parsed, dict):
        raise ValueError("LLM JSON response must be an object")
    return parsed, response
