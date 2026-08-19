from ..state import AgentState
from .gemini_utils import generate_content_with_fallback

def retry_failed_test(state: AgentState) -> AgentState:
    retry_count = state.get("retry_count", 0)
    
    if retry_count < 3:
        old_script = state.get("playwright_script", "")
        error_msg = state.get("error_message", "")
        
        prompt = f"""
        You are the VPLMQA self-healing E2E repair agent.

        The Playwright script failed. Repair the script so it fails honestly at the real broken step and only self-heals safe UI drift.

        Repair rules:
        - Return ONLY corrected Python code in a python code block.
        - Prefer role/name, label, placeholder, id, and data-testid locators over CSS/nth selectors.
        - If navigation to an app page lands on Home, click the visible module card with the same page name, then assert the target page content is visible.
        - For form flows, after opening a form wait for a unique field label before filling.
        - Keep confidential values out of code. Use args/env variables for passwords, tokens, cookies, and API keys.
        - Do not mask failures by continuing. If recovery fails, raise a clear AssertionError with current URL and visible page clue.
        - Keep reusable helper functions small and deterministic.
        
        Script:
        ```python
        {old_script}
        ```
        
        Error:
        {error_msg}
        """
        
        response = generate_content_with_fallback(prompt)
        content = response.text
        
        if "```python" in content:
            script = content.split("```python")[1].split("```")[0].strip()
        elif "```" in content:
            script = content.split("```")[1].strip()
        else:
            script = content.strip()
            
        state["playwright_script"] = script
        state["retry_count"] = retry_count + 1
        state["status"] = "retrying"
    else:
        state["status"] = "max_retries_reached"
        
    return state
