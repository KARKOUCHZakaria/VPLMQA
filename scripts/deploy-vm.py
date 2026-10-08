"""Prepare and deploy the Docker stack on an isolated test VM (Python 3.9+)."""

import argparse
import getpass
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


REPO = Path(__file__).resolve().parents[1]
SPRING = {
    "auth-service": (8081, "auth_db"),
    "project-service": (8088, "project_db"),
    "member-service": (8089, "member_db"),
    "design-service": (8082, "design_db"),
    "e2e-service": (8083, "tests_db"),
    "ticket-service": (8084, "ticket_db"),
    "notification-service": (8085, "notification_db"),
    "analytics-service": (8086, "analytics_db"),
    "gateway": (8080, "postgres"),
}

NGINX = """server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;
    client_max_body_size 30m;
    location /api/ {
        proxy_pass http://gateway:8080;
        proxy_set_header Host $http_host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 600s;
    }
    location /gherkin/ {
        proxy_pass http://gateway:8080;
        proxy_set_header Host $http_host;
        proxy_read_timeout 600s;
    }
    location /artifacts/ {
        proxy_pass http://langgraph-agents:8090;
    }
    location /storage/ {
        proxy_pass http://minio:9000/;
    }
    location / {
        try_files $uri $uri/ /index.html;
        add_header Cache-Control "no-cache" always;
    }
}
"""


def public_origin(value):
    parsed = urllib.parse.urlsplit(value.strip())
    if (
        parsed.scheme != "http" or not parsed.hostname or parsed.port != 3000
        or parsed.username or parsed.password or parsed.path not in ("", "/")
        or parsed.query or parsed.fragment
    ):
        raise ValueError("Use http://VM_IP_OR_HOST:3000. HTTPS/reverse-proxy setup is separate.")
    return f"http://{parsed.netloc}"


def read_env(path):
    if not path.exists():
        return {}
    return dict(line.split("=", 1) for line in path.read_text(encoding="utf-8").splitlines()
                if line and not line.startswith("#") and "=" in line)


def private_write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    if os.name != "nt":
        path.chmod(0o600)


