from typing import Any, Dict, List, Optional, TypedDict


class AgentState(TypedDict, total=False):
    gherkin_text: str
    parsed_feature: Dict[str, Any]
    scenarios: List[Dict[str, Any]]
    steps: List[Dict[str, Any]]
    normalized_steps: List[Dict[str, Any]]
    confidential_fields: List[Dict[str, Any]]
    step_registry_matches: List[Dict[str, Any]]
    missing_steps: List[Dict[str, Any]]
    generated_step_functions: List[Dict[str, Any]]
    validated_step_functions: List[Dict[str, Any]]
    minio_links: List[Dict[str, Any]]
    db_records: List[Dict[str, Any]]
    composed_test_plan: Dict[str, Any]
    current_feature_id: Optional[str]
    current_scenario_id: Optional[str]
    current_step_id: Optional[str]
    current_step_index: int
    failed_step: Optional[Dict[str, Any]]
    failed_scenario: Optional[Dict[str, Any]]
    failed_feature: Optional[Dict[str, Any]]
    should_stop: bool
    error_screenshot_path: Optional[str]
    error_screenshot_minio_link: Optional[str]
    execution_result: Dict[str, Any]
    errors: List[Dict[str, Any]]
    cost_policy: Dict[str, Any]
    confidentiality_policy: Dict[str, Any]
    repair_attempts: int
    max_repair_attempts: int
    repair_on_failure: bool
    execute: bool
    headless: bool
    response: Dict[str, Any]
    component_catalog: List[Dict[str, Any]]
    project_context: Dict[str, Any]
    rag_context: List[Dict[str, Any]]
