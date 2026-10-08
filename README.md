# VPLMQA

> AI-assisted end-to-end testing, execution evidence and Azure DevOps tickets.

**React + TypeScript** | **Spring Boot + Java 21** | **LangGraph + Playwright** | **Docker Compose**

[Features](#features) | [Technologies](#technologies) | [VM Setup](#quick-start-test-vm) | [Windows Setup](#windows-development-launcher) | [Troubleshooting](#troubleshooting)

---

VPLMQA is an AI-assisted quality assurance platform for web applications. Testers
can describe user journeys, run end-to-end tests, inspect execution evidence and
prepare Azure DevOps tickets, while keeping human review in the workflow.

> [!NOTE]
> The current application focuses on **E2E testing**. The former Figma/Web comparison
> workflow is no longer exposed in the dashboard. The agents no longer require
> the old `ml/*.pkl` models; you do not need to copy them to a new machine.

## Choose Your Setup

| Your goal | Start here | How it runs |
| --- | --- | --- |
| Deploy or hand over to a test VM | [Automatic VM deployment](#quick-start-test-vm) | Containerized stack with generated configuration |
| Develop on Windows | [Development launcher](#windows-development-launcher) | Local Java services with Docker infrastructure, frontend and agents |

## Typical Workflow

1. **Prepare** a project and describe its user journeys in Gherkin.
2. **Run** scenarios with reusable functions and protected test inputs.
3. **Review** results, screenshots, logs and AI analysis.
4. **Track** confirmed issues with prefilled Azure DevOps tickets.

## Features

- Authentication and password changes.
- Project management and feature/scenario building with Gherkin.
- Reusable test functions, scenario inputs and secrets stored in Vault.
- Playwright execution coordinated by LangGraph agents.
- Results, logs, screenshots and AI analysis.
- Prefilled tickets with tags and Azure DevOps assignees.
- Azure DevOps connection configuration in Settings.

## Technologies

| Layer | Technology | Purpose |
| --- | --- | --- |
| Dashboard | React, TypeScript, Vite | Manage projects, tests, results and tickets |
| Backend | Java 21, Spring Boot | Authentication and domain microservices |
| Service infrastructure | Spring Cloud Gateway, Eureka, Config Server | API routing, discovery and configuration |
| Agents | Python, LangGraph, Mistral | Test orchestration and AI analysis |
| Browser | Playwright / Chromium | Execute journeys against the target application |
| Database | PostgreSQL / pgvector | Application data and vector storage |
| Messaging and cache | Kafka, Redis | Asynchronous communication and caching |
| Artifact storage | MinIO | Files and execution evidence |
| Secrets | HashiCorp Vault | API keys and test-account credentials |
| Development email | MailHog | Inspect notifications locally |
| Build and deployment | Maven, Docker Compose | Package and run the stack |

Legacy design-related services remain in the repository and Compose stack, but
their presence does not make the comparison workflow available in the current UI.

## Quick Start: Test VM

Use this route to deploy on a Windows or Linux test VM. The helper configures
internal service addresses and generates infrastructure credentials, so you do
not need to replace `localhost` throughout the source code.

### Requirements

- Git and access to this private repository.
- Python 3.9+ (`python3` may be required on Linux).
- Docker running, with Compose supporting `include` and `--wait`.
- A Mistral API key and internet access for images, dependencies and external APIs.
- Network access to the application under test and available stack ports.

Java, Maven and Node.js do not need to be installed on the VM for this route:
the application is built in containers.

### Get Your Mistral API Key

1. Sign in or create an account at [Mistral Studio](https://console.mistral.ai/).
2. Open **API Keys** and select **Create new key**.
3. Name the key (for example, `VPLMQA test VM`) and create it.
4. Copy it immediately and keep it in a password manager. The full key is shown
   only once; if you lose it, create a replacement.
5. Paste the key into the deployment script's hidden prompt when requested.

See [Mistral's official API-key setup guide](https://docs.mistral.ai/getting-started/quickstarts/studio/activate-and-generate-api-key)
for account setup and current usage limits. Never commit the key to this repository.

### First Deployment

```sh
git clone https://github.com/KARKOUCHZakaria/VPLMQA.git
cd VPLMQA
python scripts/deploy-vm.py --public-url http://10.0.0.50:3000
```

Replace `10.0.0.50` with the VM IP or DNS name reachable from your team's browsers.
The helper currently accepts **HTTP on port 3000**, not HTTPS. It prompts for the
Mistral key without displaying it, unless a saved key already exists.

It prepares configuration, packages the Java services, builds the frontend and
agents, starts Compose and checks readiness. Wait for `ALL SERVICES ARE READY`,
then open `http://10.0.0.50:3000`. The first application screen is the login page.

Configure projects, test-account secrets and Azure DevOps inside the application.
Validate a real test and ticket creation before handover: service health checks
alone do not prove these workflows work.

### Script Options

| Option | Purpose |
| --- | --- |
| `--prepare-only` | Generate configuration without building or starting services |
| `--llm-key-file /path/to/key.txt` | Supply or replace the Mistral key from a private file |
| `--model mistral-small-latest` | Choose the model used by the helper |
| `--timeout 900` | Increase readiness timeout in seconds |
| `--skip-java-build` | Skip packaging only when current Java JARs already exist |

Do not pass API keys directly in commands or include them in screenshots.

### Generated Configuration

| File | Contents |
| --- | --- |
| `.deployment/vm.env` | PostgreSQL, Redis, MinIO, Vault and JWT credentials/settings |
| `.deployment/compose.vm.json` | VM-specific Compose overrides and internal service URLs |
| `front-end/.vm-nginx.conf` | Same-origin API, artifact and storage proxy configuration |
| `secrets/mistral_api_key.txt` | Private Mistral API key |

These files are ignored by Git. Restrict access to them on the VM. Running the
helper again preserves generated passwords. The root `.env` is left unchanged,
or a placeholder is created if it is missing.

> [!IMPORTANT]
> Keep `.deployment/vm.env`. Deleting it regenerates passwords but does not
> change credentials already stored in an existing database volume.

### Update and Operations

**Pull and redeploy**

```sh
git pull --ff-only
python scripts/deploy-vm.py --public-url http://10.0.0.50:3000
```

Always use the generated VM configuration when inspecting or stopping this stack:

**Inspect service status**

```sh
docker compose --env-file .deployment/vm.env -f docker-compose.yml -f .deployment/compose.vm.json ps
```

**Read recent gateway and agent logs**

```sh
docker compose --env-file .deployment/vm.env -f docker-compose.yml -f .deployment/compose.vm.json logs --tail 100 gateway langgraph-agents
```

**Stop without deleting stored data**

```sh
docker compose --env-file .deployment/vm.env -f docker-compose.yml -f .deployment/compose.vm.json stop
```

Run the deployment helper again to start or rebuild it. Do not use `down -v`
unless you intend to delete stored data. See the
[VM deployment guide](docs/vm-deployment.md) for handover checks and limitations.

## Windows Development Launcher

This setup runs infrastructure, frontend and agents in Docker, and Spring services
locally through Maven.

> [!WARNING]
> Do not run the Windows launcher alongside the VM stack on the same machine.
> Their published ports overlap.

### Prepare Your Machine

1. Install Docker Desktop and start its engine.
2. Install JDK 21 and make Java available to the launcher.
3. Extract Maven 3.9.9 so `maven/apache-maven-3.9.9/bin/mvn.cmd` exists.
   This machine-specific installation is not included in Git.
4. Install Python 3.10+ and Google Chrome for the local browser runner.
### Configure the Application

Copy the environment template:

```powershell
Copy-Item .env.example .env
```

1. Replace placeholder infrastructure passwords in `.env`. Use a `JWT_SECRET`
   of at least 64 characters and keep `VAULT_TOKEN` consistent with your local
   Vault setup. The template contains legacy provider settings; local Compose
   currently defaults to Mistral.
2. Create `secrets/mistral_api_key.txt` in a secure editor, containing only your
   API key. Keep it private. Optional `.env` overrides include
   `LLM_PROVIDER=mistral` and `MISTRAL_MODEL=mistral-small-latest`.
### Start and Stop

Open the launcher:

```powershell
.\VPLMQA_LAUNCHER.bat
```

Select **Start All**, or run `.\START_VPLMQA_LOCAL.bat`. Wait for readiness before
opening [the dashboard](http://localhost:3000). The first build may take longer
while images and dependencies download. Inspect the launcher log panel or
`.local-logs/start-all.log` for progress and failures.

Stop with **Stop All** or `.\STOP_VPLMQA_LOCAL.bat`. After backend code changes,
stop and restart so the launcher does not reuse already-running services.

### Local Addresses

| Component | Address |
| --- | --- |
| Dashboard | `http://localhost:3000` |
| Gateway | `http://localhost:8080` |
| Eureka | `http://localhost:8761` |
| Agents | `http://localhost:8090` |
| MinIO API / console | `http://localhost:9005` / `http://localhost:9006` |
| Vault | `http://localhost:8200` |
| MailHog | `http://localhost:8025` |
| PostgreSQL | `localhost:5434` |
| Redis | `localhost:6379` |

On the VM, browser requests use the configured frontend origin, while containers
communicate through internal service names.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| Gateway unreachable / Failed to fetch | Wait for readiness, inspect gateway logs and confirm the correct stack is running |
| Slow startup | Inspect build logs, Docker resources, disk space and internet access |
| Port already in use | Stop the other stack or process occupying that port |
| LLM authentication/quota failure | Check the saved key, model, quota and outbound network access |
| VM dashboard unreachable | Check the public URL, network/VPN and firewall access to port 3000 |
| Test target unreachable | Check access from the VM/container network, including VPN, proxy and certificates |
| Azure assignees missing | Verify the connection and PAT permissions in Settings, then refresh members |

When reporting issues, provide the failed service and relevant **redacted** logs,
not full environment files or credentials.

## Developer Checks

From the repository root:

**Deployment helper tests**

```sh
python scripts/test_deploy_vm.py -v
```

**Java packaging and tests**

```sh
mvn -B package -DskipTests
mvn -B -pl e2e-service,project-service,ticket-service -am test
```

**Frontend container build**

```sh
docker compose -f docker-compose.local.yml build front-end
```

Deployment tests require Docker for configuration and Nginx checks. Maven commands
require host JDK 21 and Maven; Windows launcher users can invoke
`maven/apache-maven-3.9.9/bin/mvn.cmd` instead of `mvn`. Packaging with `-DskipTests`
is not a behavioral test. Verify login, test execution, evidence retrieval and
Azure synchronization separately.

## Security and Data

- The deployment helper is for an isolated test environment, not public production hosting.
- It does not configure firewall rules, HTTPS, VPN access or certificates.
- Vault runs in development mode. Dynamically entered secrets can be lost after
  restart/recreation; use secure backups or persistent Vault before relying on
  the environment. The saved Mistral key is restored by `vault-init`.
- Cloning the repository does not transfer existing PostgreSQL or MinIO data.
  Plan backup and restore separately if existing workspace data is required.
- Keep secrets, logs, recordings, generated artifacts and machine-specific
  dependencies out of Git, even when the repository is private.
