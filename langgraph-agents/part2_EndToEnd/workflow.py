from pathlib import Path

from langgraph.graph import END, START, StateGraph

from .services import (
    ConfidentialityService,
    ExecutionService,
    GherkinParser,
    StepFunctionGenerator,
    StepFunctionValidator,
    StepNormalizer,
    StepRegistryRepository,
    StepStorageService,
    default_cost_policy,
    model_for_step,
    utc_now,
)
from .state import AgentState
from part3_project_rag.workflow import retrieve_project_knowledge


parser = GherkinParser()
normalizer = StepNormalizer()
confidentiality = ConfidentialityService()
registry = StepRegistryRepository()
storage = StepStorageService()
generator = StepFunctionGenerator()
validator = StepFunctionValidator()
executor = ExecutionService()


def parse_gherkin(state: AgentState) -> AgentState:
    parsed = parser.parse(state["gherkin_text"])
    steps = [step for scenario in parsed["scenarios"] for step in scenario["steps"]]
    return {**state, "parsed_feature": parsed, "scenarios": parsed["scenarios"], "steps": steps}


def normalize_steps(state: AgentState) -> AgentState:
    return {**state, "normalized_steps": [normalizer.normalize(step) for step in state.get("steps", [])]}


def classify_confidentiality(state: AgentState) -> AgentState:
    classified = [confidentiality.classify(step) for step in state.get("normalized_steps", [])]
    confidential_fields = [
        {"step_id": step["id"], "fields": step["confidential_fields"]}
        for step in classified
        if step.get("confidential_fields")
    ]
    return {**state, "normalized_steps": classified, "confidential_fields": confidential_fields}


def lookup_step_registry(state: AgentState) -> AgentState:
    matches = []
    missing = []
    for step in state.get("normalized_steps", []):
        found = registry.find(step)
        if found:
            matches.append({**step, "step_function": found})
        else:
            missing.append(step)
    return {**state, "step_registry_matches": matches, "missing_steps": missing}


def route_missing_steps(state: AgentState) -> str:
    missing = state.get("missing_steps", [])
    if not missing:
        return "compose_test_plan"
    if any(step.get("requires_secret") for step in missing):
        return "handle_confidential_missing_steps"
    return "generate_safe_step_functions"


def generate_safe_step_functions(state: AgentState) -> AgentState:
    generated = [generator.generate(step, model_for_step(step)) for step in state.get("missing_steps", [])]
    return {**state, "generated_step_functions": generated}


def handle_confidential_missing_steps(state: AgentState) -> AgentState:
    generated = [
        generator.generate(step, "manual-redacted")
        for step in state.get("missing_steps", [])
    ]
    return {**state, "generated_step_functions": generated}


def validate_generated_functions(state: AgentState) -> AgentState:
    by_hash = {step["normalized_step_hash"]: step for step in state.get("missing_steps", [])}
    validated = [
        validator.validate(generated, by_hash[generated["normalized_step_hash"]])
        for generated in state.get("generated_step_functions", [])
    ]
    errors = state.get("errors", [])
    for item in validated:
        if item.get("validation_errors"):
            errors.append({"node": "validate_generated_functions", "errors": item["validation_errors"]})
    return {**state, "validated_step_functions": validated, "errors": errors}


def store_functions_in_minio(state: AgentState) -> AgentState:
    links = []
    enriched = []
    for item in state.get("validated_step_functions", []):
        if item["status"] != "validated":
            continue
        version = registry.next_version(item["normalized_step_hash"])
        stored = storage.store_code(item["normalized_step_hash"], version, item["code"])
        enriched_item = {**item, **stored, "version": version}
        enriched.append(enriched_item)
        links.append(stored)
    return {**state, "validated_step_functions": enriched, "minio_links": links}


def save_step_function_metadata(state: AgentState) -> AgentState:
    records = []
    for item in state.get("validated_step_functions", []):
        record = {
            "id": str(item.get("id") or item["normalized_step_hash"]),
            "normalized_step_hash": item["normalized_step_hash"],
            "normalized_text": item["normalized_text"],
            "function_name": item["function_name"],
            "minio_bucket": item["minio_bucket"],
            "minio_object_key": item["minio_object_key"],
            "minio_url": item["minio_url"],
            "version": item["version"],
            "input_schema": item["input_schema"],
            "output_schema": item["output_schema"],
            "created_by": item["created_by"],
            "model_used": item["model_used"],
            "confidence_score": item["confidence_score"],
            "requires_secret": item["requires_secret"],
            "allowed_cloud_llm": item["allowed_cloud_llm"],
            "status": "active",
            "created_at": utc_now(),
            "updated_at": utc_now(),
        }
        records.append(registry.save(record))
    return {**state, "db_records": records}


