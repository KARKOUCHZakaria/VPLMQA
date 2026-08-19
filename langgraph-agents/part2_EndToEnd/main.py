import os
from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

try:
    from .services import default_cost_policy
    from .workflow import create_workflow
except ImportError:
    from services import default_cost_policy
    from workflow import create_workflow


app = FastAPI(title="E2E LangGraph Agents")


class GherkinRunRequest(BaseModel):
    gherkin: str = Field(..., min_length=1)
    headless: bool = True
    repair_on_failure: bool = False
    max_repair_attempts: int = 0
    execute: bool = True


@app.post("/gherkin/e2e/run")
async def run_gherkin_e2e(request: GherkinRunRequest):
    graph = create_workflow()
    result = graph.invoke(
        {
            "gherkin_text": request.gherkin,
            "headless": request.headless,
            "repair_on_failure": request.repair_on_failure,
            "max_repair_attempts": request.max_repair_attempts,
            "repair_attempts": 0,
            "execute": request.execute,
            "errors": [],
            "cost_policy": default_cost_policy(),
            "confidentiality_policy": {"cloud_llm_secrets": "forbidden"},
            "should_stop": False,
            "current_step_index": 0,
        }
    )
    return result["response"]


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/artifacts/{artifact_path:path}")
def get_artifact(artifact_path: str):
    root = Path(os.getenv("STEP_FUNCTION_STORAGE_ROOT", "/app/output")).resolve()
    path = (root / artifact_path).resolve()
    if root not in path.parents and path != root:
        raise HTTPException(status_code=400, detail="Invalid artifact path")
    if not path.exists() or not path.is_file():
        raise HTTPException(status_code=404, detail="Artifact not found")
    return FileResponse(path)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
