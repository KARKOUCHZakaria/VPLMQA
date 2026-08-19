from ..state import AgentState
from .gemini_utils import generate_content_with_fallback

def generate_playwright_script(state: AgentState) -> AgentState:
    gherkin = state.get("gherkin_text", "")
    
    prompt = f"""You are the VPLMQA E2E automation agent.

Generate COMPLETE, EXECUTABLE Playwright Python test code for this Gherkin:

{gherkin}

CRITICAL REQUIREMENTS:
1. from playwright.sync_api import sync_playwright
2. Use sync_playwright() context manager
3. Browser MUST launch with headless=False (VISIBLE)
4. Example: browser = playwright.chromium.launch(headless=False, slow_mo=500)
5. Treat page names as application pages, not external search terms. If a step says "go to the Tickets page", navigate to the project base URL plus /tickets or click the visible Tickets module card.
6. Prefer robust locators in this order: data-testid, id, accessible role/name, label, placeholder, then visible text. Never use brittle nth() unless there are duplicate labels and you explain why in code comments.
7. After every navigation or click that changes the page, assert the expected visible page state before continuing.
8. Add self-healing fallbacks: if direct route navigation lands on Home, click the matching module card; if a button click opens a form, wait for a form-specific label before filling.
9. Use page.wait_for_load_state("domcontentloaded") and targeted visible waits; avoid fixed sleeps except short visual pauses.
10. Use assert/expect-style checks that fail at the step where the page is wrong. Do not continue after failed recovery.
11. Take a screenshot on failure and include the precise exception.
12. Ensure browser closes with context manager.
13. Do not put secrets/password values in prompts, logs, generated code, or cloud LLM output. Read secrets from runtime args/env only.
14. When the test creates new business data, write natural Gherkin, not technical placeholders. Example: "I enter a valid article reference in the reference field".
15. Reuse generated data naturally in later steps. Example: "I should see the created article reference" or "I click the created article designation".
16. Execute like a normal tester: clear target, visible wait after each page-changing action, no racing through UI transitions.
17. Do not invent fragile selectors or one-off hardcoded values when the intent can be resolved from visible labels, roles, DOM attributes, or generated-data memory.

Generate COMPLETE working code that runs with: python test.py

Include ALL imports, functions, everything needed.
Make browser visible and slow (slow_mo=500) so you can see what's happening."""
    
    response = generate_content_with_fallback(prompt)
    content = response.text
    
    # Extract code from markdown block if present
    if "```python" in content:
        script = content.split("```python")[1].split("```")[0].strip()
    elif "```" in content:
        script = content.split("```")[1].strip()
    else:
        script = content.strip()
        
    state["playwright_script"] = script
    state["status"] = "script_generated"
    return state