def prepare(repo, origin, model, key_file=None):
    directory = repo / ".deployment"
    env_path = directory / "vm.env"
    values = read_env(env_path)
    for name in ("POSTGRES_PASSWORD", "REDIS_PASSWORD", "MINIO_ROOT_PASSWORD", "JWT_SECRET", "VAULT_TOKEN"):
        values.setdefault(name, secrets.token_hex(48))
    values.update({
        "POSTGRES_USER": "vplmqa", "MINIO_ROOT_USER": "vplmqa",
        "KAFKA_BOOTSTRAP_SERVERS": "kafka:9092", "MINIO_ENDPOINT": "http://minio:9000",
        "VAULT_ADDR": "http://vault:8200", "LLM_PROVIDER": "mistral",
        "MISTRAL_MODEL": model, "MISTRAL_VISION_MODEL": model, "TICKET_REPORT_MODEL": model,
        "GATEWAY_PUBLIC_URL": origin,
    })
    if not model or any(character.isspace() for character in model) or any(c in model for c in "'\"#$"):
        raise ValueError("Provide a valid Mistral model identifier.")
    secret_path = repo / "secrets" / "mistral_api_key.txt"
    if key_file:
        key = Path(key_file).read_text(encoding="utf-8-sig").strip()
    elif secret_path.exists() and secret_path.read_text(encoding="utf-8-sig").strip():
        key = secret_path.read_text(encoding="utf-8-sig").strip()
    else:
        key = getpass.getpass("Mistral API key (hidden): ").strip()
    if not key or any(c.isspace() for c in key):
        raise ValueError("The Mistral API key must be nonempty and contain no whitespace.")
    private_write(secret_path, key)
    private_write(env_path, "".join(f"{name}={value}\n" for name, value in sorted(values.items())))
    # Included Compose files require .env; never replace an existing local file.
    if not (repo / ".env").exists():
        private_write(repo / ".env", "# VM values are supplied by .deployment/vm.env\n")
    services = {}
    for name, (port, database) in SPRING.items():
        environment = {
            "SERVER_PORT": str(port), "SPRING_CONFIG_IMPORT": "configserver:http://config-service:8087",
            "SPRING_CLOUD_CONFIG_URI": "http://config-service:8087",
            "EUREKA_URI": "http://eureka-server:8761/eureka",
            "EUREKA_CLIENT_SERVICEURL_DEFAULTZONE": "http://eureka-server:8761/eureka",
            "DB_HOST": "postgres", "DB_PORT": "5432", "DB_NAME": database,
            "DB_USER": "${POSTGRES_USER}", "DB_PASSWORD": "${POSTGRES_PASSWORD}",
            "SPRING_DATASOURCE_URL": f"jdbc:postgresql://postgres:5432/{database}",
            "SPRING_DATASOURCE_USERNAME": "${POSTGRES_USER}",
            "SPRING_DATASOURCE_PASSWORD": "${POSTGRES_PASSWORD}",
            "REDIS_HOST": "redis", "REDIS_PASSWORD": "${REDIS_PASSWORD}",
            "SPRING_KAFKA_BOOTSTRAP_SERVERS": "kafka:9092",
            "MINIO_ENDPOINT": "http://minio:9000", "MINIO_URL": "http://minio:9000",
            "MINIO_ROOT_USER": "${MINIO_ROOT_USER}", "MINIO_ROOT_PASSWORD": "${MINIO_ROOT_PASSWORD}",
            "VAULT_ADDR": "http://vault:8200", "VAULT_URL": "http://vault:8200",
            "VAULT_TOKEN_FILE": "/run/secrets/vault_token",
            "LANGGRAPH_AGENTS_URL": "http://langgraph-agents:8090",
            "PROJECT_SERVICE_URL": "http://project-service:8088",
            "DESIGN_SERVICE_URL": "http://design-service:8082",
            "SECURITY_JWT_SECRET": "${JWT_SECRET}", "JWT_SECRET": "${JWT_SECRET}",
            "SECURITY_JWT_ISSUER": "vplmqa", "MANAGEMENT_TRACING_ENABLED": "false",
            "GATEWAY_PUBLIC_URL": origin,
        }
        if name == "gateway":
            environment["SPRING_APPLICATION_JSON"] = json.dumps({"spring": {"cloud": {"gateway": {
                "globalcors": {"cors-configurations": {"[/**]": {
                    "allowedOrigins": [origin], "allowedMethods": ["*"],
                    "allowedHeaders": ["*"], "allowCredentials": True,
                }}}
            }}}})
        services[name] = {"environment": environment, "restart": "unless-stopped"}
    services["config-service"] = {"environment": {
        "SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS": "file:/app/config-repo",
    }, "restart": "unless-stopped"}
    services["front-end"] = {"build": {"args": {
        "VITE_GATEWAY_URL": origin,
        "VITE_MINIO_PUBLIC_BASE_URL": origin + "/storage/figma-designs",
        "NGINX_CONF": ".vm-nginx.conf",
    }}, "restart": "unless-stopped"}
    services["langgraph-agents"] = {"environment": {
        "E2E_BROWSER_CDP_URL": "", "E2E_BROWSER_CDP_REQUIRED": "false",
        "E2E_BROWSER_LAUNCHER_URL": "", "E2E_BROWSER_CLOSE_URL": "",
        "E2E_BROWSER_HEADLESS": "true", "E2E_BROWSER_SLOW_MO_MS": "80",
        "E2E_ARTIFACT_PUBLIC_BASE_URL": origin + "/artifacts",
    }, "volumes": ["vm-agent-output:/app/output"], "restart": "unless-stopped"}
    private_write(repo / "front-end" / ".vm-nginx.conf", NGINX)
    override = directory / "compose.vm.json"
    private_write(override, json.dumps({"name": "vplmqa-vm", "services": services,
                                        "volumes": {"vm-agent-output": {}}}, indent=2))
    return env_path, override


