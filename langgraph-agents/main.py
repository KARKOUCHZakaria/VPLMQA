"""FastAPI entry point for the LangGraph agents service."""

import os
from pathlib import Path

from fastapi import BackgroundTasks, FastAPI, HTTPException
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

from part1_figma_to_design.agents.semantic import organize as organize_components, enrich_pages
from part2_EndToEnd.services import default_cost_policy
from part2_EndToEnd.agents.ticket_report_agent import generate_ticket_report
from part2_EndToEnd.workflow import create_workflow
from part3_project_rag.retriever import store as knowledge_store


app = FastAPI(title="VPLMQA Agents", version="0.1.0")


class AutomateRequest(BaseModel):
    feature_id: str


class GherkinRunRequest(BaseModel):
    gherkin: str = Field(..., min_length=1)
    headless: bool = True
    repair_on_failure: bool = False
    max_repair_attempts: int = 0
    execute: bool = True
    component_catalog: list[dict] = Field(default_factory=list)
    project_context: dict = Field(default_factory=dict)


class ComponentOrganizationRequest(BaseModel):
    page_name: str
    figma_components: list[dict] = Field(default_factory=list)
    web_components: list[dict] = Field(default_factory=list)
    workflow_context: str = ""


class PageEnrichmentRequest(BaseModel):
    page_name: str
    figma_page: dict = Field(default_factory=dict)
    web_page: dict = Field(default_factory=dict)
    figma_components: list[dict] = Field(default_factory=list)
    web_components: list[dict] = Field(default_factory=list)
    figma_image_base64: str = ""
    web_image_base64: str = ""
    workflow_context: str = ""


class KnowledgeRequest(BaseModel):
    project_id: str
    query: str = ""
    documents: list[dict] = Field(default_factory=list)
    limit: int = 8


class TicketReportRequest(BaseModel):
    featureName: str = ""
    featureId: str = ""
    scenarioName: str = ""
    scenarioId: str = ""
    executionId: str = ""
    action: str = "validated_failure"
    status: str = "FAILED"
    steps: list[dict] = Field(default_factory=list)
    failedStep: dict = Field(default_factory=dict)
    logs: str = ""
    screenshots: list[str] = Field(default_factory=list)
    errorMessage: str = ""
    severity: str = "MEDIUM"


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/v1/agents/part3/index")
async def index_project_knowledge(request: KnowledgeRequest) -> dict:
    return {"indexed": knowledge_store.index(request.project_id, request.documents)}


@app.post("/api/v1/agents/part3/retrieve")
async def retrieve_project_knowledge_api(request: KnowledgeRequest) -> dict:
    return {"matches": knowledge_store.retrieve(request.project_id, request.query, request.limit)}


@app.get("/artifacts/{artifact_path:path}")
async def get_artifact(artifact_path: str):
    root = Path(os.getenv("STEP_FUNCTION_STORAGE_ROOT", "/app/output")).resolve()
    path = (root / artifact_path).resolve()
    if root not in path.parents and path != root:
        raise HTTPException(status_code=400, detail="Invalid artifact path")
    if not path.exists() or not path.is_file():
        raise HTTPException(status_code=404, detail="Artifact not found")
    return FileResponse(path)


@app.post("/api/v1/agents/part1/organize-components")
async def organize_part1_components(request: ComponentOrganizationRequest) -> dict:
    import asyncio
    return await asyncio.to_thread(
        organize_components,
        request.page_name,
        request.figma_components,
        request.web_components,
        request.workflow_context,
    )


@app.post("/api/v1/agents/part1/enrich-page")
async def enrich_part1_page(request: PageEnrichmentRequest) -> dict:
    import asyncio
    return await asyncio.to_thread(
        enrich_pages,
        request.page_name,
        request.figma_page,
        request.web_page,
        request.figma_components,
        request.web_components,
        request.figma_image_base64,
        request.web_image_base64,
        request.workflow_context,
    )


@app.get("/api/v1/agents/part1/{run_id}/state")
async def part1_state(run_id: str) -> dict[str, str]:
    return {"run_id": run_id, "status": "pending"}


@app.post("/api/v1/agents/part1/{run_id}/resume")
async def resume_part1(run_id: str) -> dict[str, str]:
    return {"run_id": run_id, "status": "resumed"}


@app.post("/api/v1/agents/part2/run")
async def run_part2(request: AutomateRequest, background_tasks: BackgroundTasks) -> dict[str, str]:
    def run_graph(feature_id: str) -> None:
        graph = create_workflow()
        graph.invoke(
            {
                "gherkin_text": f"Feature: Feature {feature_id}\n  Scenario: Placeholder\n    Given the application is open",
                "headless": True,
                "repair_on_failure": False,
                "max_repair_attempts": 0,
                "repair_attempts": 0,
                "execute": False,
                "errors": [],
                "cost_policy": default_cost_policy(),
                "confidentiality_policy": {"cloud_llm_secrets": "forbidden"},
                "should_stop": False,
                "current_step_index": 0,
            }
        )

    background_tasks.add_task(run_graph, request.feature_id)
    return {"run_id": "run-part2-async", "status": "started", "feature_id": request.feature_id}


@app.post("/api/v1/agents/part2/ticket-report")
async def create_ticket_report(request: TicketReportRequest) -> dict:
    return generate_ticket_report(request.model_dump())


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
            "component_catalog": request.component_catalog,
            "project_context": request.project_context,
        }
    )
    return result["response"]
