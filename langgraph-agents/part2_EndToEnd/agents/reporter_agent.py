from ..state import AgentState
from ..e2e_service_client import E2EServiceClient

def generate_report(state: AgentState) -> AgentState:
    client = E2EServiceClient()
    execution_id = state.get("execution_id")
    
    if execution_id:
        status_map = {
            "execution_passed": "PASSED",
            "max_retries_reached": "FAILED",
            "validation_failed": "FAILED"
        }
        final_status = status_map.get(state["status"], "FAILED")
        
        client.update_test_execution(execution_id, {
            "status": final_status,
            "outputLog": state.get("execution_logs", ""),
            "errorMessage": state.get("error_message", "")
        })
        
    state["status"] = "completed"
    return state
