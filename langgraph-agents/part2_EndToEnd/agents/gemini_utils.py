from shared.llm_provider import generate


def generate_content_with_fallback(prompt: str):
    return generate(prompt)