def compose_test_plan(state: AgentState) -> AgentState:
    function_by_hash = {
        item["normalized_step_hash"]: item
        for item in state.get("db_records", [])
    }
    for match in state.get("step_registry_matches", []):
        function_by_hash[match["normalized_step_hash"]] = match["step_function"]

    scenarios = []
    for scenario in state.get("scenarios", []):
        planned_steps = []
        for step in scenario["steps"]:
            normalized = next(item for item in state["normalized_steps"] if item["id"] == step["id"])
            function = function_by_hash.get(normalized["normalized_step_hash"])
            planned_steps.append(
                {
                    "step_id": step["id"],
                    "raw_text": step["raw_text"],
                    "normalized_text": normalized["normalized_text"],
                    "arguments": normalized.get("arguments", {}),
                    "function_id": function.get("id") if function else None,
                    "function_name": function.get("function_name") if function else None,
                    "function_minio_link": function.get("minio_url") if function else None,
                    "order": step["sequence_order"],
                    "requires_secret": normalized.get("requires_secret", False),
                }
            )
        scenarios.append({"scenario_id": scenario["id"], "name": scenario["name"], "steps": planned_steps})

    component_catalog = list(state.get("component_catalog", []))
    known_keys = {str(item.get("id") or item.get("canonicalName") or item.get("uniqueName")) for item in component_catalog}
    for match in state.get("rag_context", []):
        if match.get("kind") != "component" or not isinstance(match.get("metadata"), dict):
            continue
        component = match["metadata"]
        key = str(component.get("id") or component.get("canonicalName") or component.get("uniqueName"))
        if key not in known_keys:
            component_catalog.append(component)
            known_keys.add(key)

    plan = {
        "feature": state["parsed_feature"]["name"],
        "scenarios": scenarios,
        "component_catalog": component_catalog,
        "project_context": state.get("project_context", {}),
        "rag_context": state.get("rag_context", []),
        "self_healing": {
            "enabled": state.get("repair_on_failure", True),
            "max_attempts": state.get("max_repair_attempts", 1),
        },
    }
    return {**state, "composed_test_plan": plan}


def execute_feature(state: AgentState) -> AgentState:
    result = executor.execute_plan(state["composed_test_plan"], state.get("execute", True))
    if result["status"] == "failed":
        return {
            **state,
            "execution_result": result,
            "failed_step": result["failed_step"],
            "failed_scenario": result["failed_scenario"],
            "failed_feature": state["parsed_feature"],
            "current_step_index": result["step_index"],
            "should_stop": True,
        }
    return {**state, "execution_result": result, "should_stop": False}


def route_execution(state: AgentState) -> str:
    return "capture_error_artifacts" if state.get("should_stop") else "format_success_response"


def capture_error_artifacts(state: AgentState) -> AgentState:
    failed_step = state.get("failed_step") or {}
    local_screenshot = state.get("execution_result", {}).get("local_screenshot_path")
    if local_screenshot:
        suffix = Path(local_screenshot).suffix or ".png"
        object_key = f"execution-artifacts/{failed_step.get('step_id', 'unknown')}/failure{suffix}"
        with open(local_screenshot, "rb") as artifact:
            stored = storage.store_artifact(object_key, artifact.read())
        return {**state, "error_screenshot_path": stored["local_path"], "error_screenshot_minio_link": stored["minio_url"]}

    return {**state, "error_screenshot_path": None, "error_screenshot_minio_link": None}


def route_repair(state: AgentState) -> str:
    can_repair = state.get("repair_on_failure", False) and state.get("repair_attempts", 0) < state.get("max_repair_attempts", 0)
    failed_step = state.get("failed_step") or {}
    if can_repair and not failed_step.get("requires_secret"):
        return "repair_failed_function"
    return "format_failure_response"


def repair_failed_function(state: AgentState) -> AgentState:
    return {**state, "repair_attempts": state.get("repair_attempts", 0) + 1}


def format_success_response(state: AgentState) -> AgentState:
    generated = state.get("db_records", [])
    reused = [match["step_function"] for match in state.get("step_registry_matches", [])]
    execution_result = state.get("execution_result", {})
    response = {
        "success": True,
        "feature": state["parsed_feature"]["name"],
        "scenarios_executed": len(state.get("scenarios", [])) if state.get("execute", True) else 0,
        "steps_executed": execution_result.get("steps_executed", 0),
        "generated_functions": generated,
        "reused_functions": reused,
        "execution_result": {"status": execution_result.get("status", "passed")},
        "search_result_evidence": execution_result.get("search_result_evidence", []),
    }
    return {**state, "response": response}


