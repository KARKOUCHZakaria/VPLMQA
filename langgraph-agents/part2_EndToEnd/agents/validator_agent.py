from ..state import AgentState
from .gemini_utils import generate_content_with_fallback

def validate_gherkin(state: AgentState) -> AgentState:
    gherkin = state.get("gherkin_text", "")
    
    if not gherkin:
        state["validation_feedback"] = "Gherkin is empty."
        state["status"] = "validation_failed"
        return state
        
    prompt = f"""
    You are the VPLMQA Gherkin quality gate.

    Evaluate if the following Gherkin text is valid, deterministic, and automation-ready.
    Reply with "VALID" only if it satisfies all rules:
    - Uses Feature/Scenario/Given/When/Then/And correctly.
    - Uses app page names for project navigation, not raw URLs, when testing the VPLMQA project.
    - Each step describes one user action or one assertion.
    - Assertions verify visible business outcomes, not implementation details.
    - Confidential values are named by role, not exposed directly.
    - Form steps identify the target field by human label, for example "problem title input" or "assigned to input".
    - Final steps assert a stable state such as a ticket row/title, not only a short-lived toast, unless the toast is explicitly required.

    If invalid, provide a short actionable correction.
    
    Gherkin text:
    {gherkin}
    """
    
    response = generate_content_with_fallback(prompt)
    content = response.text.strip()
    
    if "VALID" in content.upper():
        state["validation_feedback"] = "Gherkin is valid."
        state["status"] = "validation_passed"
    else:
        state["validation_feedback"] = content
        state["status"] = "validation_failed"
        
    return state
