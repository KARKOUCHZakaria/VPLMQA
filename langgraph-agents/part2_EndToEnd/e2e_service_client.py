import os
import requests
from typing import Dict, Any, Optional

class E2EServiceClient:
    def __init__(self, base_url: str = "http://localhost:8083/api"):
        self.base_url = base_url

    def get_feature_full(self, feature_id: str) -> Dict[str, Any]:
        resp = requests.get(f"{self.base_url}/features/{feature_id}/full")
        resp.raise_for_status()
        return resp.json()

    def update_feature_status(self, feature_id: str, status: str) -> Dict[str, Any]:
        resp = requests.put(f"{self.base_url}/features/{feature_id}", json={"status": status})
        resp.raise_for_status()
        return resp.json()

    def generate_gherkin(self, feature_id: str) -> str:
        resp = requests.get(f"{self.base_url}/features/{feature_id}/export-gherkin")
        resp.raise_for_status()
        return resp.text

    def create_test_execution(self, feature_id: str) -> Dict[str, Any]:
        resp = requests.post(f"{self.base_url}/test-executions", json={"featureId": feature_id})
        resp.raise_for_status()
        return resp.json()

    def update_test_execution(self, execution_id: str, data: Dict[str, Any]) -> Dict[str, Any]:
        resp = requests.put(f"{self.base_url}/test-executions/{execution_id}", json=data)
        resp.raise_for_status()
        return resp.json()