def run(command, repo, capture=False):
    # Compose uses the generated env file, not inherited local launcher settings.
    environment = dict(os.environ)
    for name in read_env(repo / ".deployment" / "vm.env"):
        environment.pop(name, None)
    result = subprocess.run(command, cwd=repo, env=environment, check=True,
                            stdout=subprocess.PIPE if capture else None,
                            stderr=subprocess.PIPE if capture else None, text=True)
    return result.stdout if capture else ""


def compose_command(repo):
    return ["docker", "compose", "--env-file", str(repo / ".deployment" / "vm.env"),
            "-f", str(repo / "docker-compose.yml"),
            "-f", str(repo / ".deployment" / "compose.vm.json")]


def check_ready(timeout):
    pending = {
        "frontend": ("http://127.0.0.1:3000", False),
        "gateway": ("http://127.0.0.1:8080/actuator/health", True),
        "agents": ("http://127.0.0.1:8090/health", True),
    }
    deadline = time.monotonic() + timeout
    while pending:
        for name, (url, health) in list(pending.items()):
            try:
                with urllib.request.urlopen(url, timeout=3) as response:
                    if response.status != 200:
                        continue
                    if health and json.load(response).get("status", "").lower() not in ("up", "ok"):
                        continue
                print(f"READY: {name}", flush=True)
                del pending[name]
            except (OSError, ValueError):
                pass
        if pending:
            if time.monotonic() >= deadline:
                raise RuntimeError("Not ready: " + ", ".join(pending) + ". Inspect Docker Compose logs.")
            print("Waiting for: " + ", ".join(pending), flush=True)
            time.sleep(3)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--public-url", help="Address used by colleagues, e.g. http://10.0.0.50:3000")
    parser.add_argument("--model", default="mistral-small-latest")
    parser.add_argument("--llm-key-file", type=Path, help="Read the key from a protected file; never pass the key itself")
    parser.add_argument("--prepare-only", action="store_true", help="Generate settings without building or starting services")
    parser.add_argument("--skip-java-build", action="store_true", help="Use previously built JARs")
    parser.add_argument("--timeout", type=int, default=600)
    args = parser.parse_args()
    origin = public_origin(args.public_url or input("VM frontend URL (http://VM:3000): "))
    if args.timeout < 1:
        raise ValueError("Timeout must be positive.")
    if not args.prepare_only:
        if not shutil.which("docker"):
            raise RuntimeError("Install Docker Engine/Desktop and Docker Compose on the VM first.")
        run(["docker", "info"], REPO, capture=True)
    prepare(REPO, origin, args.model, args.llm_key_file)
    print("VM settings generated. Existing generated passwords were preserved; secrets are not printed.", flush=True)
    print("This prepares an isolated test deployment using the existing development-mode Vault.", flush=True)
    if args.prepare_only:
        print("No services started. Run again without --prepare-only on the VM.")
        return
    compose = compose_command(REPO)
    run(compose + ["config", "--quiet"], REPO)
    if not args.skip_java_build:
        print("Building Java services with a Java 21 Maven container...", flush=True)
        run(["docker", "run", "--rm", "--mount", f"type=bind,source={REPO},target=/workspace",
             "--mount", "type=volume,source=vplmqa-vm-maven-cache,target=/root/.m2",
             "-w", "/workspace", "maven:3.9.9-eclipse-temurin-21", "mvn", "-B", "package", "-DskipTests"], REPO)
    print("Building and starting the VM stack...", flush=True)
    run(compose + ["up", "-d", "--build", "--wait", "--wait-timeout", str(args.timeout)], REPO)
    check_ready(args.timeout)
    print(f"ALL SERVICES ARE READY - {origin}")
    print("Enter Azure DevOps, Figma and test-account credentials through the application.")
    print("Existing PC data was not migrated. Restrict this test deployment to the team network.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as exc:
        # Do not print command stdout/stderr: resolved Compose output may contain secrets.
        print(f"DEPLOYMENT FAILED: {exc}", file=sys.stderr)
        sys.exit(1)
    except (KeyboardInterrupt, EOFError):
        print("Deployment cancelled.", file=sys.stderr)
        sys.exit(1)
