from ..state import AgentState
from ..e2e_service_client import E2EServiceClient
from .gemini_utils import generate_content_with_fallback
import json

def process_feature(state: AgentState) -> AgentState:
    client = E2EServiceClient()
    feature_id = state["feature_id"]
    
    # 1. Fetch feature details from backend
    feature_data = client.get_feature_full(feature_id)
    description = feature_data.get("description", "No description provided.")
    title = feature_data.get("title", f"Feature {feature_id}")
    
    # Generate Gherkin using Gemini
    prompt = f"""Generate a complete Gherkin feature for this scenario:

Feature Title: {title}
Requirement/Description: {description}

Create 2 realistic scenarios with 4 steps each.
Make it testable with Playwright (e.g. web interactions).
If a scenario creates data that is reused later, write natural Gherkin, not technical placeholders.
Example: "I enter a valid article reference in the reference field", then "I should see the created article reference".
Reuse generated data naturally in every affected verification, search, or click step so the flow stays logically consistent.

Format as proper .feature file."""

    response = generate_content_with_fallback(prompt)
    gherkin = response.text
    
    state["gherkin_text"] = gherkin
    
    # 2. Create a TestExecution record
    exec_record = client.create_test_execution(feature_id)
    state["execution_id"] = exec_record["id"]
    
    # 3. Update feature status
    client.update_feature_status(feature_id, "ACTIVE")
    
    state["status"] = "feature_processed"
    return state
