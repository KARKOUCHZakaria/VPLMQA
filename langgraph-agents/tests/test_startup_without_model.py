"""Regression checks for E2E startup without the retired comparison model."""

import sys
import unittest
from unittest.mock import Mock, patch

from fastapi.testclient import TestClient

import main


class StartupWithoutModelTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(main.app)

    def test_health_without_loading_model_modules(self):
        self.assertNotIn("part1_figma_to_design.agents.ml_prediction", sys.modules)
        self.assertNotIn("part1_figma_to_design.graph", sys.modules)
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "ok"})

    def test_retired_model_endpoints_are_absent(self):
        self.assertEqual(self.client.get("/api/v1/agents/part1/model/status").status_code, 404)
        self.assertEqual(self.client.post("/api/v1/agents/part1/run", json={"rows": []}).status_code, 404)

    def test_e2e_route_still_invokes_workflow(self):
        graph = Mock()
        graph.invoke.return_value = {"response": {"status": "passed"}}
        with patch.object(main, "create_workflow", return_value=graph):
            response = self.client.post("/gherkin/e2e/run", json={
                "gherkin": "Feature: Login\n  Scenario: Open login\n    Given the application is open",
                "execute": False,
            })
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "passed"})
        self.assertFalse(graph.invoke.call_args.args[0]["execute"])

    def test_ticket_report_route_still_calls_report_agent(self):
        report = {"title": "Login failed", "description": "The login step failed."}
        with patch.object(main, "generate_ticket_report", return_value=report) as generate:
            response = self.client.post("/api/v1/agents/part2/ticket-report", json={"featureName": "Login"})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), report)
        self.assertEqual(generate.call_args.args[0]["featureName"], "Login")


if __name__ == "__main__":
    unittest.main()