def format_failure_response(state: AgentState) -> AgentState:
    failed = state.get("failed_step") or {}
    result = state.get("execution_result", {})
    response = {
        "success": False,
        "execution_status": "failed",
        "stopped_at": {
            "feature": state["parsed_feature"]["name"],
            "scenario": (state.get("failed_scenario") or {}).get("name"),
            "step_index": state.get("current_step_index"),
            "step": failed.get("raw_text"),
            "normalized_step": failed.get("normalized_text"),
            "function_id": failed.get("function_id"),
            "function_minio_link": failed.get("function_minio_link"),
        },
        "steps_executed": result.get("steps_executed", 0),
        "failedStep": {
            "id": failed.get("step_id") or failed.get("id"),
            "keyword": failed.get("effective_keyword"),
            "text": failed.get("raw_text"),
            "normalized_text": failed.get("normalized_text"),
            "error": result.get("message"),
        },
        "screenshot": state.get("error_screenshot_minio_link"),
        "search_result_evidence": result.get("search_result_evidence", []),
        "local_screenshot_path": result.get("local_screenshot_path"),
        "error": {
            "message": result.get("message"),
            "stdout": result.get("stdout", ""),
            "stderr": result.get("stderr", ""),
            "screenshot": state.get("error_screenshot_minio_link"),
        },
        "secrets_redacted": bool(state.get("confidential_fields")),
    }
    return {**state, "response": response}


def create_workflow():
    workflow = StateGraph(AgentState)
    workflow.add_node("parse_gherkin", parse_gherkin)
    workflow.add_node("normalize_steps", normalize_steps)
    workflow.add_node("classify_confidentiality", classify_confidentiality)
    workflow.add_node("lookup_step_registry", lookup_step_registry)
    workflow.add_node("retrieve_project_knowledge", retrieve_project_knowledge)
    workflow.add_node("generate_safe_step_functions", generate_safe_step_functions)
    workflow.add_node("handle_confidential_missing_steps", handle_confidential_missing_steps)
    workflow.add_node("validate_generated_functions", validate_generated_functions)
    workflow.add_node("store_functions_in_minio", store_functions_in_minio)
    workflow.add_node("save_step_function_metadata", save_step_function_metadata)
    workflow.add_node("compose_test_plan", compose_test_plan)
    workflow.add_node("execute_feature", execute_feature)
    workflow.add_node("capture_error_artifacts", capture_error_artifacts)
    workflow.add_node("repair_failed_function", repair_failed_function)
    workflow.add_node("format_success_response", format_success_response)
    workflow.add_node("format_failure_response", format_failure_response)

    workflow.add_edge(START, "parse_gherkin")
    workflow.add_edge("parse_gherkin", "normalize_steps")
    workflow.add_edge("normalize_steps", "classify_confidentiality")
    workflow.add_edge("classify_confidentiality", "retrieve_project_knowledge")
    workflow.add_edge("retrieve_project_knowledge", "lookup_step_registry")
    workflow.add_conditional_edges(
        "lookup_step_registry",
        route_missing_steps,
        {
            "compose_test_plan": "compose_test_plan",
            "generate_safe_step_functions": "generate_safe_step_functions",
            "handle_confidential_missing_steps": "handle_confidential_missing_steps",
        },
    )
    workflow.add_edge("generate_safe_step_functions", "validate_generated_functions")
    workflow.add_edge("handle_confidential_missing_steps", "validate_generated_functions")
    workflow.add_edge("validate_generated_functions", "store_functions_in_minio")
    workflow.add_edge("store_functions_in_minio", "save_step_function_metadata")
    workflow.add_edge("save_step_function_metadata", "compose_test_plan")
    workflow.add_edge("compose_test_plan", "execute_feature")
    workflow.add_conditional_edges(
        "execute_feature",
        route_execution,
        {
            "format_success_response": "format_success_response",
            "capture_error_artifacts": "capture_error_artifacts",
        },
    )
    workflow.add_conditional_edges(
        "capture_error_artifacts",
        route_repair,
        {
            "repair_failed_function": "repair_failed_function",
            "format_failure_response": "format_failure_response",
        },
    )
    workflow.add_edge("repair_failed_function", "format_failure_response")
    workflow.add_edge("format_success_response", END)
    workflow.add_edge("format_failure_response", END)
    return workflow.compile()
