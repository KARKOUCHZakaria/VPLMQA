import os
import subprocess
import time
import sys
from ..state import AgentState
from ..e2e_service_client import E2EServiceClient

def execute_test(state: AgentState) -> AgentState:
    script = state.get("playwright_script", "")
    execution_id = state.get("execution_id")
    client = E2EServiceClient()
    
    if execution_id:
        client.update_test_execution(execution_id, {"status": "RUNNING"})
        
    script_path = "temp_test.py"
    with open(script_path, "w", encoding="utf-8") as f:
        f.write(script)
        
    start_time = time.time()
    try:
        # Run python directly on the generated script
        result = subprocess.run([sys.executable, script_path], capture_output=True, text=True, timeout=60)
        duration = int((time.time() - start_time) * 1000)
        
        state["execution_logs"] = result.stdout
        
        if result.returncode == 0:
            state["status"] = "execution_passed"
            state["error_message"] = None
        else:
            state["status"] = "execution_failed"
            state["error_message"] = result.stderr or result.stdout
            
    except subprocess.TimeoutExpired:
        state["status"] = "execution_failed"
        state["error_message"] = "Test execution timed out after 60 seconds."
    except Exception as e:
        state["status"] = "execution_failed"
        state["error_message"] = str(e)
    finally:
        if os.path.exists(script_path):
            os.remove(script_path)
            
    return state
