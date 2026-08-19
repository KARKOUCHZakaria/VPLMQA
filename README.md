# VPLMQA

VPLMQA is an AI-assisted quality assurance platform for web applications.
It combines microservices, End-to-End test automation, design comparison,
and ticket generation to help QA teams validate applications more efficiently.

## Main Modules

- Spring Boot microservices
- React dashboard
- Playwright-based E2E execution
- LangGraph agents
- Figma/Web design comparison
- Ticket generation and Azure DevOps integration
- Docker-based local infrastructure

## Local Setup

1. Copy `.env.example` to `.env`.
2. Adjust local secrets and service configuration.
3. Start the stack with the local launcher or Docker Compose.
4. Open the dashboard and run the QA workflows.

## Note

This repository intentionally ignores local logs, secrets, temporary files,
large recordings, generated payloads, and machine-specific dependencies.
