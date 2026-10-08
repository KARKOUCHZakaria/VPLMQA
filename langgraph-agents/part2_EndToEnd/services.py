import ast
import base64
import hashlib
import html
import json
import os
import random
import re
import socket
import time
import unicodedata
import uuid
import urllib.parse
import urllib.request
import urllib.error
from io import BytesIO
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional

from shared.llm_provider import generate_json


STEP_KEYWORDS = {"Given", "When", "Then", "And", "But"}
SECRET_HINTS = ("password", "token", "api key", "apikey", "secret", "session cookie", "cookie", "card", "cvv", "payment")
BANNED_IMPORTS = {"os", "subprocess", "socket", "shutil", "pathlib", "requests", "httpx"}
BANNED_CALLS = {"eval", "exec", "open", "__import__"}


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def normalized_hash(text: str) -> str:
    return hashlib.sha256(text.strip().lower().encode("utf-8")).hexdigest()


def function_name_for(text: str) -> str:
    words = re.sub(r"[^a-zA-Z0-9]+", " ", text.lower()).strip().split()
    stem = "_".join(words[:8]) or "step"
    return f"step_{stem}"


class GherkinParser:
    def parse(self, gherkin_text: str) -> Dict[str, Any]:
        feature: Dict[str, Any] = {"name": "Imported Feature", "tags": [], "scenarios": []}
        pending_tags: List[str] = []
        current_scenario: Optional[Dict[str, Any]] = None
        last_keyword: Optional[str] = None

        for raw_line in gherkin_text.splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("@"):
                pending_tags.extend(line.split())
                continue
            if line.lower().startswith("feature:"):
                feature["name"] = line.split(":", 1)[1].strip() or feature["name"]
                feature["tags"] = pending_tags
                pending_tags = []
                continue
            if line.lower().startswith("scenario:"):
                current_scenario = {
                    "id": str(uuid.uuid4()),
                    "name": line.split(":", 1)[1].strip(),
                    "tags": pending_tags,
                    "steps": [],
                }
                feature["scenarios"].append(current_scenario)
                pending_tags = []
                last_keyword = None
                continue

            parts = line.split(" ", 1)
            if len(parts) == 2 and parts[0] in STEP_KEYWORDS and current_scenario is not None:
                keyword = parts[0]
                effective_keyword = last_keyword if keyword in {"And", "But"} and last_keyword else keyword
                last_keyword = effective_keyword
                current_scenario["steps"].append(
                    {
                        "id": str(uuid.uuid4()),
                        "keyword": keyword,
                        "effective_keyword": effective_keyword,
                        "raw_text": parts[1].strip(),
                        "sequence_order": len(current_scenario["steps"]) + 1,
                    }
                )

        return feature


class StepNormalizer:
    def normalize(self, step: Dict[str, Any]) -> Dict[str, Any]:
        text = step["raw_text"]
        args: Dict[str, Any] = {}
        normalized = text
        placeholder_index = 1

        for match in list(re.finditer(r'"([^"]*)"|\'([^\']*)\'', text)):
            value = match.group(1) if match.group(1) is not None else match.group(2)
            key = self._argument_name(text[: match.start()], value, placeholder_index)
            args[key] = value
            normalized = normalized.replace(match.group(0), "{" + key + "}", 1)
            placeholder_index += 1

        for match in list(re.finditer(r"\b\d+(?:\.\d+)?\b", normalized)):
            key = f"value_{placeholder_index}"
            args[key] = match.group(0)
            normalized = normalized.replace(match.group(0), "{" + key + "}", 1)
            placeholder_index += 1

        normalized_text = f"{step['effective_keyword']} {normalized}"
        return {
            **step,
            "normalized_text": normalized_text,
            "normalized_step_hash": normalized_hash(normalized_text),
            "arguments": args,
        }

    def _argument_name(self, prefix: str, value: str, index: int) -> str:
        secret_match = re.fullmatch(r"\$\{SECRET\.([^}]+)\}", value.strip(), re.I)
        if secret_match:
            return re.sub(r"[^a-z0-9_]+", "_", secret_match.group(1).strip().lower()).strip("_") or f"value_{index}"
        lower = prefix.lower()
        if "password" in lower:
            return "password"
        if "email" in lower or "e-mail" in lower:
            return "email"
        if "token" in lower:
            return "token"
        if "username" in lower or "user name" in lower:
            return "username"
        cleaned = re.sub(r"[^a-z0-9]+", "_", lower).strip("_").split("_")
        return (cleaned[-1] if cleaned else f"value_{index}") or f"value_{index}"


class ConfidentialityService:
    def classify(self, normalized_step: Dict[str, Any]) -> Dict[str, Any]:
        confidential: List[str] = []
        redacted_args: Dict[str, Any] = {}
        normalized_text = normalized_step["normalized_text"].lower()

        for key, value in normalized_step.get("arguments", {}).items():
            is_secret = any(hint in key.lower() or hint in normalized_text for hint in SECRET_HINTS)
            if is_secret:
                confidential.append(key)
                redacted_args[key] = f"${{SECRET.{key.upper()}}}"
            else:
                redacted_args[key] = value

        return {
            **normalized_step,
            "requires_secret": bool(confidential),
            "allowed_cloud_llm": not confidential,
            "confidential_fields": confidential,
            "arguments": redacted_args,
        }


class StepRegistryRepository:
    def __init__(self, path: Optional[str] = None) -> None:
        self.database_url = os.getenv("STEP_REGISTRY_DATABASE_URL", "").strip()
        self.path = Path(path or os.getenv("STEP_REGISTRY_PATH", "/app/output/step_registry.json"))
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if not self.path.exists():
            self.path.write_text("[]", encoding="utf-8")

    def find(self, step: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        database_record = self._find_database(step["normalized_step_hash"])
        if database_record:
            return database_record
        for record in self._read():
            if record["normalized_step_hash"] == step["normalized_step_hash"]:
                return record
            if record["normalized_text"].lower() == step["normalized_text"].lower():
                return record
        return None

    def save(self, record: Dict[str, Any]) -> Dict[str, Any]:
        if self._save_database(record):
            return record
        records = self._read()
        records.append(record)
        self.path.write_text(json.dumps(records, indent=2), encoding="utf-8")
        return record

    def next_version(self, step_hash: str) -> int:
        database_version = self._next_database_version(step_hash)
        if database_version is not None:
            return database_version
        versions = [r.get("version", 0) for r in self._read() if r.get("normalized_step_hash") == step_hash]
        return max(versions, default=0) + 1

    def _read(self) -> List[Dict[str, Any]]:
        return json.loads(self.path.read_text(encoding="utf-8"))

    def _connect(self) -> Any:
        if not self.database_url:
            return None
        import psycopg
        connection = psycopg.connect(self.database_url)
        with connection.cursor() as cursor:
            cursor.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"")
            cursor.execute("""CREATE TABLE IF NOT EXISTS step_function_registry (
                id UUID PRIMARY KEY DEFAULT uuid_generate_v4(), intent_signature VARCHAR(500) NOT NULL,
                intent_hash VARCHAR(64) NOT NULL, project_id UUID, function_name VARCHAR(255) NOT NULL,
                artifact_bucket VARCHAR(255) NOT NULL, artifact_key VARCHAR(1000) NOT NULL,
                input_schema JSONB NOT NULL DEFAULT '{}'::jsonb, output_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
                version INT NOT NULL, status VARCHAR(30) NOT NULL DEFAULT 'VALIDATED',
                requires_secret BOOLEAN NOT NULL DEFAULT FALSE, validation_evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
                metadata JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ DEFAULT NOW(),
                updated_at TIMESTAMPTZ DEFAULT NOW(), UNIQUE(intent_hash, project_id, version))""")
        connection.commit()
        return connection

    def _find_database(self, step_hash: str) -> Optional[Dict[str, Any]]:
        try:
            connection = self._connect()
            if connection is None:
                return None
            with connection, connection.cursor() as cursor:
                cursor.execute("""SELECT metadata FROM step_function_registry
                                  WHERE intent_hash=%s AND status IN ('APPROVED', 'VALIDATED')
                                  ORDER BY project_id NULLS LAST, version DESC LIMIT 1""", (step_hash,))
                row = cursor.fetchone()
                return row[0] if row else None
        except Exception:
            return None

    def _save_database(self, record: Dict[str, Any]) -> bool:
        try:
            connection = self._connect()
            if connection is None:
                return False
            with connection, connection.cursor() as cursor:
                cursor.execute("""INSERT INTO step_function_registry
                    (intent_signature,intent_hash,function_name,artifact_bucket,artifact_key,input_schema,
                     output_schema,version,status,requires_secret,validation_evidence,metadata)
                    VALUES (%s,%s,%s,%s,%s,%s::jsonb,%s::jsonb,%s,'VALIDATED',%s,%s::jsonb,%s::jsonb)""",
                    (record["normalized_text"], record["normalized_step_hash"], record["function_name"],
                     record["minio_bucket"], record["minio_object_key"], json.dumps(record["input_schema"]),
                     json.dumps(record["output_schema"]), record["version"], record["requires_secret"],
                     json.dumps({"confidence": record.get("confidence_score"), "model": record.get("model_used")}),
                     json.dumps(record)))
            return True
        except Exception:
            return False

    def _next_database_version(self, step_hash: str) -> Optional[int]:
        try:
            connection = self._connect()
            if connection is None:
                return None
            with connection, connection.cursor() as cursor:
                cursor.execute("SELECT COALESCE(MAX(version),0)+1 FROM step_function_registry WHERE intent_hash=%s", (step_hash,))
                return int(cursor.fetchone()[0])
        except Exception:
            return None


class StepStorageService:
    def __init__(self, root: Optional[str] = None, bucket: Optional[str] = None) -> None:
        self.root = Path(root or os.getenv("STEP_FUNCTION_STORAGE_ROOT", "/app/output"))
        self.bucket = bucket or os.getenv("STEP_FUNCTION_BUCKET", "e2e-artifacts")
        self.root.mkdir(parents=True, exist_ok=True)
        self.minio = self._create_minio_client()

    def store_code(self, step_hash: str, version: int, code: str) -> Dict[str, str]:
        object_key = f"step-functions/{step_hash}/v{version}.py"
        path = self.root / object_key
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(code, encoding="utf-8")
        self._put(object_key, code.encode("utf-8"), "text/x-python")
        return {
            "minio_bucket": self.bucket,
            "minio_object_key": object_key,
            "minio_url": f"minio://{self.bucket}/{object_key}",
            "local_path": str(path),
        }

    def store_artifact(self, object_key: str, content: bytes) -> Dict[str, str]:
        path = self.root / object_key
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        self._put(object_key, content, "application/octet-stream")
        public_base_url = os.getenv("E2E_ARTIFACT_PUBLIC_BASE_URL", "http://localhost:8090/artifacts").rstrip("/")
        return {
            "minio_url": f"{public_base_url}/{object_key}",
            "local_path": str(path),
            "object_key": object_key,
        }

    def _create_minio_client(self) -> Any:
        endpoint = os.getenv("MINIO_ENDPOINT", "").replace("http://", "").replace("https://", "").strip("/")
        if not endpoint:
            return None
        try:
            from minio import Minio
            client = Minio(
                endpoint,
                access_key=os.getenv("MINIO_ACCESS_KEY", ""),
                secret_key=os.getenv("MINIO_SECRET_KEY", ""),
                secure=os.getenv("MINIO_SECURE", "false").lower() in {"1", "true", "yes"},
            )
            if not client.bucket_exists(self.bucket):
                client.make_bucket(self.bucket)
            return client
        except Exception:
            return None

    def _put(self, object_key: str, content: bytes, content_type: str) -> None:
        if self.minio is None:
            return
        self.minio.put_object(self.bucket, object_key, BytesIO(content), len(content), content_type=content_type)


class StepFunctionGenerator:
    def generate(self, step: Dict[str, Any], model: str) -> Dict[str, Any]:
        name = function_name_for(step["normalized_text"])
        schema = {key: {"type": "string"} for key in step.get("arguments", {}).keys()}
        prompt = self._generation_prompt(step)
        code = (
            "from typing import Any, Dict\n\n"
            f"async def {name}(page: Any, args: Dict[str, Any]) -> Dict[str, Any]:\n"
            f"    \"\"\"Reusable Playwright step for: {step['normalized_text']}\"\"\"\n"
            "    if page is None:\n"
            "        return {\"status\": \"skipped\", \"reason\": \"no Playwright page supplied\"}\n"
            "    # Runtime execution is handled by the guarded VPLMQA executor.\n"
            "    # This artifact documents the reusable intent and input schema.\n"
            "    return {\"status\": \"delegated\", \"executor\": \"guarded-runtime\"}\n"
        )
        return {
            "normalized_step_hash": step["normalized_step_hash"],
            "normalized_text": step["normalized_text"],
            "function_name": name,
            "code": code,
            "generation_prompt": prompt,
            "input_schema": schema,
            "output_schema": {"status": {"type": "string"}},
            "created_by": "llm" if step.get("allowed_cloud_llm") else "manual",
            "model_used": model,
            "confidence_score": 0.78,
            "requires_secret": step.get("requires_secret", False),
            "allowed_cloud_llm": step.get("allowed_cloud_llm", False),
            "status": "draft",
        }

    def _generation_prompt(self, step: Dict[str, Any]) -> str:
        return f"""You are generating one reusable Playwright step function for VPLMQA.

Step intent:
{step['normalized_text']}

Rules:
- Generate only a small reusable function for this single step.
- Inputs must come from args; never hardcode argument values.
- For generated test data that is reused later, write human Gherkin such as
  "I enter a valid article reference in the reference field" and later
  "I should see the created article reference". The executor will ask the LLM
  for the real value and remember it for affected assertions, clicks, and searches.
- Prefer locators in this order: data-testid, id, role/name, label, placeholder, visible text.
- If interacting with a project page, use project page metadata/RAG context before guessing URLs.
- Add one safe self-healing fallback for UI drift, then assert the target state.
- If recovery fails, raise AssertionError with current URL and visible page clue.
- Do not leak passwords, tokens, API keys, cookies, or secret values to logs/prompts/cloud LLMs.
- Return structured status and evidence only after the action/assertion truly succeeded.
- Keep execution stable and human-like: every action must target one clear UI intent,
  wait for the affected page/state, and fail at the exact step if the state is not reached.
"""


class StepFunctionValidator:
    def validate(self, generated: Dict[str, Any], step: Dict[str, Any]) -> Dict[str, Any]:
        errors: List[str] = []
        try:
            tree = ast.parse(generated["code"])
        except SyntaxError as exc:
            errors.append(f"syntax: {exc}")
            tree = None

        if tree is not None:
            for node in ast.walk(tree):
                if isinstance(node, (ast.Import, ast.ImportFrom)):
                    names = [alias.name.split(".")[0] for alias in getattr(node, "names", [])]
                    if isinstance(node, ast.ImportFrom) and node.module:
                        names.append(node.module.split(".")[0])
                    if any(name in BANNED_IMPORTS for name in names):
                        errors.append("dangerous import")
                if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and node.func.id in BANNED_CALLS:
                    errors.append("dangerous call")

        for value in step.get("arguments", {}).values():
            is_placeholder = isinstance(value, str) and re.fullmatch(r"\$\{(?:SECRET|RUN|LLM)\.[^}]+\}", value.strip(), re.I)
            if isinstance(value, str) and value and not is_placeholder and value in generated["code"]:
                errors.append("argument hardcoded")

        return {**generated, "status": "validated" if not errors else "draft", "validation_errors": errors}


class ExecutionService:
    def execute_plan(self, plan: Dict[str, Any], execute: bool) -> Dict[str, Any]:
        if not execute:
            return {"status": "planned", "steps_executed": 0}

        with ThreadPoolExecutor(max_workers=1) as executor:
            return executor.submit(self._execute_plan_sync, plan).result()

    def _execute_plan_sync(self, plan: Dict[str, Any]) -> Dict[str, Any]:
        steps_executed = 0
        runtime_values: Dict[str, str] = {}
        self._search_result_evidence: List[Dict[str, Any]] = []
        self._search_result_capture_keys: set[str] = set()
        self._current_scenario_name = ""
        self._seed_navigation_context_from_plan(plan)

        try:
            from playwright.sync_api import sync_playwright
        except Exception as exc:
            first = self._first_step(plan)
            return {
                "status": "failed",
                "steps_executed": 0,
                "failed_scenario": first.get("scenario"),
                "failed_step": first.get("step"),
                "step_index": 1,
                "message": "Playwright is not installed in the LangGraph container.",
                "stdout": "",
                "stderr": str(exc),
            }

        with sync_playwright() as playwright:
            headless = os.getenv("E2E_BROWSER_HEADLESS", "true").lower() not in {"false", "0", "no"}
            slow_mo = int(os.getenv("E2E_BROWSER_SLOW_MO_MS", "80"))
            chromium_path = os.getenv("PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH", "").strip()
            launch_options = {"headless": headless, "slow_mo": slow_mo}
            launch_options["args"] = ["--ignore-certificate-errors"]
            if chromium_path:
                launch_options["executable_path"] = chromium_path
            cdp_url = os.getenv("E2E_BROWSER_CDP_URL", "").strip()
            external_browser = bool(cdp_url)
            if external_browser:
                try:
                    self._launch_external_browser_if_needed()
                    browser = playwright.chromium.connect_over_cdp(
                        self._numeric_cdp_url(cdp_url), slow_mo=slow_mo
                    )
                    context = browser.contexts[0] if browser.contexts else browser.new_context(
                        viewport={"width": 1366, "height": 768},
                        ignore_https_errors=True,
                    )
                    page = context.new_page()
                    try:
                        cdp_session = context.new_cdp_session(page)
                        cdp_session.send("Security.setIgnoreCertificateErrors", {"ignore": True})
                        cdp_session.send("Network.clearBrowserCache")
                    except Exception:
                        pass
                    page.bring_to_front()
                except Exception as exc:
                    if os.getenv("E2E_BROWSER_CDP_REQUIRED", "false").lower() in {"true", "1", "yes"}:
                        first = self._first_step(plan)
                        return {
                            "status": "failed",
                            "steps_executed": 0,
                            "failed_scenario": first.get("scenario"),
                            "failed_step": first.get("step"),
                            "step_index": 1,
                            "message": (
                                f"External Chrome is not reachable at {cdp_url}. "
                                "Make sure the hidden VPLMQA E2E Chrome runner is installed and reachable at "
                                "http://host.docker.internal:9223/health, or unset E2E_BROWSER_CDP_REQUIRED."
                            ),
                            "stdout": "",
                            "stderr": repr(exc),
                        }
                    external_browser = False
                    browser = playwright.chromium.launch(**launch_options)
                    context = browser.new_context(
                        viewport={"width": 1366, "height": 768},
                        ignore_https_errors=True,
                    )
                    page = context.new_page()
            else:
                browser = playwright.chromium.launch(**launch_options)
                context = browser.new_context(
                    viewport={"width": 1366, "height": 768},
                    ignore_https_errors=True,
                )
                page = context.new_page()
            try:
                healing = plan.get("self_healing", {}) or {}
                healing_enabled = bool(healing.get("enabled", True))
                max_healing_attempts = int(healing.get("max_attempts", 1) or 0)
                for scenario in plan.get("scenarios", []):
                    self._current_scenario_name = str(scenario.get("name") or "")
                    for index, step in enumerate(scenario.get("steps", []), start=1):
                        steps_executed += 1
                        attempts = 0
                        while True:
                            try:
                                self._execute_step(
                                    page,
                                    step,
                                    plan.get("component_catalog", []),
                                    plan.get("project_context", {}),
                                    runtime_values,
                                )
                                page = self._latest_open_page(context, page)
                                self._human_pause(page, plan.get("project_context", {}))
                                break
                            except Exception as step_exc:
                                if self._skip_self_healing_for_step(step) or not healing_enabled or attempts >= max_healing_attempts:
                                    raise
                                attempts += 1
                                if not self._self_heal_step(page, step, plan.get("project_context", {}), step_exc):
                                    raise
                self._finish_browser(browser, external_browser)
                return {
                    "status": "passed",
                    "steps_executed": steps_executed,
                    "runtime_values": runtime_values,
                    "search_result_evidence": list(getattr(self, "_search_result_evidence", [])),
                }
            except Exception as exc:
                screenshot_path = self._try_capture_screenshot(page, step)
                self._finish_browser(browser, external_browser)
                return {
                    "status": "failed",
                    "steps_executed": steps_executed,
                    "failed_scenario": scenario,
                    "failed_step": step,
                    "step_index": index,
                    "message": str(exc),
                    "stdout": "",
                    "stderr": repr(exc),
                    "local_screenshot_path": screenshot_path,
                    "runtime_values": runtime_values,
                    "search_result_evidence": list(getattr(self, "_search_result_evidence", [])),
                }

    def _launch_external_browser_if_needed(self) -> None:
        launcher_url = os.getenv("E2E_BROWSER_LAUNCHER_URL", "").strip()
        if not launcher_url:
            return

        request = urllib.request.Request(
            launcher_url,
            data=b"{}",
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=8) as response:
                response.read()
        except Exception as exc:
            if os.getenv("E2E_BROWSER_CDP_REQUIRED", "false").lower() in {"true", "1", "yes"}:
                raise RuntimeError(f"Could not launch external Chrome through {launcher_url}: {exc}") from exc

        time.sleep(float(os.getenv("E2E_BROWSER_LAUNCH_WAIT_SECONDS", "2")))

    def _skip_self_healing_for_step(self, step: Dict[str, Any]) -> bool:
        text = str(step.get("normalized_text") or step.get("raw_text") or "").lower()
        raw_text = str(step.get("raw_text") or step.get("normalized_text") or "")
        fill_label = self._field_label_from_fill_step(raw_text)
        return bool(
            "advanced search condition" in text
            or self._is_advanced_condition_row_check_step(text)
            or self._is_advanced_condition_group_step(text)
            or self._is_advanced_search_ordinal_field_label(fill_label)
            or self._is_advanced_search_ordinal_select_step(raw_text)
        )

    def _numeric_cdp_url(self, cdp_url: str) -> str:
        parsed = urllib.parse.urlsplit(cdp_url)
        if not parsed.hostname:
            return cdp_url

        address = socket.gethostbyname(parsed.hostname)
        port = f":{parsed.port}" if parsed.port else ""
        return urllib.parse.urlunsplit(
            (parsed.scheme, f"{address}{port}", parsed.path, parsed.query, parsed.fragment)
        )

    def _finish_browser(self, browser: Any, external_browser: bool) -> None:
        self._hold_browser_open()
        try:
            browser.close()
        except Exception:
            pass
        if external_browser:
            self._close_external_browser_if_needed()

    def _latest_open_page(self, context: Any, current_page: Any) -> Any:
        """Keep execution on the page/tab the tester would naturally see."""
        try:
            pages = [candidate for candidate in context.pages if not candidate.is_closed()]
        except Exception:
            return current_page
        if not pages:
            return current_page
        latest = pages[-1]
        if latest == current_page:
            return current_page
        try:
            latest.wait_for_load_state("domcontentloaded", timeout=8000)
        except Exception:
            pass
        try:
            latest.bring_to_front()
        except Exception:
            pass
        return latest

    def _close_external_browser_if_needed(self) -> None:
        close_url = os.getenv("E2E_BROWSER_CLOSE_URL", "").strip()
        if not close_url:
            return

        request = urllib.request.Request(
            close_url,
            data=b"{}",
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                response.read()
        except Exception:
            pass

    def _seed_navigation_context_from_plan(self, plan: Dict[str, Any]) -> None:
        context = plan.setdefault("project_context", {})
        project = context.setdefault("project", {})
        if str(project.get("baseUrl") or project.get("url") or "").strip():
            return
        for scenario in plan.get("scenarios", []) or []:
            for step in scenario.get("steps", []) or []:
                candidates = [str(step.get("raw_text") or ""), str(step.get("normalized_text") or "")]
                args = step.get("arguments", {}) or {}
                candidates.extend(str(value or "") for value in args.values())
                for candidate in candidates:
                    match = re.search(r"https?://[^\s\"')>\]}]+", candidate)
                    if match:
                        project["baseUrl"] = match.group(0).rstrip(".,;")
                        return

    def _try_capture_screenshot(self, page: Any, step: Dict[str, Any]) -> Optional[str]:
        last_error: Optional[Exception] = None
        for full_page in (True, False):
            try:
                return self._capture_screenshot(page, step, full_page=full_page)
            except Exception as exc:
                last_error = exc

        try:
            return self._capture_cdp_screenshot(page, step)
        except Exception as exc:
            last_error = exc

        if last_error is not None:
            print(f"Failed to capture browser screenshot: {last_error}", flush=True)
            return self._write_screenshot_failure_card(step, str(last_error))
        return None

    def _human_pause(self, page: Any, project_context: Optional[Dict[str, Any]] = None) -> None:
        if os.getenv("E2E_HUMAN_PACING", "true").lower() in {"false", "0", "no"}:
            return
        settings = (project_context or {}).get("settings")
        configured_delay = None
        if isinstance(settings, dict):
            configured_delay = settings.get("actionDelayMs") or settings.get("action_delay_ms")
        try:
            base_ms = int(configured_delay if configured_delay is not None else os.getenv("E2E_HUMAN_ACTION_DELAY_MS", "320"))
        except (TypeError, ValueError):
            base_ms = 320
        base_ms = max(0, min(3000, base_ms))
        jitter_ms = int(os.getenv("E2E_HUMAN_ACTION_JITTER_MS", str(min(180, max(0, base_ms // 2)))))
        delay = max(0, base_ms + random.randint(-jitter_ms, jitter_ms))
        if delay:
            try:
                page.wait_for_timeout(delay)
            except Exception:
                pass

    def _execute_step(
        self,
        page: Any,
        step: Dict[str, Any],
        component_catalog: List[Dict[str, Any]],
        project_context: Dict[str, Any],
        runtime_values: Dict[str, str],
    ) -> None:
        text = step["normalized_text"].lower()
        args = self._resolve_step_arguments(step.get("arguments", {}), project_context, runtime_values)
        value = self._first_arg(args, project_context) if args else ""
        runtime_phrase_value = self._resolve_runtime_phrase(
            value or step.get("raw_text") or step.get("normalized_text") or "",
            runtime_values,
        )
        if runtime_phrase_value:
            value = runtime_phrase_value
        component = self._resolve_component(text, component_catalog, project_context)
        click_like = any(term in text for term in ("click", "press", "submit", "choose"))

        advanced_condition = self._advanced_search_condition_args(step, project_context, runtime_values)
        if advanced_condition is not None:
            self._set_advanced_search_condition(page, **advanced_condition)
            return

        advanced_calendar = self._advanced_calendar_args(step, runtime_values)
        if advanced_calendar is not None:
            self._select_date_from_calendar_field(
                page,
                advanced_calendar["field_label"],
                advanced_calendar["value"],
                advanced_calendar["row_index"],
            )
            return

        pointer_selector = self._advanced_pointer_selector_args(step, runtime_values)
        if pointer_selector is not None:
            action = pointer_selector["action"]
            if action == "open":
                self._open_pointer_selector(
                    page,
                    pointer_selector["field_label"],
                    pointer_selector["row_index"],
                )
                return
            if action == "search":
                self._search_pointer_selector(page, pointer_selector["value"])
                return
            if action == "select":
                self._select_pointer_selector_row(page, pointer_selector["value"])
                return
            if action == "validate":
                self._validate_pointer_selector(page)
                return

        advanced_value_selector = self._advanced_value_selector_args(step, runtime_values)
        if advanced_value_selector is not None:
            action = advanced_value_selector["action"]
            if action == "open":
                self._open_advanced_value_selector(
                    page,
                    advanced_value_selector["field_label"],
                    advanced_value_selector["row_index"],
                )
                return
            if action == "filter":
                self._filter_value_selector(page, advanced_value_selector["value"])
                return
            if action == "select":
                self._select_value_selector_option(page, advanced_value_selector["value"])
                return
            if action == "validate":
                self._validate_value_selector(page)
                return

        if self._is_advanced_condition_row_check_step(text):
            row_index = self._condition_row_ordinal_from_text(text)
            self._check_advanced_condition_row(page, row_index)
            return

        if self._is_advanced_condition_group_step(text):
            self._group_selected_advanced_conditions(page)
            return

        if self._is_enter_key_step(text):
            page.keyboard.press("Enter")
            self._wait_after_search_submit(page)
            return

        if "navigate" in text or "go to" in text or "open" in text:
            target = self._resolve_navigation_target(page, text, value, project_context)
            page.goto(target, wait_until="domcontentloaded", timeout=30000)
            self._recover_project_module_navigation(page, text, value)
            self._assert_project_navigation_state(page, text, value)
            return

        if any(term in text for term in ("enter ", "fill ", "type ")) and self._is_search_field_intent(text):
            if not value:
                value = self._llm_input_value_for_step(page, text, runtime_values)
            self._fill_search_field(page, value)
            return

        if self._is_direct_search_action(text) and not click_like:
            if component:
                locator = self._component_locator(page, component)
                if self._locator_is_fillable(locator):
                    locator.fill(value)
                    locator.press("Enter")
                    page.wait_for_load_state("domcontentloaded", timeout=30000)
                else:
                    self._search(page, value)
            else:
                self._search(page, value)
            return

        if any(term in text for term in ("enter ", "fill ", "type ")):
            if not value:
                value = self._llm_input_value_for_step(page, text, runtime_values)
            generic_label = self._field_label_from_fill_step(text)
            if generic_label:
                _, ordinal_index = self._field_label_and_ordinal(generic_label)
                # Repeated forms such as VPLM advanced-search conditions use the same
                # visible labels in several rows. If the tester says "second Reference"
                # or "third Version", respect that row before falling back to generic
                # Reference/Designation helpers that intentionally target the first form.
                if ordinal_index > 0 and self._fill_text_field_by_normalized_label(page, generic_label, value):
                    self._remember_runtime_input(text, value, runtime_values)
                    return
                if self._is_advanced_search_ordinal_field_label(generic_label):
                    raise AssertionError(f"Could not fill the exact advanced-search field '{generic_label}'")
            if self._is_form_field_intent(text):
                try:
                    self._fill_by_intent(page, text, value)
                    self._remember_runtime_input(text, value, runtime_values)
                    return
                except Exception:
                    pass
            if generic_label and self._fill_text_field_by_normalized_label(page, generic_label, value):
                self._remember_runtime_input(text, value, runtime_values)
                return
            live_locator = self._live_dom_locator_by_intent(page, text, expected_kind="input")
            if live_locator is not None:
                if self._locator_is_fillable(live_locator):
                    live_locator.fill(value)
                    self._remember_runtime_input(text, value, runtime_values)
                    return
            if component:
                try:
                    component_locator = self._component_locator(page, component)
                    if self._locator_is_fillable(component_locator):
                        component_locator.fill(value)
                        self._remember_runtime_input(text, value, runtime_values)
                        return
                except Exception:
                    pass
            self._fill_by_intent(page, text, value)
            self._remember_runtime_input(text, value, runtime_values)
            return

        if "select" in text and " from " in text:
            option_label, field_label = self._selection_args(step, project_context, runtime_values)
            clean_field_label, ordinal_index = self._field_label_and_ordinal(field_label)
            if self._is_condition_operator_field(clean_field_label, option_label):
                if self._select_condition_operator(page, option_label, ordinal_index, grouped="group" in field_label.lower()):
                    return
                if self._is_advanced_search_ordinal_select_field(field_label):
                    raise AssertionError(f"Could not set the exact condition operator field '{field_label}'")
            if self._is_advanced_search_ordinal_select_field(field_label):
                for candidate_label in self._field_label_aliases(clean_field_label):
                    if self._select_custom_field_by_normalized_label(page, candidate_label, option_label, ordinal_index):
                        self._remember_object_context_from_value(text, option_label, runtime_values)
                        page.wait_for_timeout(500)
                        return
                raise AssertionError(f"Could not select '{option_label}' from the exact advanced-search field '{field_label}'")
            self._select_by_label_or_text(page, field_label, option_label)
            self._remember_object_context_from_value(text, option_label, runtime_values)
            page.wait_for_timeout(500)
            return

        if click_like and self._click_priority_intent(page, text):
            return

        if click_like and value:
            if self._is_logout_intent(value):
                self._click_logout(page)
                return
            if self._is_save_intent(value):
                self._click_save_and_assert(page)
                return
            self._click_text_target(page, value)
            self._remember_object_context_from_value(text, value, runtime_values)
            page.wait_for_timeout(1000)
            return

        if click_like:
            live_locator = self._live_dom_locator_by_intent(page, f"{text} {value}".strip(), expected_kind="button")
            if live_locator is not None:
                self._safe_click(page, live_locator)
                if any(term in text for term in ("sign in", "log in", "login", "submit")):
                    self._wait_after_submit(page)
                return

        if click_like and component:
            try:
                component_locator = self._component_locator(page, component)
                if self._locator_is_clickable(component_locator):
                    self._safe_click(page, component_locator)
                else:
                    raise AssertionError("Resolved component is not clickable")
                if any(term in text for term in ("sign in", "log in", "login", "submit")):
                    self._wait_after_submit(page)
                return
            except Exception:
                pass

        if click_like:
            self._click_by_intent(page, text)
            return

        if any(term in text for term in ("wait ", "attend", "waiting")):
            if any(term in text for term in (
                "save", "saving", "enregistrement", "enregistrer", "complete", "termin",
                "created", "créé", "cree", "object", "objet", "creation", "création",
            )):
                self._wait_for_save_completion(page)
                return
            if any(term in text for term in ("load", "loading", "chargement", "result", "résultat", "resultat")):
                self._wait_for_ui_idle(page)
                return
            page.wait_for_timeout(int(os.getenv("E2E_GENERIC_WAIT_MS", "1500")))
            return

        if "title should contain" in text or "page title should contain" in text:
            page.wait_for_load_state("domcontentloaded", timeout=30000)
            title = page.title()
            if value.lower() not in title.lower():
                raise AssertionError(f"Expected page title to contain '{value}', got '{title}'")
            return

        if "heading" in text:
            page.wait_for_load_state("domcontentloaded", timeout=30000)
            heading = page.locator("h1").first
            heading.wait_for(state="visible", timeout=30000)
            actual = heading.inner_text()
            if value.lower() not in actual.lower():
                raise AssertionError(f"Expected heading to contain '{value}', got '{actual}'")
            return

        if self._is_page_state_assertion(text):
            requested_page = self._requested_asserted_page_name(step.get("raw_text") or step.get("normalized_text") or "")
            self._assert_current_page(page, requested_page)
            return

        field_value_assertion = self._field_value_assertion_args(step, runtime_values)
        if field_value_assertion is not None:
            if not self._date_field_has_value(
                page,
                field_value_assertion["field_label"],
                field_value_assertion["value"],
                field_value_assertion["row_index"],
            ):
                raise AssertionError(
                    f"Expected field '{field_value_assertion['field_label']}' to contain "
                    f"'{field_value_assertion['value']}'"
                )
            return

        if "see" in text or "contain" in text:
            page.wait_for_load_state("domcontentloaded", timeout=30000)
            if "page" in text:
                requested_page = value or self._requested_asserted_page_name(step.get("raw_text") or step.get("normalized_text") or "")
                if requested_page:
                    self._assert_current_page(page, requested_page)
                    return
            if self._is_search_result_assertion(text):
                self._assert_search_result_available(page, value)
                return
            self._assert_text_available(page, value)
            return

        if "visible" in text:
            page.wait_for_load_state("domcontentloaded", timeout=30000)
            return

        raise NotImplementedError(f"No reusable Playwright executor for step: {step['normalized_text']}")

    def _selection_args(
        self,
        step: Dict[str, Any],
        project_context: Dict[str, Any],
        runtime_values: Optional[Dict[str, str]] = None,
    ) -> tuple[str, str]:
        args = self._resolve_step_arguments(step.get("arguments", {}), project_context, runtime_values or {})
        values = [str(value) for value in args.values()]
        if len(values) >= 2:
            return values[0], values[1]

        raw_text = str(step.get("raw_text") or step.get("normalized_text") or "")
        match = re.search(
            r"select\s+[\"']([^\"']+)[\"']\s+from\s+(?:the\s+)?[\"']([^\"']+)[\"']",
            raw_text,
            re.I,
        )
        if match:
            return match.group(1), match.group(2)
        raise ValueError(f"Selection step requires an option and a field name: {raw_text}")

    def _select_by_label_or_text(self, page: Any, field_label: str, option_label: str) -> None:
        clean_field_label, ordinal_index = self._field_label_and_ordinal(field_label)
        field_labels = self._field_label_aliases(clean_field_label)

        if ordinal_index > 0 and self._select_custom_field_by_normalized_label(page, clean_field_label, option_label, ordinal_index):
            return

        for label_text in field_labels:
            label_pattern = re.compile(rf"^\s*{re.escape(label_text)}\s*\*?\s*$", re.I)

            native_candidates = [
                page.get_by_label(label_pattern),
                page.locator(f"select[aria-label*='{label_text}' i]"),
                page.locator(f"select[name*='{label_text}' i]"),
                page.locator(f"select[id*='{label_text}' i]"),
            ]
            for locator in native_candidates:
                try:
                    if locator.count() > 0:
                        candidate = locator.nth(min(ordinal_index, max(0, locator.count() - 1)))
                        tag = str(candidate.evaluate("element => element.tagName.toLowerCase()"))
                        if tag == "select":
                            candidate.select_option(label=option_label, timeout=5000)
                            return
                        self._safe_click(page, candidate, timeout=5000)
                        self._click_dropdown_option(page, option_label)
                        return
                except Exception:
                    pass

            if self._select_custom_field_by_normalized_label(page, clean_field_label, option_label, ordinal_index):
                return

            label_matches = page.get_by_text(label_pattern)
            label = label_matches.nth(min(ordinal_index, max(0, label_matches.count() - 1))) if label_matches.count() > 0 else label_matches.first
            if label.count() > 0:
                try:
                    field = label.locator(
                        "xpath=following::select[1] | following::*[@role='combobox'][1] | following::input[1]"
                    ).first
                    if field.count() > 0:
                        tag = str(field.evaluate("element => element.tagName.toLowerCase()"))
                        if tag == "select":
                            field.select_option(label=option_label, timeout=5000)
                        else:
                            self._safe_click(page, field, timeout=5000)
                            self._click_dropdown_option(page, option_label)
                        return
                except Exception:
                    pass

            comboboxes = [
                page.get_by_role("combobox", name=label_pattern),
                page.locator(f"[role='combobox'][aria-label*='{label_text}' i]"),
                page.locator(f"input[placeholder*='{label_text}' i]"),
            ]
            for combobox in comboboxes:
                try:
                    if combobox.count() > 0:
                        index = min(ordinal_index, max(0, combobox.count() - 1))
                        self._safe_click(page, combobox.nth(index), timeout=5000)
                        self._click_dropdown_option(page, option_label)
                        return
                except Exception:
                    pass

        # Some legacy PLM screens render the field label separately from a custom dropdown.
        # In that case, clicking the visible option directly is still safer than passing silently.
        try:
            self._click_dropdown_option(page, option_label)
            return
        except Exception as exc:
            raise AssertionError(
                f"Could not select '{option_label}' from field '{field_label}'"
            ) from exc

    def _field_label_aliases(self, field_label: str) -> List[str]:
        label = field_label.strip()
        aliases = [label]
        lower = self._normalize_text_key(label)
        if lower in {"poste", "workstation"}:
            aliases.extend(["Workstation", "Poste"])
        if lower in {"base", "database"}:
            aliases.extend(["Database", "Base"])
        if lower in {"classesdobjets", "classedobjet", "classe", "class", "objectclass"}:
            aliases.extend(["Classes d'objets", "Classe", "Class"])
        if lower in {"ajoutduncritere", "critere", "criterion"}:
            aliases.extend(["Ajout d'un critère", "Critere", "Critère"])
        if lower in {"createur", "creator"}:
            aliases.extend(["Créateur", "Createur", "Creator"])
        if lower in {"operateur", "operator"}:
            aliases.extend(["Opérateur", "Operateur", "Operator"])
        if lower in {"reference", "ref"}:
            aliases.extend(["Référence", "Reference"])
        if lower in {"categorie", "category"}:
            aliases.extend(["Catégorie", "Categorie", "Category"])
        if lower in {"attribut", "attribute"}:
            aliases.extend(["Attribut", "Attribute"])
        return list(dict.fromkeys(aliases))

    def _field_label_and_ordinal(self, field_label: str) -> tuple[str, int]:
        label = re.sub(r"\s+", " ", str(field_label or "").strip())
        ordinal = 0
        patterns = {
            0: r"^(?:first|1st|premier|premiere|première)\s+",
            1: r"^(?:second|2nd|deuxieme|deuxième)\s+",
            2: r"^(?:third|3rd|troisieme|troisième)\s+",
            3: r"^(?:fourth|4th|quatrieme|quatrième)\s+",
            4: r"^(?:fifth|5th|cinquieme|cinquième)\s+",
            5: r"^(?:sixth|6th|sixieme|sixième)\s+",
            6: r"^(?:seventh|7th|septieme|septième)\s+",
            7: r"^(?:eighth|8th|huitieme|huitième)\s+",
            8: r"^(?:ninth|9th|neuvieme|neuvième)\s+",
            9: r"^(?:tenth|10th|dixieme|dixième)\s+",
        }
        for index, pattern in patterns.items():
            if re.search(pattern, label, re.I):
                ordinal = index
                label = re.sub(pattern, "", label, flags=re.I).strip()
                break
        return label, ordinal

    def _has_ordinal_prefix(self, value: str) -> bool:
        return bool(
            re.search(
                r"^\s*(?:first|1st|premier|premiere|première|second|2nd|deuxieme|deuxième|third|3rd|troisieme|troisième|fourth|4th|quatrieme|quatrième|fifth|5th|cinquieme|cinquième|sixth|6th|sixieme|sixième|seventh|7th|septieme|septième|eighth|8th|huitieme|huitième|ninth|9th|neuvieme|neuvième|tenth|10th|dixieme|dixième)\b",
                str(value or ""),
                re.I,
            )
        )

    def _is_advanced_search_ordinal_field_label(self, field_label: str) -> bool:
        if not field_label or not self._has_ordinal_prefix(field_label):
            return False
        clean_label, _ = self._field_label_and_ordinal(field_label)
        return self._normalize_text_key(clean_label) in {
            "reference",
            "ref",
            "designation",
            "createur",
            "creator",
            "version",
        }

    def _is_advanced_search_ordinal_select_field(self, field_label: str) -> bool:
        if not field_label:
            return False
        clean_label, _ = self._field_label_and_ordinal(field_label)
        label_key = self._normalize_text_key(clean_label)
        return self._has_ordinal_prefix(field_label) and label_key in {
            "ajoutduncritere",
            "critere",
            "criterion",
            "attribut",
            "attribute",
            "operateur",
            "operator",
            "condition",
        }

    def _is_advanced_search_ordinal_select_step(self, step_text: str) -> bool:
        match = re.search(
            r"\bselect\s+[\"'][^\"']+[\"']\s+from\s+(?:the\s+)?[\"']([^\"']+)[\"']",
            str(step_text or ""),
            re.I,
        )
        return bool(match and self._is_advanced_search_ordinal_select_field(match.group(1)))

    def _condition_row_ordinal_from_text(self, step_text: str) -> int:
        ordinal_patterns = [
            (0, r"\b(?:first|1st|premier|premiere|première)\b"),
            (1, r"\b(?:second|2nd|deuxieme|deuxième)\b"),
            (2, r"\b(?:third|3rd|troisieme|troisième)\b"),
            (3, r"\b(?:fourth|4th|quatrieme|quatrième)\b"),
            (4, r"\b(?:fifth|5th|cinquieme|cinquième)\b"),
            (5, r"\b(?:sixth|6th|sixieme|sixième)\b"),
            (6, r"\b(?:seventh|7th|septieme|septième)\b"),
            (7, r"\b(?:eighth|8th|huitieme|huitième)\b"),
            (8, r"\b(?:ninth|9th|neuvieme|neuvième)\b"),
            (9, r"\b(?:tenth|10th|dixieme|dixième)\b"),
        ]
        for index, pattern in ordinal_patterns:
            if re.search(pattern, step_text, re.I):
                return index
        match = re.search(r"\bcondition\s+(?:row\s+)?(\d+)\b", step_text, re.I)
        if match:
            return max(0, int(match.group(1)) - 1)
        return 0

    def _is_advanced_condition_row_check_step(self, step_text: str) -> bool:
        return bool(
            re.search(r"\b(?:check|select|tick)\s+(?:the\s+)?(?:first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|\d+(?:st|nd|rd|th)?)\s+condition\s+(?:row|checkbox)\b", step_text, re.I)
            or re.search(r"\b(?:cocher|selectionner|sélectionner)\s+(?:la\s+)?(?:premiere|première|deuxieme|deuxième|troisieme|troisième|quatrieme|quatrième|\d+)\s+(?:ligne\s+de\s+)?condition\b", step_text, re.I)
        )

    def _is_advanced_condition_group_step(self, step_text: str) -> bool:
        return bool(re.search(r"\b(?:group|grouper|regrouper|create\s+(?:a\s+)?group|make\s+(?:a\s+)?group)\b.*\bcondition", step_text, re.I))

    def _is_condition_operator_field(self, field_label: str, option_label: str) -> bool:
        label_key = self._normalize_text_key(field_label)
        option_key = self._normalize_text_key(option_label)
        return label_key in {"condition", "conditionoperator", "operateurcondition", "groupedcondition"} and option_key in {"et", "ou", "and", "or"}

    def _advanced_search_condition_args(
        self,
        step: Dict[str, Any],
        project_context: Dict[str, Any],
        runtime_values: Dict[str, str],
    ) -> Optional[Dict[str, Any]]:
        raw_text = str(step.get("raw_text") or step.get("normalized_text") or "")
        normalized = str(step.get("normalized_text") or raw_text).lower()
        if "advanced search condition" not in normalized:
            return None

        row_match = re.search(
            r"\bcondition\s+(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|\d+)\b",
            raw_text,
            re.I,
        )
        if not row_match:
            return None
        row_token = row_match.group(1)
        if row_token.isdigit():
            row_index = max(0, int(row_token) - 1)
        else:
            row_index = self._condition_row_ordinal_from_text(row_token)

        def extract(label: str) -> str:
            pattern = rf"\b{label}\s+[\"']([^\"']+)[\"']"
            match = re.search(pattern, raw_text, re.I)
            if match:
                return self._resolve_runtime_phrase(match.group(1), runtime_values) or match.group(1)
            return ""

        criterion = extract("criterion")
        attribute = extract("attribute")
        operator = extract("operator")
        value = extract("value")

        args = self._resolve_step_arguments(step.get("arguments", {}), project_context, runtime_values)
        if len(args) >= 4:
            values = [str(item) for item in args.values()]
            criterion = criterion or values[0]
            attribute = attribute or values[1]
            operator = operator or values[2]
            value = value or values[3]

        if not (criterion and attribute and operator and value):
            raise AssertionError(
                "Advanced search condition step must provide criterion, attribute, operator and value"
            )
        return {
            "row_index": row_index,
            "criterion": criterion,
            "attribute": attribute,
            "operator": operator,
            "value": value,
        }

    def _field_value_assertion_args(
        self,
        step: Dict[str, Any],
        runtime_values: Dict[str, str],
    ) -> Optional[Dict[str, Any]]:
        text = str(step.get("raw_text") or step.get("normalized_text") or "")
        patterns = [
            r"\b(?:the\s+)?[\"']([^\"']+)[\"']\s+field\s+should\s+(?:contain|have)\s+[\"']([^\"']+)[\"']",
            r"\b(?:le\s+)?champ\s+[\"']([^\"']+)[\"']\s+doit\s+(?:contenir|avoir)\s+[\"']([^\"']+)[\"']",
        ]
        for pattern in patterns:
            match = re.search(pattern, text, re.I)
            if not match:
                continue
            field_label, row_index = self._field_label_and_ordinal(match.group(1))
            value = self._resolve_runtime_phrase(match.group(2), runtime_values) or match.group(2)
            return {"field_label": field_label, "row_index": row_index, "value": value}
        return None

    def _set_advanced_search_condition(
        self,
        page: Any,
        row_index: int,
        criterion: str,
        attribute: str,
        operator: str,
        value: str,
    ) -> None:
        steps = [
            ("Ajout d'un critere", criterion, self._select_custom_field_by_normalized_label),
            ("Attribut", attribute, self._select_custom_field_by_normalized_label),
            ("Operateur", operator, self._select_custom_field_by_normalized_label),
        ]
        for label, option, action in steps:
            if not action(page, label, option, row_index):
                raise AssertionError(
                    f"Could not set advanced search condition {row_index + 1}: select '{option}' from '{label}'"
                )
            page.wait_for_timeout(350)

        if not self._fill_text_field_by_normalized_label(page, f"{self._ordinal_prefix(row_index)} {attribute}", value):
            raise AssertionError(
                f"Could not set advanced search condition {row_index + 1}: enter '{value}' in '{attribute}'"
            )
        page.wait_for_timeout(350)

    def _advanced_calendar_args(
        self,
        step: Dict[str, Any],
        runtime_values: Dict[str, str],
    ) -> Optional[Dict[str, Any]]:
        text = str(step.get("raw_text") or step.get("normalized_text") or "")
        patterns = [
            r"\bselect\s+(?:the\s+)?date\s+[\"']([^\"']+)[\"']\s+from\s+(?:the\s+)?calendar\s+in\s+(?:the\s+)?[\"']([^\"']+)[\"']\s+field\b",
            r"\bselect\s+[\"']([^\"']+)[\"']\s+from\s+(?:the\s+)?calendar\s+in\s+(?:the\s+)?[\"']([^\"']+)[\"']\s+field\b",
            r"\bchoisir\s+(?:la\s+)?date\s+[\"']([^\"']+)[\"']\s+dans\s+(?:le\s+)?calendrier\s+du\s+champ\s+[\"']([^\"']+)[\"']\b",
        ]
        for pattern in patterns:
            match = re.search(pattern, text, re.I)
            if not match:
                continue
            value = self._resolve_runtime_phrase(match.group(1), runtime_values) or match.group(1)
            field_label, row_index = self._field_label_and_ordinal(match.group(2))
            return {"field_label": field_label, "row_index": row_index, "value": value}
        return None

    def _select_date_from_calendar_field(self, page: Any, field_label: str, value: str, row_index: int = 0) -> None:
        self._dismiss_blocking_plm_dialog(page)
        parsed_date = self._parse_date_value(value)
        target = self._advanced_date_field_target(page, field_label, row_index)
        if not target:
            raise AssertionError(f"Could not find the calendar field '{field_label}'")

        input_selector = str(target.get("inputSelector") or "")
        if input_selector:
            self._clear_date_field_if_needed(page, input_selector, value)

        icon = target.get("icon")
        if icon:
            page.mouse.click(float(icon["x"]), float(icon["y"]))
            self._calendar_pause(page)
            if parsed_date and self._select_material_calendar_date(page, **parsed_date):
                self._calendar_pause(page)
                if self._date_field_has_value(page, field_label, value, row_index):
                    return

        if parsed_date and os.getenv("E2E_CALENDAR_ALLOW_DIRECT_FILL", "false").lower() not in {"true", "1", "yes"}:
            current = self._date_field_current_value(page, field_label, row_index)
            raise AssertionError(
                f"Could not select date '{value}' visibly from the calendar field '{field_label}'. "
                f"Current value is '{current}'."
            )

        if input_selector:
            locator = page.locator(input_selector).first
            try:
                locator.click(timeout=5000)
                locator.press("Control+A", timeout=1000)
                locator.type(value, delay=55, timeout=10000)
                locator.evaluate(
                    """element => {
                      element.dispatchEvent(new Event('input', { bubbles: true }));
                      element.dispatchEvent(new Event('change', { bubbles: true }));
                      element.dispatchEvent(new Event('blur', { bubbles: true }));
                    }"""
                )
                try:
                    locator.press("Enter", timeout=1000)
                except Exception:
                    pass
                try:
                    locator.press("Tab", timeout=1000)
                except Exception:
                    pass
                page.wait_for_timeout(500)
                self._close_open_calendar_overlay(page)
                if self._date_field_has_value(page, field_label, value, row_index):
                    return
            except Exception:
                pass

        if self._fill_text_field_by_normalized_label(page, f"{self._ordinal_prefix(row_index)} {field_label}", value):
            self._close_open_calendar_overlay(page)
            if self._date_field_has_value(page, field_label, value, row_index):
                return

        current = self._date_field_current_value(page, field_label, row_index)
        raise AssertionError(f"Could not select date '{value}' in calendar field '{field_label}'. Current value is '{current}'.")

    def _clear_date_field_if_needed(self, page: Any, input_selector: str, expected_value: str) -> None:
        try:
            locator = page.locator(input_selector).first
            current = str(locator.input_value(timeout=2000) or "").strip()
            if not current or self._date_values_match(current, expected_value):
                return
            locator.click(timeout=3000)
            locator.press("Control+A", timeout=1000)
            locator.press("Backspace", timeout=1000)
            locator.evaluate(
                """element => {
                  element.dispatchEvent(new Event('input', { bubbles: true }));
                  element.dispatchEvent(new Event('change', { bubbles: true }));
                }"""
            )
            page.wait_for_timeout(150)
        except Exception:
            pass

    def _calendar_pause(self, page: Any) -> None:
        delay_ms = int(os.getenv("E2E_CALENDAR_ACTION_DELAY_MS", "700"))
        if delay_ms > 0:
            page.wait_for_timeout(delay_ms)

    def _parse_date_value(self, value: str) -> Optional[Dict[str, int]]:
        text = str(value or "").strip()
        match = re.match(r"^\s*(\d{1,2})[/-](\d{1,2})[/-](\d{4})\s*$", text)
        if match:
            return {"day": int(match.group(1)), "month": int(match.group(2)), "year": int(match.group(3))}
        match = re.match(r"^\s*(\d{4})[/-](\d{1,2})[/-](\d{1,2})\s*$", text)
        if match:
            return {"day": int(match.group(3)), "month": int(match.group(2)), "year": int(match.group(1))}
        return None

    def _date_values_match(self, actual: str, expected: str) -> bool:
        actual_date = self._parse_date_value(actual)
        expected_date = self._parse_date_value(expected)
        if actual_date and expected_date:
            return actual_date == expected_date
        return self._normalize_text_key(actual) == self._normalize_text_key(expected)

    def _select_material_calendar_date(self, page: Any, day: int, month: int, year: int) -> bool:
        try:
            page.locator(".mat-datepicker-content,.mat-calendar,.cdk-overlay-pane").first.wait_for(
                state="visible",
                timeout=5000,
            )
        except Exception:
            return False

        if not self._click_calendar_period_button(page):
            return False
        self._calendar_pause(page)

        if not self._click_calendar_year(page, year):
            return False
        self._calendar_pause(page)

        if not self._click_calendar_month(page, month):
            return False
        self._calendar_pause(page)

        if not self._click_calendar_day(page, day):
            return False
        self._calendar_pause(page)
        return True

    def _click_calendar_period_button(self, page: Any) -> bool:
        candidates = [
            page.locator(".mat-calendar-period-button").first,
            page.locator("button.mat-calendar-period-button").first,
            page.locator(".mat-datepicker-content button").first,
        ]
        for locator in candidates:
            try:
                if locator.count() > 0 and self._locator_is_visible(locator):
                    self._click_locator_center(page, locator)
                    return True
            except Exception:
                pass
        return False

    def _click_calendar_year(self, page: Any, year: int) -> bool:
        for _ in range(16):
            if self._click_calendar_cell(page, str(year), exact=True):
                return True
            direction = self._calendar_navigation_direction(page, str(year))
            nav_selector = ".mat-calendar-previous-button" if direction <= 0 else ".mat-calendar-next-button"
            if not self._click_calendar_nav(page, nav_selector):
                return False
            self._calendar_pause(page)
        return False

    def _click_calendar_month(self, page: Any, month: int) -> bool:
        month_labels = {
            1: ["JANV.", "JANVIER", "JAN", "JANUARY"],
            2: ["FÉVR.", "FEVR.", "FÉVRIER", "FEVRIER", "FEB", "FEBRUARY"],
            3: ["MARS", "MAR", "MARCH"],
            4: ["AVR.", "AVRIL", "APR", "APRIL"],
            5: ["MAI", "MAY"],
            6: ["JUIN", "JUN", "JUNE"],
            7: ["JUIL.", "JUILLET", "JUL", "JULY"],
            8: ["AOÛT", "AOUT", "AUG", "AUGUST"],
            9: ["SEPT.", "SEPTEMBRE", "SEP", "SEPTEMBER"],
            10: ["OCT.", "OCTOBRE", "OCT", "OCTOBER"],
            11: ["NOV.", "NOVEMBRE", "NOV", "NOVEMBER"],
            12: ["DÉC.", "DEC.", "DÉCEMBRE", "DECEMBRE", "DEC", "DECEMBER"],
        }
        for label in month_labels.get(month, []):
            if self._click_calendar_cell(page, label, exact=False):
                return True
        return False

    def _click_calendar_day(self, page: Any, day: int) -> bool:
        return self._click_calendar_cell(page, str(day), exact=True, prefer_current_month=True)

    def _click_calendar_nav(self, page: Any, selector: str) -> bool:
        try:
            locator = page.locator(selector).first
            if locator.count() > 0 and self._locator_is_visible(locator):
                self._click_locator_center(page, locator)
                return True
        except Exception:
            pass
        return False

    def _calendar_navigation_direction(self, page: Any, target_label: str) -> int:
        script = """
        (targetLabel) => {
          const nums = Array.from(document.querySelectorAll(".mat-calendar-body-cell,.mat-calendar-body-cell-content,[role='gridcell'],td,button"))
            .map((element) => Number((element.innerText || element.textContent || "").trim()))
            .filter((value) => Number.isFinite(value) && value > 999);
          const target = Number(targetLabel);
          if (!nums.length || !Number.isFinite(target)) return -1;
          const min = Math.min(...nums);
          const max = Math.max(...nums);
          if (target < min) return -1;
          if (target > max) return 1;
          return -1;
        }
        """
        try:
            return int(page.evaluate(script, str(target_label)) or -1)
        except Exception:
            return -1

    def _click_calendar_cell(
        self,
        page: Any,
        label: str,
        exact: bool = True,
        prefer_current_month: bool = False,
    ) -> bool:
        script = """
        ({ label, exact, preferCurrentMonth }) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/[^a-z0-9]+/gi, "")
            .toLowerCase();
          const wanted = normalize(label);
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const overlay = Array.from(document.querySelectorAll(".mat-datepicker-content,.mat-calendar,.cdk-overlay-pane"))
            .filter(isVisible)
            .pop() || document.body;
          const cells = Array.from(overlay.querySelectorAll(
            ".mat-calendar-body-cell:not(.mat-calendar-body-disabled),"
            + "[role='gridcell'],td,button,.mat-calendar-body-cell-content"
          )).filter(isVisible).map((element) => {
            const clickable = element.closest("button,.mat-calendar-body-cell,[role='gridcell'],td") || element;
            const text = (element.innerText || element.textContent || clickable.innerText || clickable.textContent || "").trim();
            const normalized = normalize(text);
            const currentMonthPenalty = preferCurrentMonth && String(clickable.className || "").includes("mat-calendar-body-disabled") ? 1000 : 0;
            let score = 0;
            if (exact ? normalized === wanted : normalized.includes(wanted)) score += 100;
            if (String(clickable.className || "").includes("mat-calendar-body-disabled")) score -= 1000;
            const rect = clickable.getBoundingClientRect();
            return {
              x: rect.left + rect.width / 2,
              y: rect.top + rect.height / 2,
              score: score - currentMonthPenalty,
              area: rect.width * rect.height,
              text
            };
          }).filter((item) => item.score > 0)
            .sort((a, b) => (b.score - a.score) || (a.area - b.area));
          return cells[0] || null;
        }
        """
        try:
            target = page.evaluate(script, {"label": label, "exact": exact, "preferCurrentMonth": prefer_current_month})
            if not target:
                return False
            page.mouse.click(float(target["x"]), float(target["y"]))
            return True
        except Exception:
            return False

    def _advanced_date_field_target(self, page: Any, field_label: str, row_index: int = 0) -> Dict[str, Any]:
        script = """
        ({ fieldLabel, rowIndex }) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/[^a-z0-9]+/gi, "")
            .toLowerCase();
          const wanted = normalize(fieldLabel);
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const cssEscape = (value) => {
            if (window.CSS && CSS.escape) return CSS.escape(value);
            return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\\\$&");
          };
          const mark = (element, prefix) => {
            const existing = element.getAttribute("data-vplmqa-runtime-id");
            if (existing) return `[data-vplmqa-runtime-id="${cssEscape(existing)}"]`;
            const assigned = `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2)}`;
            element.setAttribute("data-vplmqa-runtime-id", assigned);
            return `[data-vplmqa-runtime-id="${cssEscape(assigned)}"]`;
          };
          const labelText = (container) => Array.from(container.querySelectorAll(
            "label,mat-label,.mat-mdc-floating-label,.mat-form-field-label,.mdc-floating-label,legend"
          )).map((label) => label.innerText || label.textContent || "").join(" ");
          const containers = Array.from(document.querySelectorAll(
            "mat-form-field,.mat-mdc-form-field,.mat-form-field,.field,.form-group,[class*='field']"
          )).filter(isVisible).map((container) => {
            const rect = container.getBoundingClientRect();
            const label = normalize(labelText(container));
            const identity = normalize([
              labelText(container),
              container.getAttribute("aria-label"),
              container.getAttribute("title"),
              container.innerText || container.textContent || ""
            ].join(" "));
            let score = 0;
            if (label === wanted) score += 120;
            else if (label.includes(wanted)) score += 100;
            else if (identity.includes(wanted)) score += 35;
            const labelKey = normalize(labelText(container));
            if (labelKey.includes("attribut")) score -= 100;
            if (labelKey.includes("operateur")) score -= 100;
            if (labelKey.includes("ajoutduncritere")) score -= 100;
            return { container, score, top: rect.top, left: rect.left, rect };
          }).filter((item) => item.score >= 70);
          containers.sort((a, b) => (a.top - b.top) || (a.left - b.left) || (b.score - a.score));
          const selected = containers[Math.min(Number(rowIndex || 0), Math.max(0, containers.length - 1))];
          if (!selected) return null;
          const container = selected.container;
          const fieldRect = container.getBoundingClientRect();
          const input = Array.from(container.querySelectorAll("input:not([type='hidden']):not([type='checkbox']),textarea"))
            .find((element) => isVisible(element) && !element.disabled && element.getAttribute("aria-disabled") !== "true");
          const iconCandidates = Array.from(container.querySelectorAll(
            "button,[role='button'],.mat-datepicker-toggle,.mat-datepicker-toggle button,mat-datepicker-toggle,mat-icon,.mat-icon,svg,[class*='calendar'],[aria-label],[title]"
          )).filter((element) => {
            if (!isVisible(element)) return false;
            const rect = element.getBoundingClientRect();
            return rect.left >= fieldRect.right - Math.max(90, fieldRect.width * 0.25)
              && rect.top >= fieldRect.top - 6
              && rect.bottom <= fieldRect.bottom + 6;
          }).map((element) => {
            let clickable = element;
            for (let depth = 0; clickable && depth < 5; depth += 1) {
              const tag = clickable.tagName.toLowerCase();
              const role = (clickable.getAttribute("role") || "").toLowerCase();
              if (tag === "button" || role === "button") break;
              clickable = clickable.parentElement;
            }
            clickable = clickable || element;
            const rect = clickable.getBoundingClientRect();
            const text = normalize([
              clickable.getAttribute("aria-label"),
              clickable.getAttribute("title"),
              clickable.innerText || clickable.textContent || "",
              element.getAttribute("aria-label"),
              element.getAttribute("title"),
              element.innerText || element.textContent || "",
              element.className || ""
            ].join(" "));
            let score = rect.right;
            if (text.includes("calendar") || text.includes("datepicker") || text.includes("date")) score += 200;
            return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2, score };
          }).sort((a, b) => b.score - a.score);
          return {
            inputSelector: input ? mark(input, "runtime-date-input") : "",
            icon: iconCandidates[0] || { x: Math.max(fieldRect.left + 4, fieldRect.right - 26), y: fieldRect.top + fieldRect.height / 2 },
          };
        }
        """
        try:
            result = page.evaluate(script, {"fieldLabel": field_label, "rowIndex": row_index})
            return result if isinstance(result, dict) else {}
        except Exception:
            return {}

    def _close_open_calendar_overlay(self, page: Any) -> None:
        try:
            page.keyboard.press("Escape")
            page.wait_for_timeout(150)
        except Exception:
            pass

    def _date_field_has_value(self, page: Any, field_label: str, value: str, row_index: int = 0) -> bool:
        current = self._date_field_current_value(page, field_label, row_index)
        if not current:
            return False
        return self._date_values_match(current, value)

    def _date_field_current_value(self, page: Any, field_label: str, row_index: int = 0) -> str:
        target = self._advanced_date_field_target(page, field_label, row_index)
        selector = str(target.get("inputSelector") or "") if target else ""
        if not selector:
            return ""
        try:
            return str(page.locator(selector).first.input_value(timeout=3000) or "").strip()
        except Exception:
            return ""

    def _advanced_pointer_selector_args(
        self,
        step: Dict[str, Any],
        runtime_values: Dict[str, str],
    ) -> Optional[Dict[str, Any]]:
        raw_text = str(step.get("raw_text") or step.get("normalized_text") or "")
        text = re.sub(r"\s+", " ", raw_text).strip()
        lower = text.lower()

        open_match = re.search(
            r"\bopen\s+(?:the\s+)?(?:pointer|pointeur)\s+selector\s+for\s+(?:the\s+)?[\"']?(.+?)[\"']?\s+field\b",
            text,
            re.I,
        )
        if not open_match:
            open_match = re.search(
                r"\bopen\s+(?:the\s+)?[\"']?(.+?)[\"']?\s+(?:pointer|pointeur)\s+selector\b",
                text,
                re.I,
            )
        if open_match:
            field_label, row_index = self._field_label_and_ordinal(open_match.group(1).strip().strip("\"'"))
            return {"action": "open", "field_label": field_label, "row_index": row_index}

        search_match = re.search(
            r"\b(?:search|filter|write|type|enter)\s+(?:the\s+)?(?:pointer|pointeur)\s+selector\s+with\s+[\"']([^\"']+)[\"']",
            text,
            re.I,
        )
        if search_match:
            value = self._resolve_runtime_phrase(search_match.group(1), runtime_values) or search_match.group(1)
            return {"action": "search", "value": value}

        select_match = re.search(
            r"\bselect\s+[\"']([^\"']+)[\"']\s+from\s+(?:the\s+)?(?:pointer|pointeur)\s+(?:selector\s+)?(?:table|grid|list)?\b",
            text,
            re.I,
        )
        if select_match:
            value = self._resolve_runtime_phrase(select_match.group(1), runtime_values) or select_match.group(1)
            return {"action": "select", "value": value}

        if re.search(r"\b(?:validate|confirm|choose|valider|confirmer)\s+(?:the\s+)?(?:pointer|pointeur)\s+selector\b", lower, re.I):
            return {"action": "validate"}
        return None

    def _open_pointer_selector(self, page: Any, field_label: str, row_index: int = 0) -> None:
        self._dismiss_blocking_plm_dialog(page)
        target = self._advanced_pointer_field_target(page, field_label, row_index)
        icons = target.get("icons") if target else []
        if not icons:
            raise AssertionError(f"Could not find pointer selector icon for field '{field_label}'")
        last_error: Optional[Exception] = None
        for icon in icons[:4]:
            try:
                page.mouse.click(float(icon["x"]), float(icon["y"]))
                self._wait_for_pointer_selector(page, timeout_ms=4500)
                return
            except Exception as exc:
                last_error = exc
                page.wait_for_timeout(400)
        raise AssertionError("The pointer selector table did not open") from last_error

    def _advanced_pointer_field_target(self, page: Any, field_label: str, row_index: int = 0) -> Dict[str, Any]:
        script = """
        ({ fieldLabel, rowIndex }) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/[^a-z0-9]+/gi, "")
            .toLowerCase();
          const wanted = normalize(fieldLabel);
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const labelText = (container) => Array.from(container.querySelectorAll(
            "label,mat-label,.mat-mdc-floating-label,.mat-form-field-label,.mdc-floating-label,legend"
          )).map((label) => label.innerText || label.textContent || "").join(" ");
          const containers = Array.from(document.querySelectorAll(
            "mat-form-field,.mat-mdc-form-field,.mat-form-field,.field,.form-group,[class*='field']"
          )).filter(isVisible).map((container) => {
            const rect = container.getBoundingClientRect();
            const label = normalize(labelText(container));
            const identity = normalize([
              labelText(container),
              container.getAttribute("aria-label"),
              container.getAttribute("title"),
              container.innerText || container.textContent || ""
            ].join(" "));
            let score = 0;
            if (label === wanted) score += 130;
            else if (label.includes(wanted)) score += 110;
            else if (identity.includes(wanted)) score += 40;
            const labelKey = normalize(labelText(container));
            if (labelKey.includes("attribut")) score -= 110;
            if (labelKey.includes("operateur")) score -= 110;
            if (labelKey.includes("ajoutduncritere")) score -= 110;
            return { container, score, top: rect.top, left: rect.left, rect };
          }).filter((item) => item.score >= 80);
          containers.sort((a, b) => (a.top - b.top) || (a.left - b.left) || (b.score - a.score));
          const selected = containers[Math.min(Number(rowIndex || 0), Math.max(0, containers.length - 1))];
          if (!selected) return { icons: [] };
          const container = selected.container;
          const fieldRect = container.getBoundingClientRect();
          const iconSelectors = [
            "button",
            "[role='button']",
            "mat-icon",
            ".mat-icon",
            "svg",
            ".mat-mdc-form-field-icon-suffix",
            ".mat-form-field-suffix",
            "[class*='icon']",
            "[aria-label]",
            "[title]"
          ];
          const nearestClickable = (element) => {
            let current = element;
            for (let depth = 0; current && depth < 6; depth += 1) {
              const tag = current.tagName.toLowerCase();
              const role = (current.getAttribute("role") || "").toLowerCase();
              if (tag === "button" || role === "button") return current;
              current = current.parentElement;
            }
            return element;
          };
          const icons = Array.from(container.querySelectorAll(iconSelectors.join(",")))
            .filter(isVisible)
            .map((element) => {
              const clickable = nearestClickable(element);
              const rect = clickable.getBoundingClientRect();
              const text = normalize([
                clickable.getAttribute("aria-label"),
                clickable.getAttribute("title"),
                clickable.innerText || clickable.textContent || "",
                clickable.className || "",
                element.getAttribute("aria-label"),
                element.getAttribute("title"),
                element.innerText || element.textContent || "",
                element.className || ""
              ].join(" "));
              return { element: clickable, rect, text };
            })
            .filter((item) => {
              const rect = item.rect;
              return rect.width > 0
                && rect.height > 0
                && rect.left >= fieldRect.left + Math.max(40, fieldRect.width * 0.45)
                && rect.right <= fieldRect.right + 8
                && rect.top >= fieldRect.top - 8
                && rect.bottom <= fieldRect.bottom + 8;
            })
            .map((item) => {
              let score = 0;
              if (item.text.includes("search") || item.text.includes("recherche") || item.text.includes("loupe")) score += 300;
              if (item.text.includes("visibility") || item.text.includes("visibilite") || item.text.includes("eye")) score -= 250;
              if (item.text.includes("close") || item.text.includes("clear")) score -= 250;
              if (item.text.includes("more") || item.text.includes("menu")) score -= 120;
              score += Math.max(0, 180 - Math.abs(item.rect.right - fieldRect.right + 48));
              score += Math.max(0, 60 - Math.abs((item.rect.top + item.rect.height / 2) - (fieldRect.top + fieldRect.height / 2)));
              return {
                x: item.rect.left + item.rect.width / 2,
                y: item.rect.top + item.rect.height / 2,
                score,
                text: item.text
              };
            });
          const unique = [];
          for (const icon of icons.sort((a, b) => b.score - a.score)) {
            if (!unique.some((other) => Math.abs(other.x - icon.x) < 3 && Math.abs(other.y - icon.y) < 3)) {
              unique.push(icon);
            }
          }
          return { icons: unique };
        }
        """
        try:
            result = page.evaluate(script, {"fieldLabel": field_label, "rowIndex": row_index})
            return result if isinstance(result, dict) else {}
        except Exception:
            return {}

    def _wait_for_pointer_selector(self, page: Any, timeout_ms: int = 12000) -> None:
        deadline = time.monotonic() + timeout_ms / 1000
        while time.monotonic() < deadline:
            if self._pointer_selector_visible(page):
                return
            page.wait_for_timeout(300)
        raise AssertionError("The pointer selector table did not open")

    def _pointer_selector_visible(self, page: Any) -> bool:
        script = """
        () => {
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const overlays = Array.from(document.querySelectorAll(
            ".cdk-overlay-pane,[role='dialog'],mat-dialog-container,.mat-mdc-dialog-container,.modal,.dialog"
          )).filter(isVisible);
          const scopes = overlays.length ? overlays : [document.body];
          return scopes.some((scope) => {
            const text = (scope.innerText || scope.textContent || "").toLowerCase();
            const hasGrid = scope.querySelector("[role='grid'],[role='table'],table,.ag-root,.ag-theme-alpine,.mat-mdc-table,.mat-table,.k-grid,.datatable");
            const hasInput = scope.querySelector("input:not([type='hidden']):not([type='checkbox']),textarea");
            const hasButton = /valider|confirmer|sélectionner|selectionner|ok|choisir/.test(text);
            return Boolean(hasGrid || (hasInput && hasButton));
          });
        }
        """
        try:
            return bool(page.evaluate(script))
        except Exception:
            return False

    def _pointer_selector_scope_selector(self, page: Any) -> str:
        script = """
        () => {
          const cssEscape = (value) => {
            if (window.CSS && CSS.escape) return CSS.escape(value);
            return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\\\$&");
          };
          const mark = (element) => {
            const existing = element.getAttribute("data-vplmqa-runtime-id");
            if (existing) return `[data-vplmqa-runtime-id="${cssEscape(existing)}"]`;
            const assigned = `runtime-pointer-scope-${Date.now()}-${Math.random().toString(36).slice(2)}`;
            element.setAttribute("data-vplmqa-runtime-id", assigned);
            return `[data-vplmqa-runtime-id="${cssEscape(assigned)}"]`;
          };
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const scopes = Array.from(document.querySelectorAll(
            ".cdk-overlay-pane,[role='dialog'],mat-dialog-container,.mat-mdc-dialog-container,.modal,.dialog"
          )).filter(isVisible);
          if (scopes.length) return mark(scopes[scopes.length - 1]);
          return mark(document.body);
        }
        """
        return str(page.evaluate(script) or "body")

    def _search_pointer_selector(self, page: Any, value: str) -> None:
        self._wait_for_pointer_selector(page)
        scope_selector = self._pointer_selector_scope_selector(page)
        candidates = [
            page.locator(scope_selector).locator("input[placeholder*='Rechercher' i],input[placeholder*='Filtre' i],input[aria-label*='Rechercher' i],input[aria-label*='Filtre' i]"),
            page.locator(scope_selector).locator("input:not([type='hidden']):not([type='checkbox']),textarea"),
        ]
        for locator in candidates:
            try:
                if locator.count() <= 0:
                    continue
                candidate = locator.first
                if not (self._locator_is_visible(candidate) and self._is_editable_text_locator(candidate)):
                    continue
                candidate.click(timeout=5000)
                try:
                    candidate.press("Control+A", timeout=1000)
                    candidate.press("Backspace", timeout=1000)
                except Exception:
                    candidate.fill("", timeout=2000)
                candidate.type(value, delay=70, timeout=10000)
                try:
                    candidate.press("Enter", timeout=1000)
                except Exception:
                    pass
                page.wait_for_timeout(1200)
                return
            except Exception:
                pass
        raise AssertionError("Could not find the pointer selector search input")

    def _select_pointer_selector_row(self, page: Any, value: str) -> None:
        self._wait_for_pointer_selector(page)
        scope_selector = self._pointer_selector_scope_selector(page)
        exact_text = re.compile(rf"^\s*{re.escape(value)}\s*$", re.I)
        text_candidates = [
            page.locator(scope_selector).get_by_text(exact_text),
            page.locator(scope_selector).get_by_text(re.compile(re.escape(value), re.I)),
        ]
        for locator in text_candidates:
            try:
                count = min(locator.count(), 4)
                for index in range(count):
                    candidate = locator.nth(index)
                    if not self._locator_is_visible(candidate):
                        continue
                    candidate.scroll_into_view_if_needed(timeout=3000)
                    candidate.click(timeout=5000)
                    if not self._pointer_selector_visible(page) or self._wait_for_pointer_selector_action_enabled(page):
                        return
                    try:
                        page.keyboard.press("Space", timeout=1000)
                    except Exception:
                        pass
                    if not self._pointer_selector_visible(page) or self._wait_for_pointer_selector_action_enabled(page, timeout_ms=1200):
                        return
                    try:
                        candidate.dblclick(timeout=5000)
                    except Exception:
                        pass
                    if not self._pointer_selector_visible(page) or self._wait_for_pointer_selector_action_enabled(page):
                        return
            except Exception:
                pass
        script = """
        ({ scopeSelector, value }) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/[^a-z0-9]+/gi, "")
            .toLowerCase();
          const wanted = normalize(value);
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const scope = document.querySelector(scopeSelector) || document.body;
          const rowSelectors = [
            "[role='row']",
            "tr",
            ".ag-row",
            ".mat-mdc-row",
            ".mat-row",
            ".k-table-row",
            ".k-master-row",
            ".datatable-row-wrapper",
            ".datatable-body-row",
            "li",
            ".list-item",
            ".mat-mdc-list-item"
          ];
          const rows = Array.from(scope.querySelectorAll(rowSelectors.join(",")))
            .filter(isVisible)
            .map((row) => {
              const text = row.innerText || row.textContent || "";
              const norm = normalize(text);
              let score = 0;
              if (norm === wanted) score += 120;
              else if (norm.includes(wanted)) score += 100;
              else if (wanted.includes(norm) && norm.length > 4) score += 45;
              const rect = row.getBoundingClientRect();
              return { row, score, area: rect.width * rect.height };
            })
            .filter((item) => item.score > 0)
            .sort((a, b) => (b.score - a.score) || (b.area - a.area));
          const selected = rows[0]?.row;
          if (!selected) return [];
          const checkbox = selected.querySelector("input[type='checkbox'],[role='checkbox']");
          const rowRect = selected.getBoundingClientRect();
          const matchingCell = Array.from(selected.querySelectorAll("[role='gridcell'],td,.ag-cell,.mat-mdc-cell,.mat-cell,span,div"))
            .filter(isVisible)
            .map((cell) => {
              const text = cell.innerText || cell.textContent || "";
              const norm = normalize(text);
              let score = 0;
              if (norm === wanted) score += 120;
              else if (norm.includes(wanted)) score += 100;
              const rect = cell.getBoundingClientRect();
              return { cell, score, rect, area: rect.width * rect.height };
            })
            .filter((item) => item.score > 0)
            .sort((a, b) => (b.score - a.score) || (a.area - b.area))[0];
          const points = [];
          if (checkbox) {
            const rect = checkbox.getBoundingClientRect();
            points.push({ x: rect.left + rect.width / 2, y: rect.top + rect.height / 2, name: "checkbox" });
          }
          if (matchingCell) {
            const rect = matchingCell.rect;
            points.push({ x: rect.left + Math.min(12, Math.max(4, rect.width - 4)), y: rect.top + rect.height / 2, name: "matching-cell-left" });
            points.push({ x: rect.left + Math.min(Math.max(rect.width * 0.35, 12), rect.width - 8), y: rect.top + rect.height / 2, name: "matching-cell" });
            points.push({ x: rect.left + rect.width / 2, y: rect.top + rect.height / 2, name: "matching-cell-center" });
          }
          points.push({ x: rowRect.left + Math.min(42, Math.max(12, rowRect.width * 0.08)), y: rowRect.top + rowRect.height / 2, name: "row-left" });
          points.push({ x: rowRect.left + rowRect.width / 2, y: rowRect.top + rowRect.height / 2, name: "row-center" });
          return points;
        }
        """
        targets = page.evaluate(script, {"scopeSelector": scope_selector, "value": value})
        if not targets:
            raise AssertionError(f"Could not find pointer selector row '{value}'")
        for target in targets[:7]:
            page.mouse.click(float(target["x"]), float(target["y"]))
            if not self._pointer_selector_visible(page) or self._wait_for_pointer_selector_action_enabled(page):
                return
            try:
                page.keyboard.press("Space", timeout=1000)
            except Exception:
                pass
            if not self._pointer_selector_visible(page) or self._wait_for_pointer_selector_action_enabled(page, timeout_ms=1200):
                return
            try:
                page.mouse.dblclick(float(target["x"]), float(target["y"]))
            except Exception:
                pass
            if not self._pointer_selector_visible(page) or self._wait_for_pointer_selector_action_enabled(page):
                return
        raise AssertionError(f"Pointer selector row '{value}' was found but could not be selected")

    def _wait_for_pointer_selector_action_enabled(self, page: Any, timeout_ms: int = 2500) -> bool:
        deadline = time.monotonic() + (timeout_ms / 1000)
        while time.monotonic() < deadline:
            if self._pointer_selector_action_enabled(page):
                return True
            page.wait_for_timeout(150)
        return self._pointer_selector_action_enabled(page)

    def _pointer_selector_action_enabled(self, page: Any) -> bool:
        try:
            scope_selector = self._pointer_selector_scope_selector(page)
            script = """
            (scopeSelector) => {
              const normalize = (text) => String(text || "")
                .normalize("NFD")
                .replace(/[\\u0300-\\u036f]/g, "")
                .replace(/[^a-z0-9]+/gi, "")
                .toLowerCase();
              const isVisible = (element) => {
                const rect = element.getBoundingClientRect();
                const style = window.getComputedStyle(element);
                return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
              };
              const scope = document.querySelector(scopeSelector) || document.body;
              const candidates = Array.from(scope.querySelectorAll("button,[role='button']"))
                .filter(isVisible)
                .filter((button) => {
                  const text = normalize([
                    button.innerText || button.textContent || "",
                    button.getAttribute("aria-label") || "",
                    button.getAttribute("title") || ""
                  ].join(" "));
                  return /selectionner|sélectionner|valider|confirmer|choisir|ok/.test(text);
                });
              return candidates.some((button) => {
                const clickable = button.closest("button,[role='button']") || button;
                const style = window.getComputedStyle(clickable);
                const disabled = clickable.disabled
                  || clickable.getAttribute("aria-disabled") === "true"
                  || clickable.classList.contains("disabled")
                  || clickable.classList.contains("mat-mdc-button-disabled")
                  || clickable.classList.contains("mat-button-disabled")
                  || clickable.classList.contains("mdc-button--disabled")
                  || style.pointerEvents === "none";
                return !disabled;
              });
            }
            """
            return bool(page.evaluate(script, scope_selector))
        except Exception:
            return False

    def _validate_pointer_selector(self, page: Any) -> None:
        self._wait_for_pointer_selector(page)
        scope_selector = self._pointer_selector_scope_selector(page)
        candidates = [
            page.locator(scope_selector).get_by_role("button", name=re.compile(r"valider|confirmer|s[eé]lectionner|choisir|ok", re.I)),
            page.locator(scope_selector).get_by_text(re.compile(r"^\s*(Valider|Confirmer|S[eé]lectionner|Choisir|OK)\s*$", re.I)),
            page.get_by_role("button", name=re.compile(r"valider|confirmer|s[eé]lectionner|choisir|ok", re.I)),
        ]
        last_error: Optional[Exception] = None
        for locator in candidates:
            try:
                count = locator.count()
                for index in range(count):
                    candidate = locator.nth(index)
                    if not self._locator_is_visible(candidate):
                        continue
                    disabled = bool(candidate.evaluate(
                        """element => {
                          const clickable = element.closest("button,[role='button']") || element;
                          const style = window.getComputedStyle(clickable);
                          return clickable.disabled
                            || clickable.getAttribute('aria-disabled') === 'true'
                            || clickable.classList.contains('disabled')
                            || clickable.classList.contains('mat-mdc-button-disabled')
                            || clickable.classList.contains('mat-button-disabled')
                            || clickable.classList.contains('mdc-button--disabled')
                            || style.pointerEvents === 'none';
                        }"""
                    ))
                    if disabled:
                        continue
                    self._safe_click(page, candidate, timeout=8000, dismiss_overlays=False)
                    try:
                        page.wait_for_function(
                            """() => !document.querySelector(".cdk-overlay-pane,[role='dialog'],mat-dialog-container,.mat-mdc-dialog-container,.modal,.dialog")""",
                            timeout=3500,
                        )
                    except Exception:
                        page.wait_for_timeout(800)
                    if not self._pointer_selector_visible(page):
                        return
            except Exception as exc:
                last_error = exc
        raise AssertionError("Could not validate the pointer selector") from last_error

    def _advanced_value_selector_args(
        self,
        step: Dict[str, Any],
        runtime_values: Dict[str, str],
    ) -> Optional[Dict[str, Any]]:
        raw_text = str(step.get("raw_text") or step.get("normalized_text") or "")
        text = re.sub(r"\s+", " ", raw_text).strip()
        lower = text.lower()

        open_match = re.search(
            r"\bopen\s+(?:the\s+)?value\s+selector\s+for\s+(?:the\s+)?(.+?)\s+field\b",
            text,
            re.I,
        )
        if open_match:
            field_label, row_index = self._field_label_and_ordinal(open_match.group(1).replace(" value", "").strip())
            return {"action": "open", "field_label": field_label, "row_index": row_index}

        filter_match = re.search(
            r"\bfilter\s+(?:the\s+)?value\s+selector\s+with\s+[\"']([^\"']+)[\"']",
            text,
            re.I,
        )
        if filter_match:
            value = self._resolve_runtime_phrase(filter_match.group(1), runtime_values) or filter_match.group(1)
            return {"action": "filter", "value": value}

        select_match = re.search(
            r"\bselect\s+[\"']([^\"']+)[\"']\s+from\s+(?:the\s+)?value\s+selector\b",
            text,
            re.I,
        )
        if select_match:
            value = self._resolve_runtime_phrase(select_match.group(1), runtime_values) or select_match.group(1)
            return {"action": "select", "value": value}

        if re.search(r"\bvalidate\s+(?:the\s+)?selected\s+value\b", lower, re.I):
            return {"action": "validate"}
        return None

    def _open_advanced_value_selector(self, page: Any, field_label: str, row_index: int = 0) -> None:
        self._dismiss_blocking_plm_dialog(page)
        script = """
        ({ fieldLabel, rowIndex }) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/[^a-z0-9]+/gi, "")
            .toLowerCase();
          const wanted = normalize(fieldLabel);
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const labelText = (container) => Array.from(container.querySelectorAll(
            "label,mat-label,.mat-mdc-floating-label,.mat-form-field-label,.mdc-floating-label,legend"
          )).map((label) => label.innerText || label.textContent || "").join(" ");
          const containers = Array.from(document.querySelectorAll(
            "mat-form-field,.mat-mdc-form-field,.mat-form-field,.field,.form-group,[class*='field']"
          )).filter(isVisible).map((container) => {
            const rect = container.getBoundingClientRect();
            const label = normalize(labelText(container));
            const identity = normalize([
              labelText(container),
              container.getAttribute("aria-label"),
              container.getAttribute("title"),
              container.innerText || container.textContent || ""
            ].join(" "));
            let score = 0;
            if (label === wanted) score += 100;
            else if (label.includes(wanted)) score += 90;
            else if (identity.includes(wanted)) score += 25;
            if (normalize(labelText(container)).includes("attribut")) score -= 80;
            if (normalize(labelText(container)).includes("operateur")) score -= 80;
            if (normalize(labelText(container)).includes("ajoutduncritere")) score -= 80;
            return { container, score, top: rect.top, left: rect.left };
          }).filter((item) => item.score >= 80);
          containers.sort((a, b) => (a.top - b.top) || (a.left - b.left) || (b.score - a.score));
          const selected = containers[Math.min(Number(rowIndex || 0), Math.max(0, containers.length - 1))];
          if (!selected) return false;
          const clickLike = (element) => {
            const tag = element.tagName.toLowerCase();
            const role = (element.getAttribute("role") || "").toLowerCase();
            const type = (element.getAttribute("type") || "").toLowerCase();
            if (tag === "input" || tag === "textarea" || type === "checkbox") return false;
            return tag === "button"
              || role === "button"
              || tag === "mat-icon"
              || tag === "svg"
              || String(element.className || "").includes("suffix")
              || String(element.className || "").includes("icon");
          };
          const fieldRect = selected.container.getBoundingClientRect();
          const nearestClickable = (element) => {
            let current = element;
            for (let depth = 0; current && depth < 5; depth += 1) {
              if (clickLike(current)) return current;
              current = current.parentElement;
            }
            return element;
          };
          const rightEdgeCandidates = Array.from(selected.container.querySelectorAll(
            "button,[role='button'],.mat-mdc-form-field-icon-suffix,.mat-form-field-suffix,mat-icon,.mat-icon,svg,[class*='icon'],[class*='suffix'],[title],[aria-label]"
          )).filter((element) => {
            if (!isVisible(element)) return false;
            const rect = element.getBoundingClientRect();
            return rect.left >= fieldRect.right - Math.max(92, fieldRect.width * 0.24)
              && rect.top >= fieldRect.top - 4
              && rect.bottom <= fieldRect.bottom + 4;
          }).map((element) => {
            const clickable = nearestClickable(element);
            const rect = clickable.getBoundingClientRect();
            const text = normalize([
              clickable.getAttribute("title"),
              clickable.getAttribute("aria-label"),
              clickable.innerText || clickable.textContent || "",
              element.getAttribute("title"),
              element.getAttribute("aria-label"),
              element.innerText || element.textContent || ""
            ].join(" "));
            let score = 0;
            if (text.includes("erp") || text.includes("tclcod") || text.includes("categorie")) score += 50;
            if (clickLike(clickable)) score += 25;
            score += Math.max(0, Math.round(rect.left - fieldRect.left));
            return {
              x: rect.left + rect.width / 2,
              y: rect.top + rect.height / 2,
              right: rect.right,
              score
            };
          }).sort((a, b) => b.right - a.right);
          if (rightEdgeCandidates.length) return rightEdgeCandidates[0];
          return {
            x: Math.max(fieldRect.left + 4, fieldRect.right - 26),
            y: fieldRect.top + fieldRect.height / 2,
            right: fieldRect.right,
            score: 1
          };
        }
        """
        last_error: Optional[Exception] = None
        for attempt in range(4):
            try:
                target = page.evaluate(script, {"fieldLabel": field_label, "rowIndex": row_index})
                if not target:
                    page.wait_for_timeout(400 + attempt * 250)
                    continue
                page.mouse.click(float(target["x"]), float(target["y"]))
                try:
                    self._wait_for_value_selector_dialog(page, timeout_ms=2500 + attempt * 1500)
                    return
                except Exception as exc:
                    last_error = exc
                    page.wait_for_timeout(350 + attempt * 250)
            except Exception as exc:
                last_error = exc
                page.wait_for_timeout(500 + attempt * 250)
        raise AssertionError(f"The value selector dialog did not open for field '{field_label}'") from last_error

    def _wait_for_value_selector_dialog(self, page: Any, timeout_ms: int = 10000) -> None:
        try:
            page.get_by_text(re.compile(r"S[eé]lectionner une valeur", re.I)).first.wait_for(
                state="visible",
                timeout=timeout_ms,
            )
            return
        except Exception:
            pass
        try:
            page.get_by_placeholder(re.compile(r"filtrer", re.I)).first.wait_for(state="visible", timeout=timeout_ms)
            return
        except Exception as exc:
            raise AssertionError("The value selector dialog did not open") from exc

    def _filter_value_selector(self, page: Any, value: str) -> None:
        self._wait_for_value_selector_dialog(page)
        candidates = [
            page.get_by_placeholder(re.compile(r"filtrer", re.I)),
            page.locator(".cdk-overlay-pane input:not([type='hidden']):not([type='checkbox'])"),
            page.locator("[role='dialog'] input:not([type='hidden']):not([type='checkbox'])"),
        ]
        for locator in candidates:
            try:
                if locator.count() > 0:
                    candidate = locator.first
                    if self._locator_is_visible(candidate) and self._is_editable_text_locator(candidate):
                        candidate.click(timeout=5000)
                        try:
                            candidate.press("Control+A", timeout=1000)
                            candidate.press("Backspace", timeout=1000)
                        except Exception:
                            candidate.fill("", timeout=2000)
                        candidate.type(value, delay=70, timeout=10000)
                        try:
                            candidate.evaluate(
                                """element => {
                                  element.dispatchEvent(new Event('input', { bubbles: true }));
                                  element.dispatchEvent(new Event('change', { bubbles: true }));
                                  element.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: 'Enter' }));
                                }"""
                            )
                        except Exception:
                            pass
                        page.wait_for_timeout(1400)
                        return
            except Exception:
                pass
        raise AssertionError("Could not find the value selector filter input")

    def _select_value_selector_option(self, page: Any, option_label: str) -> None:
        self._wait_for_value_selector_dialog(page)
        if not self._value_selector_option_visible(page, option_label):
            page.wait_for_timeout(1200)
        if not self._value_selector_option_visible(page, option_label):
            self._expand_value_selector_tree(page)
        script = """
        ({ optionLabel }) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/[^a-z0-9]+/gi, "")
            .toLowerCase();
          const target = normalize(optionLabel);
          const targetWithoutCode = normalize(String(optionLabel || "").replace(/\\[[^\\]]+\\]/g, " "));
          const targetCode = normalize((String(optionLabel || "").match(/\\[([^\\]]+)\\]/) || [])[1] || "");
          const targets = Array.from(new Set([target, targetWithoutCode, targetCode].filter((item) => item.length >= 3)));
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const overlay = Array.from(document.querySelectorAll(".cdk-overlay-pane,[role='dialog'],mat-dialog-container"))
            .filter(isVisible)
            .pop() || document.body;
          const rows = Array.from(overlay.querySelectorAll("li,mat-tree-node,.mat-tree-node,[role='treeitem'],[role='option'],.mat-mdc-list-item,.mat-list-item,div"))
            .filter(isVisible)
            .map((element) => {
              const text = element.innerText || element.textContent || "";
              const norm = normalize(text);
              let score = 0;
              for (const item of targets) {
                if (norm === item) score = Math.max(score, 100);
                else if (norm.includes(item)) score = Math.max(score, 80);
                else if (item.includes(norm) && norm.length > 4) score = Math.max(score, 45);
              }
              return { element, score, textLength: text.length };
            })
            .filter((item) => item.score > 0)
            .sort((a, b) => (b.score - a.score) || (a.textLength - b.textLength));
          const selected = rows[0]?.element;
          if (!selected) return "missing";
          const checkbox = selected.querySelector("input[type='checkbox'],[role='checkbox']")
            || selected.closest("li,mat-tree-node,.mat-tree-node,[role='treeitem'],.mat-mdc-list-item,.mat-list-item,div")?.querySelector("input[type='checkbox'],[role='checkbox']");
          if (checkbox) {
            const checked = checkbox.checked || checkbox.getAttribute("aria-checked") === "true";
            if (!checked) checkbox.click();
            return "selected";
          }
          selected.click();
          return "selected";
        }
        """
        try:
            result = str(page.evaluate(script, {"optionLabel": option_label}) or "")
        except Exception as exc:
            raise AssertionError(f"Could not select value selector option '{option_label}'") from exc
        if result != "selected":
            raise AssertionError(f"Could not find value selector option '{option_label}'")
        page.wait_for_timeout(350)

    def _value_selector_option_visible(self, page: Any, option_label: str) -> bool:
        script = """
        ({ optionLabel }) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/[^a-z0-9]+/gi, "")
            .toLowerCase();
          const target = normalize(optionLabel);
          const targetWithoutCode = normalize(String(optionLabel || "").replace(/\\[[^\\]]+\\]/g, " "));
          const targets = Array.from(new Set([target, targetWithoutCode].filter((item) => item.length >= 3)));
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const overlay = Array.from(document.querySelectorAll(".cdk-overlay-pane,[role='dialog'],mat-dialog-container"))
            .filter(isVisible)
            .pop() || document.body;
          return Array.from(overlay.querySelectorAll("li,mat-tree-node,.mat-tree-node,[role='treeitem'],[role='option'],.mat-mdc-list-item,.mat-list-item,div"))
            .filter(isVisible)
            .some((element) => {
              const norm = normalize(element.innerText || element.textContent || "");
              return targets.some((item) => norm === item || norm.includes(item));
            });
        }
        """
        try:
            return bool(page.evaluate(script, {"optionLabel": option_label}))
        except Exception:
            return False

    def _expand_value_selector_tree(self, page: Any) -> None:
        script = """
        () => {
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const overlay = Array.from(document.querySelectorAll(".cdk-overlay-pane,[role='dialog'],mat-dialog-container"))
            .filter(isVisible)
            .pop() || document.body;
          const candidates = Array.from(overlay.querySelectorAll(
            "mat-tree-node button,mat-nested-tree-node button,.mat-tree-node button,[role='treeitem'] button,"
            + "mat-icon,.mat-icon,button,[role='button'],svg"
          )).filter((element) => {
            if (!isVisible(element)) return false;
            const text = [
              element.innerText || element.textContent || "",
              element.getAttribute("aria-label") || "",
              element.getAttribute("title") || "",
              element.className || ""
            ].join(" ").toLowerCase();
            const rect = element.getBoundingClientRect();
            const looksLikeExpander = text.includes("expand")
              || text.includes("chevron")
              || text.includes("keyboard_arrow_right")
              || text.includes("keyboard_arrow_down")
              || text.includes("arrow")
              || (rect.width <= 40 && rect.height <= 40);
            return looksLikeExpander;
          });
          let clicked = 0;
          for (const element of candidates.slice(0, 8)) {
            const text = (element.innerText || element.textContent || "").toLowerCase();
            const ariaExpanded = element.getAttribute("aria-expanded");
            if (ariaExpanded === "true" || text.includes("keyboard_arrow_down")) continue;
            element.click();
            clicked += 1;
          }
          return clicked;
        }
        """
        for _ in range(2):
            try:
                clicked = int(page.evaluate(script) or 0)
                page.wait_for_timeout(350 if clicked else 150)
            except Exception:
                return

    def _validate_value_selector(self, page: Any) -> None:
        self._wait_for_value_selector_dialog(page)
        candidates = [
            page.get_by_role("button", name=re.compile(r"^\s*Valider\s*$", re.I)),
            page.get_by_text(re.compile(r"^\s*Valider\s*$", re.I)),
        ]
        last_error: Optional[Exception] = None
        for locator in candidates:
            try:
                if locator.count() > 0:
                    self._safe_click(page, locator.first, timeout=8000, dismiss_overlays=False)
                    try:
                        page.get_by_text(re.compile(r"S[eé]lectionner une valeur", re.I)).first.wait_for(
                            state="hidden",
                            timeout=5000,
                        )
                    except Exception:
                        pass
                    page.wait_for_timeout(500)
                    return
            except Exception as exc:
                last_error = exc
        raise AssertionError("Could not validate the selected value") from last_error

    def _ordinal_prefix(self, ordinal_index: int) -> str:
        prefixes = [
            "first",
            "second",
            "third",
            "fourth",
            "fifth",
            "sixth",
            "seventh",
            "eighth",
            "ninth",
            "tenth",
        ]
        if 0 <= ordinal_index < len(prefixes):
            return prefixes[ordinal_index]
        return f"{ordinal_index + 1}th"

    def _check_advanced_condition_row(self, page: Any, row_index: int) -> None:
        selector = self._advanced_condition_row_checkbox_selector(page, row_index)
        if not selector:
            raise AssertionError(f"Could not find condition row checkbox #{row_index + 1}")
        locator = page.locator(selector).first
        try:
            checked = bool(locator.is_checked(timeout=1000))
        except Exception:
            checked = False
        if not checked:
            self._safe_click(page, locator, timeout=8000)
        page.wait_for_timeout(250)

    def _advanced_condition_row_checkbox_selector(self, page: Any, row_index: int) -> str:
        script = """
        ({ rowIndex }) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/[^a-z0-9]+/gi, "")
            .toLowerCase();
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const cssEscape = (value) => {
            if (window.CSS && CSS.escape) return CSS.escape(value);
            return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\\\$&");
          };
          const mark = (element) => {
            const existing = element.getAttribute("data-vplmqa-runtime-id");
            if (existing) return `[data-vplmqa-runtime-id="${cssEscape(existing)}"]`;
            const assigned = `runtime-condition-checkbox-${Date.now()}-${Math.random().toString(36).slice(2)}`;
            element.setAttribute("data-vplmqa-runtime-id", assigned);
            return `[data-vplmqa-runtime-id="${cssEscape(assigned)}"]`;
          };
          const rowTextFor = (checkbox) => {
            let current = checkbox.parentElement;
            let best = "";
            for (let depth = 0; current && depth < 9; depth += 1) {
              const text = current.innerText || current.textContent || "";
              const normalized = normalize(text);
              if (
                (normalized.includes("ajoutduncritere") || normalized.includes("attribut") || normalized.includes("operateur"))
                && !normalized.includes("toutselectionner")
              ) {
                best = text;
              }
              current = current.parentElement;
            }
            return best;
          };
          const checkboxes = Array.from(document.querySelectorAll("input[type='checkbox'], [role='checkbox']"))
            .filter((checkbox) => isVisible(checkbox))
            .map((checkbox) => {
              const rect = checkbox.getBoundingClientRect();
              return { checkbox, top: rect.top, left: rect.left, text: rowTextFor(checkbox) };
            })
            .filter((item) => item.text && item.top > 80)
            .sort((a, b) => (a.top - b.top) || (a.left - b.left));
          const uniqueRows = [];
          for (const item of checkboxes) {
            if (uniqueRows.some((existing) => Math.abs(existing.top - item.top) < 12)) continue;
            uniqueRows.push(item);
          }
          const selected = uniqueRows[Math.min(Number(rowIndex || 0), Math.max(0, uniqueRows.length - 1))];
          return selected ? mark(selected.checkbox) : "";
        }
        """
        try:
            return str(page.evaluate(script, {"rowIndex": row_index}) or "")
        except Exception:
            return ""

    def _group_selected_advanced_conditions(self, page: Any) -> None:
        selector = self._advanced_condition_group_button_selector(page)
        if not selector:
            raise AssertionError("Could not find the advanced-search group conditions button")
        self._safe_click(page, page.locator(selector).first, timeout=8000)
        page.wait_for_timeout(600)

    def _advanced_condition_group_button_selector(self, page: Any) -> str:
        script = """
        () => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/[^a-z0-9]+/gi, "")
            .toLowerCase();
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const cssEscape = (value) => {
            if (window.CSS && CSS.escape) return CSS.escape(value);
            return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\\\$&");
          };
          const mark = (element) => {
            const existing = element.getAttribute("data-vplmqa-runtime-id");
            if (existing) return `[data-vplmqa-runtime-id="${cssEscape(existing)}"]`;
            const assigned = `runtime-condition-group-${Date.now()}-${Math.random().toString(36).slice(2)}`;
            element.setAttribute("data-vplmqa-runtime-id", assigned);
            return `[data-vplmqa-runtime-id="${cssEscape(assigned)}"]`;
          };
          const wanted = ["grouper", "regrouper", "group", "creergroupe", "creerungroupe", "groupconditions"];
          const iconHints = ["account_tree", "call_split", "merge_type", "schema", "lan"];
          const candidates = Array.from(document.querySelectorAll("button,[role='button'],.mat-mdc-icon-button,.mat-icon-button"))
            .filter((button) => isVisible(button))
            .map((button) => {
              const rect = button.getBoundingClientRect();
              const attrs = [
                button.innerText,
                button.textContent,
                button.getAttribute("aria-label"),
                button.getAttribute("title"),
                button.getAttribute("mattooltip"),
                button.getAttribute("ng-reflect-message")
              ].join(" ");
              const normalized = normalize(attrs);
              let score = 0;
              if (wanted.some((item) => normalized.includes(item))) score += 100;
              if (iconHints.some((item) => normalized.includes(normalize(item)))) score += 50;
              if (rect.top < 220) score += 8;
              if (rect.left < 260) score += 4;
              return { button, score, top: rect.top, left: rect.left };
            })
            .filter((item) => item.score >= 50)
            .sort((a, b) => (b.score - a.score) || (a.top - b.top) || (a.left - b.left));
          return candidates[0] ? mark(candidates[0].button) : "";
        }
        """
        try:
            return str(page.evaluate(script) or "")
        except Exception:
            return ""

    def _select_condition_operator(self, page: Any, option_label: str, ordinal_index: int = 0, grouped: bool = False) -> bool:
        selector = self._condition_operator_selector(page, ordinal_index, grouped)
        if not selector:
            return False
        try:
            self._safe_click(page, page.locator(selector).first, timeout=8000)
            self._click_dropdown_option(page, option_label)
            return True
        except Exception:
            return False

    def _condition_operator_selector(self, page: Any, ordinal_index: int = 0, grouped: bool = False) -> str:
        script = """
        ({ ordinalIndex, grouped }) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/[^a-z0-9]+/gi, "")
            .toLowerCase();
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const cssEscape = (value) => {
            if (window.CSS && CSS.escape) return CSS.escape(value);
            return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\\\$&");
          };
          const mark = (element) => {
            const existing = element.getAttribute("data-vplmqa-runtime-id");
            if (existing) return `[data-vplmqa-runtime-id="${cssEscape(existing)}"]`;
            const assigned = `runtime-condition-operator-${Date.now()}-${Math.random().toString(36).slice(2)}`;
            element.setAttribute("data-vplmqa-runtime-id", assigned);
            return `[data-vplmqa-runtime-id="${cssEscape(assigned)}"]`;
          };
          const controls = Array.from(document.querySelectorAll("select,[role='combobox'],.mat-mdc-select,.mat-select,[aria-haspopup='listbox'],button,[role='button']"))
            .filter((control) => isVisible(control))
            .map((control) => {
              const rect = control.getBoundingClientRect();
              const text = [
                control.innerText,
                control.textContent,
                control.getAttribute("aria-label"),
                control.getAttribute("title"),
                control.getAttribute("placeholder")
              ].join(" ");
              const normalized = normalize(text);
              let score = 0;
              if (normalized === "et" || normalized === "ou") score += 100;
              if (normalized.includes("et") || normalized.includes("ou")) score += 20;
              if (rect.left < 260) score += 15;
              if (rect.top > 120) score += 5;
              return { control, score, top: rect.top, left: rect.left };
            })
            .filter((item) => item.score >= 90)
            .sort((a, b) => (a.top - b.top) || (a.left - b.left));
          const index = grouped ? controls.length - 1 : Math.min(Number(ordinalIndex || 0), Math.max(0, controls.length - 1));
          const selected = controls[Math.max(0, index)];
          return selected ? mark(selected.control) : "";
        }
        """
        try:
            return str(page.evaluate(script, {"ordinalIndex": ordinal_index, "grouped": grouped}) or "")
        except Exception:
            return ""

    def _normalize_text_key(self, value: str) -> str:
        text = unicodedata.normalize("NFD", str(value or ""))
        text = "".join(ch for ch in text if unicodedata.category(ch) != "Mn")
        return re.sub(r"[^a-z0-9]+", "", text.lower())

    def _select_custom_field_by_normalized_label(
        self,
        page: Any,
        field_label: str,
        option_label: str,
        ordinal_index: int = 0,
    ) -> bool:
        script = """
        ({ fieldLabel, optionLabel, ordinalIndex }) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/[^a-z0-9]+/gi, "")
            .toLowerCase();
          const wanted = normalize(fieldLabel);
          const wantedOption = normalize(optionLabel);
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const cssEscape = (value) => {
            if (window.CSS && CSS.escape) return CSS.escape(value);
            return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\\\$&");
          };
          const mark = (element) => {
            const existing = element.getAttribute("data-vplmqa-runtime-id");
            if (existing) return `[data-vplmqa-runtime-id="${cssEscape(existing)}"]`;
            const assigned = `runtime-select-${Date.now()}-${Math.random().toString(36).slice(2)}`;
            element.setAttribute("data-vplmqa-runtime-id", assigned);
            return `[data-vplmqa-runtime-id="${cssEscape(assigned)}"]`;
          };
          const isSelectLike = (element) => {
            const tag = element.tagName.toLowerCase();
            const role = (element.getAttribute("role") || "").toLowerCase();
            const className = String(element.className || "");
            const ariaHasPopup = (element.getAttribute("aria-haspopup") || "").toLowerCase();
            if (tag === "select") return true;
            if (tag === "mat-form-field") return true;
            if (role === "combobox" || role === "listbox") return true;
            if (className.includes("mat-mdc-select") || className.includes("mat-select")) return true;
            if (className.includes("mat-mdc-form-field") || className.includes("mat-form-field")) return true;
            if (ariaHasPopup.includes("listbox")) return true;
            return false;
          };
          const fieldSelector = [
            "select",
            "[role='combobox']",
            ".mat-mdc-select",
            ".mat-select",
            "[aria-haspopup='listbox']",
            "mat-form-field",
            ".mat-mdc-form-field",
            ".mat-form-field"
          ].join(",");
          const controls = Array.from(document.querySelectorAll(fieldSelector))
            .filter((control) => isVisible(control) && isSelectLike(control));
          const matches = controls.map((control) => {
            const rect = control.getBoundingClientRect();
            const container = control.closest("mat-form-field,.mat-mdc-form-field,.mat-form-field,.field,.form-group,[class*='field'],[class*='select']")
              || control.parentElement
              || control;
            const labelText = Array.from(container.querySelectorAll("label,mat-label,.mat-mdc-floating-label,.mat-form-field-label,.mdc-floating-label,legend"))
              .map((label) => label.innerText || label.textContent || "")
              .join(" ");
            const context = [
              labelText,
              control.id,
              control.name,
              control.getAttribute("aria-label"),
              control.getAttribute("placeholder"),
              control.getAttribute("title"),
              control.getAttribute("formcontrolname"),
              container.innerText || container.textContent || ""
            ].join(" ");
            const normalizedLabel = normalize(labelText);
            const normalized = normalize(context);
            const currentValue = normalize([
              control.value,
              control.innerText || control.textContent || "",
              container.innerText || container.textContent || ""
            ].join(" "));
            let score = 0;
            if (normalizedLabel === wanted) score += 80;
            else if (normalizedLabel.includes(wanted)) score += 70;
            else if (normalized.includes(wanted)) score += 40;
            if (control.getAttribute("role") === "combobox" || control.tagName === "SELECT" || control.className.toString().includes("select")) score += 10;
            const clickTarget = control.matches("mat-form-field,.mat-mdc-form-field,.mat-form-field")
              ? (
                  control.querySelector(".mat-mdc-select-arrow-wrapper,.mat-mdc-select-arrow,.mat-select-arrow-wrapper,.mat-select-arrow")
                  || control.querySelector(".mat-mdc-select-trigger,.mat-select-trigger,[role='combobox']")
                  || control.querySelector("input")
                  || control.querySelector(".mat-mdc-form-field-infix,.mat-form-field-infix")
                  || control
                )
              : control;
            return { control: clickTarget, score, top: rect.top, left: rect.left, currentValue };
          }).filter((item) => item.score >= 40);
          matches.sort((a, b) => {
            if (Number(ordinalIndex || 0) > 0) {
              return (a.top - b.top) || (a.left - b.left) || (b.score - a.score);
            }
            return (b.score - a.score) || (a.top - b.top) || (a.left - b.left);
          });
          const selected = matches[Math.min(Number(ordinalIndex || 0), Math.max(0, matches.length - 1))];
          if (!selected) return "";
          if (wantedOption && selected.currentValue.includes(wantedOption)) return "__already_selected__";
          return mark(selected.control);
        }
        """
        last_error: Optional[Exception] = None
        for attempt in range(4):
            try:
                selector = str(page.evaluate(script, {
                    "fieldLabel": field_label,
                    "optionLabel": option_label,
                    "ordinalIndex": ordinal_index,
                }) or "")
            except Exception as exc:
                last_error = exc
                selector = ""
            if selector == "__already_selected__":
                return True
            if not selector:
                page.wait_for_timeout(500 + attempt * 300)
                continue
            try:
                page.wait_for_timeout(300 + attempt * 250)
                locator = page.locator(selector).first
                self._safe_click(page, locator, timeout=6000)
                try:
                    self._click_dropdown_option(page, option_label)
                except Exception:
                    self._type_into_open_select_filter_and_choose(page, option_label)
                return True
            except Exception as exc:
                last_error = exc
                try:
                    page.keyboard.press("Escape")
                except Exception:
                    pass
                page.wait_for_timeout(500 + attempt * 300)
        return False

    def _type_into_open_select_filter_and_choose(self, page: Any, option_label: str) -> None:
        try:
            page.wait_for_timeout(250)
        except Exception:
            pass
        input_candidates = [
            self._focused_text_input(page),
            page.locator(".cdk-overlay-pane input:not([type='hidden']):not([type='checkbox'])"),
            page.locator("[aria-expanded='true'] input:not([type='hidden']):not([type='checkbox'])"),
            page.locator("input[role='combobox'][aria-expanded='true']:not([type='hidden']):not([type='checkbox'])"),
        ]
        typed = False
        for locator in input_candidates:
            try:
                if locator is None:
                    continue
                if locator.count() <= 0:
                    continue
                candidate = locator.first
                if self._locator_is_visible(candidate) and self._is_editable_text_locator(candidate):
                    candidate.fill(option_label, timeout=4000)
                    typed = True
                    page.wait_for_timeout(600)
                    break
            except Exception:
                continue
        if typed:
            try:
                self._click_dropdown_option(page, option_label)
                return
            except Exception:
                pass
        try:
            page.keyboard.press("ArrowDown")
            page.wait_for_timeout(150)
            page.keyboard.press("Enter")
            self._wait_for_dropdown_to_close(page)
            return
        except Exception as exc:
            raise AssertionError(f"Could not choose filtered option '{option_label}'") from exc

    def _click_dropdown_option(self, page: Any, option_label: str) -> None:
        option_pattern = re.compile(rf"^\s*{re.escape(option_label)}\s*$", re.I)
        candidates = [
            page.get_by_role("option", name=option_pattern),
            page.locator("[role='listbox'] [role='option']").filter(has_text=option_pattern),
            page.locator("li[role='option']").filter(has_text=option_pattern),
            page.locator(".MuiMenuItem-root").filter(has_text=option_pattern),
            page.get_by_text(option_pattern),
        ]
        last_error: Optional[Exception] = None
        for locator in candidates:
            try:
                if locator.count() > 0:
                    candidate = locator.first
                    candidate.scroll_into_view_if_needed(timeout=3000)
                    candidate.click(timeout=10000, force=True)
                    self._wait_for_dropdown_to_close(page)
                    return
            except Exception as exc:
                last_error = exc
        normalized_selector = self._normalized_dropdown_option_selector(page, option_label)
        if normalized_selector:
            self._safe_click(page, page.locator(normalized_selector).first, dismiss_overlays=False)
            self._wait_for_dropdown_to_close(page)
            return
        normalized_target = self._normalized_text_click_locator(page, option_label)
        if normalized_target is not None:
            self._safe_click(page, normalized_target, dismiss_overlays=False)
            self._wait_for_dropdown_to_close(page)
            return
        raise AssertionError(f"Could not click dropdown option '{option_label}'") from last_error

    def _normalized_dropdown_option_selector(self, page: Any, option_label: str) -> str:
        script = """
        ({ optionLabel }) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/[^a-z0-9]+/gi, "")
            .toLowerCase();
          const wanted = normalize(optionLabel);
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const cssEscape = (value) => {
            if (window.CSS && CSS.escape) return CSS.escape(value);
            return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\\\$&");
          };
          const mark = (element) => {
            const existing = element.getAttribute("data-vplmqa-runtime-id");
            if (existing) return `[data-vplmqa-runtime-id="${cssEscape(existing)}"]`;
            const assigned = `runtime-option-${Date.now()}-${Math.random().toString(36).slice(2)}`;
            element.setAttribute("data-vplmqa-runtime-id", assigned);
            return `[data-vplmqa-runtime-id="${cssEscape(assigned)}"]`;
          };
          const selectors = [
            ".cdk-overlay-pane [role='option']",
            ".cdk-overlay-pane mat-option",
            ".cdk-overlay-pane .mat-mdc-option",
            ".cdk-overlay-pane .mat-option",
            ".cdk-overlay-pane li",
            "[role='listbox'] [role='option']"
          ];
          const options = Array.from(document.querySelectorAll(selectors.join(",")))
            .filter(isVisible)
            .map((element) => {
              const text = element.innerText || element.textContent || "";
              const normalized = normalize(text);
              let score = 0;
              if (normalized === wanted) score += 100;
              else if (normalized.includes(wanted)) score += 80;
              else if (wanted.includes(normalized) && normalized.length > 4) score += 45;
              return { element, score, length: text.length };
            })
            .filter((item) => item.score > 0)
            .sort((a, b) => (b.score - a.score) || (a.length - b.length));
          return options[0] ? mark(options[0].element) : "";
        }
        """
        try:
            return str(page.evaluate(script, {"optionLabel": option_label}) or "")
        except Exception:
            return ""

    def _wait_for_dropdown_to_close(self, page: Any) -> None:
        try:
            page.locator(
                ".cdk-overlay-backdrop-showing, .cdk-overlay-pane [role='listbox'], .mat-mdc-select-panel, .mat-select-panel"
            ).first.wait_for(state="hidden", timeout=3000)
        except Exception:
            try:
                page.keyboard.press("Escape")
                page.wait_for_timeout(300)
            except Exception:
                pass

    def _self_heal_step(
        self,
        page: Any,
        step: Dict[str, Any],
        project_context: Dict[str, Any],
        error: Exception,
    ) -> bool:
        text = step["normalized_text"].lower()
        args = step.get("arguments", {})
        value = self._first_arg(args, project_context) if args else ""
        raw = str(step.get("raw_text") or step.get("normalized_text") or "")

        try:
            if any(term in text for term in ("navigate", "go to", "open")):
                target = self._resolve_navigation_target(page, text, value, project_context)
                page.goto(target, wait_until="networkidle", timeout=45000)
                self._recover_project_module_navigation(page, text, value)
                return True

            if any(term in text for term in ("enter ", "fill ", "type ")) and any(
                field in text for field in ("problem title", "description", "assigned", "project id")
            ):
                self._ensure_ticket_form(page)
                return True

            if any(term in text for term in ("click", "press", "submit", "choose")):
                if value:
                    self._click_text_target(page, value)
                else:
                    self._click_by_intent(page, text)
                return True

            if "see" in text or "contain" in text or "visible" in text:
                page.wait_for_timeout(1200)
                if "ticket created" in f"{text} {value}".lower():
                    try:
                        page.reload(wait_until="domcontentloaded", timeout=30000)
                    except Exception:
                        pass
                return True
        except Exception as heal_exc:
            print(
                f"Self-healing failed for step '{raw}': {heal_exc}. Original error: {error}",
                flush=True,
            )
            return False
        print(f"Self-healing retried step '{raw}' after: {error}", flush=True)
        return True

    def _assert_project_navigation_state(self, page: Any, step_text: str, value: str) -> None:
        requested_page = self._requested_page_name(step_text, value)
        requested_key = self._page_match_key(requested_page)
        if requested_key == "tickets":
            tickets_heading = page.get_by_text(re.compile(r"^Tickets$", re.I)).first
            ticket_action = page.get_by_role("button", name=re.compile(r"create\s+(?:the\s+)?ticket", re.I)).first
            if self._locator_is_visible(tickets_heading) or self._locator_is_visible(ticket_action):
                return
            home_heading = page.get_by_text(re.compile(r"Welcome to VPLMQA", re.I)).first
            if self._locator_is_visible(home_heading):
                raise AssertionError(f"Expected Tickets page, but Home is visible at {page.url}")

    def _resolve_navigation_target(self, page: Any, step_text: str, value: str, context: Dict[str, Any]) -> str:
        if value.startswith(("http://", "https://")):
            return self._host_browser_url(value)

        requested_page = self._requested_page_name(step_text, value)
        requested_key = self._page_match_key(requested_page)
        base_url = self._navigation_base_url(context, page)
        known_path = self._known_app_page_path(requested_key)

        if value and value.startswith("/") and base_url:
            return self._host_browser_url(self._with_cache_buster(self._join_url(base_url, value)))
        if value and base_url and self._looks_like_relative_path(value):
            return self._host_browser_url(self._with_cache_buster(self._join_url(base_url, value)))
        if known_path and base_url:
            return self._host_browser_url(self._with_cache_buster(self._join_url(base_url, known_path)))
        if str(context.get("targetMode", "EXTERNAL")).upper() != "PROJECT":
            fallback_base = self._navigation_base_url(context, page, include_current=True)
            if value and fallback_base:
                return self._host_browser_url(self._with_cache_buster(self._join_url(fallback_base, value)))
            raise ValueError(
                "Cannot resolve navigation target to an absolute URL. "
                "Use an absolute URL in the step or set the project base URL."
            )

        pages = context.get("pages", []) or []
        default_page_id = str(context.get("defaultPageId") or "")

        if requested_key:
            exact_web = [
                page for page in pages
                if self._is_web_page(page)
                and (
                    self._page_match_key(str(page.get("name") or "")) == requested_key
                    or self._page_match_key(str(page.get("path") or "").strip("/")) == requested_key
                )
            ]
            if exact_web:
                selected = exact_web[0]
                context["activePageId"] = str(selected.get("id") or "")
                return self._page_url(selected, context)

            exact_any = [
                page for page in pages
                if self._page_match_key(str(page.get("name") or "")) == requested_key
                or self._page_match_key(str(page.get("path") or "").strip("/")) == requested_key
            ]
            if exact_any:
                selected = exact_any[0]
                context["activePageId"] = str(selected.get("id") or "")
                return self._page_url(selected, context)

        words = set(re.sub(r"[^a-z0-9]+", " ", f"{requested_page or step_text} {value}".lower()).split())
        selected = None
        default_page = None
        best_score = -1
        for candidate in pages:
            if str(candidate.get("id") or "") == default_page_id:
                default_page = candidate
            signals = " ".join(str(candidate.get(key) or "") for key in ("name", "path", "url", "source"))
            score = len(words & set(re.sub(r"[^a-z0-9]+", " ", signals.lower()).split()))
            if self._is_web_page(candidate):
                score += 2
            if score > best_score:
                selected, best_score = candidate, score
        if best_score <= 0 and default_page is not None:
            selected = default_page
        if not selected:
            raise ValueError("No project page matches this navigation step")
        context["activePageId"] = str(selected.get("id") or "")
        return self._page_url(selected, context)

    def _page_url(self, selected: Dict[str, Any], context: Dict[str, Any]) -> str:
        url = str(selected.get("url") or "").strip()
        if url.startswith(("http://", "https://")):
            return self._host_browser_url(self._with_cache_buster(url))
        base_url = self._navigation_base_url(context)
        path = str(selected.get("path") or url or "").strip()
        if not base_url:
            raise ValueError("The selected project does not have a base URL")
        return self._host_browser_url(self._with_cache_buster(self._join_url(base_url, path)))

    def _navigation_base_url(self, context: Dict[str, Any], page: Any = None, include_current: bool = False) -> str:
        project = context.get("project") or {}
        candidates: List[str] = [
            str(project.get("baseUrl") or "").strip(),
            str(project.get("url") or "").strip(),
            os.getenv("E2E_TARGET_BASE_URL", "").strip(),
            os.getenv("VPLM_TARGET_BASE_URL", "").strip(),
            os.getenv("VPLM_BASE_URL", "").strip(),
            os.getenv("TARGET_BASE_URL", "").strip(),
        ]
        for candidate_page in context.get("pages", []) or []:
            candidates.append(str(candidate_page.get("url") or "").strip())
        if include_current and page is not None:
            try:
                candidates.append(str(page.url or "").strip())
            except Exception:
                pass
        for candidate in candidates:
            if candidate.startswith(("http://", "https://")) and not self._is_internal_tool_url(candidate):
                return candidate.rstrip("/")
        return ""

    def _is_internal_tool_url(self, url: str) -> bool:
        try:
            parsed = urllib.parse.urlsplit(url)
        except Exception:
            return False
        host = (parsed.hostname or "").lower()
        return host in {"localhost", "127.0.0.1"} and parsed.port in {3000, 8080, 8083, 8090}

    def _looks_like_relative_path(self, value: str) -> bool:
        cleaned = value.strip()
        if not cleaned or " " in cleaned:
            return False
        if cleaned.startswith(("./", "../", "?", "#")):
            return True
        return "/" in cleaned or cleaned.endswith(".html")

    def _known_app_page_path(self, requested_key: str) -> str:
        mapping = {
            "login": "/apps/plm/portal/login",
            "signin": "/apps/plm/portal/login",
            "connexion": "/apps/plm/portal/login",
            "home": "/apps/plm/portal/home",
            "homepage": "/apps/plm/portal/home",
            "accueil": "/apps/plm/portal/home",
            "advancedsearch": "/apps/plm/search/home",
            "rechercheavancee": "/apps/plm/search/home",
            "search": "/apps/plm/search/home",
            "recherche": "/apps/plm/search/home",
        }
        return mapping.get(requested_key, "")

    def _join_url(self, base_url: str, path: str) -> str:
        base = base_url.strip()
        target = path.strip()
        if target.startswith(("http://", "https://")):
            return target
        if not target:
            return base
        parsed_base = urllib.parse.urlsplit(base)
        if target.startswith("/") and parsed_base.scheme and parsed_base.netloc:
            return urllib.parse.urlunsplit((parsed_base.scheme, parsed_base.netloc, target, "", ""))
        return urllib.parse.urljoin(base.rstrip("/") + "/", target.lstrip("/"))

    def _with_cache_buster(self, url: str) -> str:
        separator = "&" if "?" in url else "?"
        return f"{url}{separator}__e2e={int(time.time() * 1000)}"

    def _requested_page_name(self, step_text: str, value: str) -> str:
        candidate = value.strip()
        if candidate:
            return candidate
        text = re.sub(r"\s+", " ", step_text.strip())
        patterns = [
            r"(?:go to|open|navigate to)\s+the\s+(.+?)\s+page\b",
            r"(?:go to|open|navigate to)\s+(.+?)\s+page\b",
        ]
        for pattern in patterns:
            match = re.search(pattern, text, flags=re.IGNORECASE)
            if match:
                return match.group(1).strip()
        return ""

    def _page_match_key(self, value: str) -> str:
        value = re.sub(r"\bpage\b", "", value, flags=re.IGNORECASE)
        return re.sub(r"[^a-z0-9]+", "", value.lower())

    def _is_page_state_assertion(self, step_text: str) -> bool:
        return bool(
            re.search(r"\bi\s+(?:am|should\s+be)\s+on\s+(?:the\s+)?[a-z0-9 _-]+\s+page\b", step_text, re.I)
            or re.search(r"\b(?:the\s+)?[a-z0-9 _-]+\s+page\s+(?:is\s+)?(?:displayed|visible|opened)\b", step_text, re.I)
        )

    def _requested_asserted_page_name(self, step_text: str) -> str:
        text = re.sub(r"\s+", " ", step_text.strip())
        patterns = [
            r"\bi\s+(?:am|should\s+be)\s+on\s+(?:the\s+)?(.+?)\s+page\b",
            r"\bi\s+should\s+see\s+(?:the\s+)?(.+?)\s+page\b",
            r"\b(?:the\s+)?(.+?)\s+page\s+(?:is\s+)?(?:displayed|visible|opened)\b",
        ]
        for pattern in patterns:
            match = re.search(pattern, text, flags=re.IGNORECASE)
            if match:
                return match.group(1).strip()
        return ""

    def _assert_current_page(self, page: Any, requested_page: str) -> None:
        page.wait_for_load_state("domcontentloaded", timeout=30000)
        page.wait_for_timeout(300)
        requested_key = self._page_match_key(requested_page)
        if not requested_key:
            return

        url = str(page.url or "").lower()
        title = ""
        try:
            title = str(page.title() or "").lower()
        except Exception:
            pass

        if requested_key in {"home", "homepage", "accueil"}:
            if "login" in url or "signin" in url:
                self._wait_for_login_completion(page)
                url = str(page.url or "").lower()
            if any(token in url for token in ("/home", "/portal", "/dashboard")) and "login" not in url:
                return
            if self._home_state_reached(page):
                return
            visible_signals = [
                r"\bvplm\b",
                r"\bhome\b",
                r"\baccueil\b",
                r"\bapplications\b",
                r"\bmes\s+applications\b",
                r"\blast\s+used\s+applications\b",
            ]
            if self._any_visible_text(page, visible_signals) and not self._any_visible_text(page, [r"\bsign\s+in\b", r"\busername\b", r"\bpassword\b"]):
                return
            if "login" not in url and "sign in" not in title:
                return
            raise AssertionError(f"Expected Home page, but current URL is {page.url}")

        if requested_key in {"login", "signin", "connexion"}:
            if "login" in url or "signin" in url:
                return
            if self._any_visible_text(page, [r"\bsign\s+in\b", r"\busername\b", r"\bpassword\b", r"\bconnexion\b"]):
                return
            raise AssertionError(f"Expected Login page, but current URL is {page.url}")

        page_name = re.escape(requested_page)
        if self._any_visible_text(page, [rf"\b{page_name}\b"]):
            return
        if requested_key and requested_key in self._page_match_key(url):
            return
        raise AssertionError(f"Expected {requested_page} page, but current URL is {page.url}")

    def _any_visible_text(self, page: Any, patterns: List[str]) -> bool:
        for pattern in patterns:
            try:
                locator = page.get_by_text(re.compile(pattern, re.I)).first
                if self._locator_is_visible(locator):
                    return True
            except Exception:
                pass
        return False

    def _assert_text_available(self, page: Any, value: str) -> None:
        target = str(value or "").strip()
        if not target:
            return

        if self._looks_like_results_view(page):
            self._assert_search_result_available(page, target)
            return

        locator = page.get_by_text(target, exact=False)
        try:
            locator.first.wait_for(state="visible", timeout=4000)
            return
        except Exception:
            pass

        try:
            count = locator.count()
            for index in range(min(count, 10)):
                candidate = locator.nth(index)
                try:
                    candidate.scroll_into_view_if_needed(timeout=1000)
                    if self._locator_is_visible(candidate):
                        return
                except Exception:
                    continue
        except Exception:
            pass

        if self._text_exists_in_dom(page, target):
            return

        if self._normalized_text_exists_in_dom(page, target):
            return

        visible_text = self._visible_text_snapshot(page)
        raise AssertionError(
            f"Expected text '{target}' on the page, but it was not found. "
            f"Current URL is {page.url}. Visible text sample: {visible_text}"
        )

    def _is_search_result_assertion(self, step_text: str) -> bool:
        lower = str(step_text or "").lower()
        return bool(re.search(r"\b(search|recherche)\b", lower)) and bool(
            re.search(r"\b(result|results|résultat|resultat|résultats|resultats|grid|grille)\b", lower)
        )

    def _assert_search_result_available(self, page: Any, value: str) -> None:
        target = str(value or "").strip()
        if not target:
            self._assert_results_table_ready(page)
            return
        timeout_ms = int(os.getenv("E2E_SEARCH_RESULT_TIMEOUT_MS", "20000"))
        deadline = time.time() + timeout_ms / 1000
        while time.time() < deadline:
            result_state = self._search_results_state(page, target)
            if result_state.get("has_table") and result_state.get("has_rows") and result_state.get("target_found"):
                self._record_search_result_evidence(page, target, result_state)
                return
            if result_state.get("empty"):
                break
            try:
                page.wait_for_load_state("networkidle", timeout=1200)
            except Exception:
                pass
            try:
                page.wait_for_timeout(600)
            except Exception:
                pass
        result_state = self._search_results_state(page, target)
        visible_text = self._visible_text_snapshot(page)
        if not result_state.get("has_table"):
            raise AssertionError(
                f"Expected a search results table/grid before validating '{target}', but no visible results table was found. "
                f"Current URL is {page.url}. Visible text sample: {visible_text}"
            )
        if result_state.get("empty"):
            raise AssertionError(
                f"Expected search result '{target}', but the visible results table is empty. "
                f"Current URL is {page.url}. Visible text sample: {visible_text}"
            )
        raise AssertionError(
            f"Expected search result '{target}' inside the visible results table/grid, but it was not found in result rows. "
            f"Rows detected: {result_state.get('row_count', 0)}. "
            f"Current URL is {page.url}. Visible text sample: {visible_text}"
        )

    def _assert_results_table_ready(self, page: Any) -> None:
        timeout_ms = int(os.getenv("E2E_SEARCH_RESULT_TIMEOUT_MS", "20000"))
        deadline = time.time() + timeout_ms / 1000
        last_state: Dict[str, Any] = {}
        while time.time() < deadline:
            last_state = self._search_results_state(page, "")
            if last_state.get("has_table") and last_state.get("has_rows"):
                self._record_search_result_evidence(page, "", last_state)
                return
            if last_state.get("empty"):
                break
            try:
                page.wait_for_load_state("networkidle", timeout=1200)
            except Exception:
                pass
            try:
                page.wait_for_timeout(600)
            except Exception:
                pass
        visible_text = self._visible_text_snapshot(page)
        if not last_state.get("has_table"):
            raise AssertionError(
                f"Expected a visible search results table/grid, but none was found. "
                f"Current URL is {page.url}. Visible text sample: {visible_text}"
            )
        raise AssertionError(
            f"Expected at least one row in the search results table/grid, but no result row was found. "
            f"Current URL is {page.url}. Visible text sample: {visible_text}"
        )

    def _text_exists_in_result_area(self, page: Any, value: str) -> bool:
        state = self._search_results_state(page, value)
        return bool(state.get("has_table") and state.get("has_rows") and state.get("target_found"))

    def _looks_like_results_view(self, page: Any) -> bool:
        try:
            url = str(page.url or "").lower()
        except Exception:
            url = ""
        if any(token in url for token in ("/portal/home", "/portal/login", "/search/home", "/search/create")):
            return False
        state = self._search_results_state(page, "")
        if any(token in url for token in ("/search/result", "/search/results")):
            return bool(state.get("has_table") and (state.get("has_rows") or state.get("empty")))
        try:
            body_text = page.locator("body").inner_text(timeout=1200)
        except Exception:
            body_text = ""
        has_results_heading = bool(re.search(r"(^|\n)\s*R[ée]sultat[s]?\s*(\n|$)", body_text, re.I))
        has_results_chrome = bool(re.search(r"\b(objets?\s+par\s+page|Sauvegarder\s+la\s+recherche)\b", body_text, re.I))
        if has_results_heading and has_results_chrome:
            return bool(state.get("has_table") and (state.get("has_rows") or state.get("empty")))
        return False

    def _search_results_state(self, page: Any, value: str = "") -> Dict[str, Any]:
        script = """
        (target) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/[\\u00a0\\u200b\\u200c\\u200d]/g, " ")
            .replace(/\\s+/g, " ")
            .trim()
            .toLowerCase();
          const needle = normalize(target);
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const isControl = (element) => {
            if (!element) return false;
            const tag = element.tagName.toLowerCase();
            return ["input", "textarea", "select", "button"].includes(tag)
              || ["button", "combobox", "textbox", "searchbox", "listbox", "option"].includes((element.getAttribute("role") || "").toLowerCase());
          };
          const stripControls = (root) => {
            const clone = root.cloneNode(true);
            clone.querySelectorAll("input,textarea,select,button,[role='button'],[role='combobox'],[role='textbox'],[role='searchbox'],[role='listbox'],[role='option'],mat-select,.mat-mdc-select,.mat-select").forEach((element) => element.remove());
            return normalize(clone.innerText || clone.textContent || "");
          };
          const rowText = (row) => {
            const cells = Array.from(row.querySelectorAll("[role='gridcell'],[role='cell'],td,.ag-cell,.mat-mdc-cell,.mat-cell,.k-table-td,.datatable-body-cell,.dx-datagrid-text-content,span,div"))
              .filter(isVisible)
              .filter((element) => !isControl(element) && !element.closest("button,[role='button'],mat-select,.mat-mdc-select,.mat-select"));
            const text = cells.length
              ? cells.map((cell) => cell.innerText || cell.textContent || "").join(" ")
              : row.innerText || row.textContent || "";
            return normalize(text);
          };
          const selectors = [
            "[role='grid']",
            "[role='table']",
            "table",
            ".ag-root",
            ".ag-center-cols-container",
            ".mat-mdc-table",
            ".mat-table",
            ".cdk-table",
            ".k-grid",
            ".datatable",
            ".ngx-datatable",
            ".dx-datagrid",
            ".MuiDataGrid-root",
            ".cdk-virtual-scroll-content-wrapper",
            "[class*='grid']",
            "[class*='Grid']",
            "[class*='table']",
            "[class*='Table']",
            ".results",
            ".search-results"
          ];
          const emptyPattern = /(aucun\\s+(objet|resultat|résultat|element|élément)|no\\s+(result|results|data|items?)|0\\s+(objet|result|résultat|element|élément))/i;
          const areas = Array.from(document.querySelectorAll(selectors.join(",")))
            .filter(isVisible)
            .map((area) => {
              const rect = area.getBoundingClientRect();
              const rows = Array.from(area.querySelectorAll(
                "tbody tr,tr,[role='row'],.ag-row,.mat-mdc-row,.mat-row,.cdk-row,.k-table-row,.datatable-body-row,.datatable-row-wrapper,.dx-row,.MuiDataGrid-row,[class*='row'],[class*='Row']"
              )).filter(isVisible).filter((row) => {
                const text = rowText(row);
                if (!text) return false;
                if (/^(classe|reference|référence|version|revision|révision|designation|désignation|createur|créateur|datecreation|datecréation|toutselectionner|toutselectionner)$/.test(text)) return false;
                return !row.closest("thead,[role='columnheader'],.ag-header,.mat-mdc-header-row,.mat-header-row,.k-grid-header");
              });
              const directCells = Array.from(area.querySelectorAll(
                "[role='gridcell'],[role='cell'],td,.ag-cell,.mat-mdc-cell,.mat-cell,.cdk-cell,.k-table-td,.datatable-body-cell,.MuiDataGrid-cell,[class*='cell'],[class*='Cell']"
              )).filter(isVisible).filter((cell) => !cell.closest("thead,[role='columnheader'],.ag-header,.mat-mdc-header-row,.mat-header-row,.k-grid-header"));
              const areaText = stripControls(area);
              const fallbackLooksLikeResultTable =
                /(classe|reference|référence|version|revision|révision|designation|désignation|statut)/i.test(area.innerText || area.textContent || "")
                && rect.width > window.innerWidth * 0.45
                && rect.height > 120;
              const candidateTexts = rows.length
                ? rows.map(rowText)
                : directCells.length
                  ? directCells.map((cell) => normalize(cell.innerText || cell.textContent || ""))
                  : fallbackLooksLikeResultTable
                    ? [areaText]
                    : [];
              const targetFound = !!needle && candidateTexts.some((text) => text.includes(needle));
              return {
                hasTable: true,
                rowCount: rows.length || (directCells.length ? 1 : fallbackLooksLikeResultTable ? 1 : 0),
                cellCount: directCells.length,
                targetFound,
                empty: emptyPattern.test(area.innerText || area.textContent || ""),
                score: (rows.length * 10) + directCells.length + rect.width / 100 + rect.height / 100,
                sample: candidateTexts.slice(0, 5).join(" | ") || areaText.slice(0, 300)
              };
            })
            .sort((a, b) => b.score - a.score);
          const best = areas[0] || null;
          const bodyText = document.body ? (document.body.innerText || document.body.textContent || "") : "";
          if (!best) {
            return {
              has_table: false,
              has_rows: false,
              row_count: 0,
              cell_count: 0,
              target_found: false,
              empty: emptyPattern.test(bodyText),
              sample: normalize(bodyText).slice(0, 300)
            };
          }
          return {
            has_table: true,
            has_rows: best.rowCount > 0,
            row_count: best.rowCount,
            cell_count: best.cellCount,
            target_found: best.targetFound || (!needle && best.rowCount > 0),
            empty: best.empty,
            sample: best.sample
          };
        }
        """
        try:
            result = page.evaluate(script, value)
            return result if isinstance(result, dict) else {}
        except Exception:
            return {}

    def _record_search_result_evidence(self, page: Any, target: str, result_state: Dict[str, Any]) -> None:
        if os.getenv("E2E_SEARCH_RESULT_EVIDENCE", "true").lower() in {"false", "0", "no"}:
            return
        if not result_state.get("has_table"):
            return

        try:
            url = str(page.url or "")
        except Exception:
            url = ""
        sample = str(result_state.get("sample") or "")
        capture_key = hashlib.sha1(
            f"{url}|{result_state.get('row_count', 0)}|{sample[:160]}".encode("utf-8", errors="ignore")
        ).hexdigest()

        evidence_items = getattr(self, "_search_result_evidence", [])
        for item in evidence_items:
            if item.get("capture_key") == capture_key:
                targets = item.setdefault("validated_targets", [])
                if target and target not in targets:
                    targets.append(target)
                return

        capture_root_key = f"execution-artifacts/search-results/{capture_key}"
        screenshots = self._capture_search_table_screenshots(page, capture_root_key)
        evidence_items.append(
            {
                "type": "search_results_table",
                "capture_key": capture_key,
                "scenario": getattr(self, "_current_scenario_name", ""),
                "url": url,
                "validated_targets": [target] if target else [],
                "analysis": {
                    "table_detected": bool(result_state.get("has_table")),
                    "rows_detected": int(result_state.get("row_count") or 0),
                    "cells_detected": int(result_state.get("cell_count") or 0),
                    "target_found": bool(result_state.get("target_found")),
                    "empty_result": bool(result_state.get("empty")),
                    "visible_row_sample": sample[:500],
                    "screenshots_captured": len(screenshots),
                },
                "screenshots": screenshots,
            }
        )
        self._search_result_evidence = evidence_items

    def _capture_search_table_screenshots(self, page: Any, capture_root_key: str) -> List[Dict[str, Any]]:
        max_screenshots = max(1, int(os.getenv("E2E_SEARCH_TABLE_SCREENSHOT_MAX", "8")))
        evidence_timeout_ms = max(1000, int(os.getenv("E2E_SEARCH_TABLE_SCREENSHOT_TIMEOUT_MS", "5000")))
        area = self._best_search_results_area(page)
        if not area.get("area_selector"):
            return []

        scroll_selector = str(area.get("scroll_selector") or area.get("area_selector") or "")
        max_left = max(0, int(float(area.get("max_scroll_left") or 0)))
        max_top = max(0, int(float(area.get("max_scroll_top") or 0)))
        client_width = max(1, int(float(area.get("client_width") or 1)))
        client_height = max(1, int(float(area.get("client_height") or 1)))

        top_step = max(1, int(client_height * 0.85))
        left_step = max(1, int(client_width * 0.85))
        top_positions = self._scroll_positions(max_top, top_step)
        left_positions = self._scroll_positions(max_left, left_step)

        positions: List[Dict[str, int]] = []
        for top in top_positions:
            for left in left_positions:
                positions.append({"left": left, "top": top})
        total_positions = len(positions)
        positions = positions[:max_screenshots]

        original = self._scroll_position(page, scroll_selector)
        screenshots: List[Dict[str, Any]] = []
        seen_scroll_positions: set[tuple[int, int]] = set()
        root = Path(os.getenv("STEP_FUNCTION_STORAGE_ROOT", "/app/output"))
        try:
            for index, position in enumerate(positions, start=1):
                self._set_scroll_position(page, scroll_selector, position["left"], position["top"])
                try:
                    page.wait_for_timeout(180)
                except Exception:
                    pass
                actual_position = self._scroll_position(page, scroll_selector)
                actual_key = (int(actual_position.get("left") or 0), int(actual_position.get("top") or 0))
                if actual_key in seen_scroll_positions:
                    continue
                seen_scroll_positions.add(actual_key)
                clip = self._search_result_capture_clip(page, area)
                if not clip:
                    continue
                object_key = f"{capture_root_key}/table-part-{index}.png"
                path = root / object_key
                path.parent.mkdir(parents=True, exist_ok=True)
                page.screenshot(path=str(path), clip=clip, timeout=evidence_timeout_ms)
                screenshots.append(
                    {
                        "url": self._artifact_public_url(object_key),
                        "local_path": str(path),
                        "part": index,
                        "scroll_left": actual_key[0],
                        "scroll_top": actual_key[1],
                        "truncated": total_positions > max_screenshots,
                    }
                )
        except Exception as exc:
            screenshots.append(
                {
                    "error": f"Search result screenshot capture failed: {exc}",
                    "truncated": total_positions > max_screenshots,
                }
            )
        finally:
            if original:
                self._set_scroll_position(
                    page,
                    scroll_selector,
                    int(original.get("left") or 0),
                    int(original.get("top") or 0),
                )
        return screenshots

    def _scroll_positions(self, max_value: int, step: int) -> List[int]:
        if max_value <= 0:
            return [0]
        positions = [0]
        current = step
        while current < max_value:
            positions.append(current)
            current += step
        if positions[-1] != max_value:
            positions.append(max_value)
        return positions

    def _best_search_results_area(self, page: Any) -> Dict[str, Any]:
        script = """
        () => {
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const rowText = (row) => String(row.innerText || row.textContent || "").replace(/\\s+/g, " ").trim();
          const selectors = [
            "[role='grid']",
            "[role='table']",
            "table",
            ".ag-root",
            ".ag-center-cols-container",
            ".mat-mdc-table",
            ".mat-table",
            ".cdk-table",
            ".k-grid",
            ".datatable",
            ".ngx-datatable",
            ".dx-datagrid",
            ".MuiDataGrid-root",
            ".cdk-virtual-scroll-content-wrapper",
            "[class*='grid']",
            "[class*='Grid']",
            "[class*='table']",
            "[class*='Table']",
            ".results",
            ".search-results"
          ];
          const candidates = Array.from(document.querySelectorAll(selectors.join(",")))
            .filter(isVisible)
            .map((area) => {
              const rect = area.getBoundingClientRect();
              const rows = Array.from(area.querySelectorAll(
                "tbody tr,tr,[role='row'],.ag-row,.mat-mdc-row,.mat-row,.cdk-row,.k-table-row,.datatable-body-row,.datatable-row-wrapper,.dx-row,.MuiDataGrid-row,[class*='row'],[class*='Row']"
              )).filter(isVisible).filter((row) => {
                const text = rowText(row);
                if (!text) return false;
                return !row.closest("thead,[role='columnheader'],.ag-header,.mat-mdc-header-row,.mat-header-row,.k-grid-header");
              });
              const cells = Array.from(area.querySelectorAll(
                "[role='gridcell'],[role='cell'],td,.ag-cell,.mat-mdc-cell,.mat-cell,.cdk-cell,.k-table-td,.datatable-body-cell,.MuiDataGrid-cell,[class*='cell'],[class*='Cell']"
              )).filter(isVisible).filter((cell) => !cell.closest("thead,[role='columnheader'],.ag-header,.mat-mdc-header-row,.mat-header-row,.k-grid-header"));
              const text = area.innerText || area.textContent || "";
              const hasResultHeaders = /(classe|reference|référence|version|revision|révision|designation|désignation|statut)/i.test(text);
              const score = (rows.length * 12) + cells.length + (hasResultHeaders ? 30 : 0) + rect.width / 80 + rect.height / 120;
              return { area, rect, rows: rows.length, cells: cells.length, score };
            })
            .filter((item) => item.rows > 0 || item.cells > 0)
            .sort((a, b) => b.score - a.score);
          const best = candidates[0];
          if (!best) return {};

          const token = `vplmqa-results-${Date.now()}-${Math.floor(Math.random() * 1000000)}`;
          best.area.setAttribute("data-vplmqa-results-area", token);
          const ancestors = [];
          let parent = best.area.parentElement;
          while (parent && parent !== document.body && ancestors.length < 6) {
            ancestors.push(parent);
            parent = parent.parentElement;
          }
          const scrollables = [best.area, ...Array.from(best.area.querySelectorAll("*")), ...ancestors]
            .filter(isVisible)
            .filter((element) => element.scrollWidth > element.clientWidth + 8 || element.scrollHeight > element.clientHeight + 8)
            .map((element) => {
              const xOverflow = Math.max(0, element.scrollWidth - element.clientWidth);
              const yOverflow = Math.max(0, element.scrollHeight - element.clientHeight);
              const horizontalPriority = xOverflow > 8 ? 10000 : 0;
              const areaBonus = element === best.area ? 80 : element.contains(best.area) ? 50 : 0;
              return {
                element,
                score: horizontalPriority + xOverflow * 4 + yOverflow + areaBonus
              };
            })
            .sort((a, b) => b.score - a.score);
          const scroller = scrollables[0]?.element || best.area;
          scroller.setAttribute("data-vplmqa-results-scroll", token);
          const rect = best.area.getBoundingClientRect();
          return {
            area_selector: `[data-vplmqa-results-area="${token}"]`,
            scroll_selector: `[data-vplmqa-results-scroll="${token}"]`,
            scroll_width: scroller.scrollWidth,
            scroll_height: scroller.scrollHeight,
            client_width: scroller.clientWidth,
            client_height: scroller.clientHeight,
            max_scroll_left: Math.max(0, scroller.scrollWidth - scroller.clientWidth),
            max_scroll_top: Math.max(0, scroller.scrollHeight - scroller.clientHeight),
            rect: { x: rect.x, y: rect.y, width: rect.width, height: rect.height }
          };
        }
        """
        try:
            result = page.evaluate(script)
            return result if isinstance(result, dict) else {}
        except Exception:
            return {}

    def _scroll_position(self, page: Any, selector: str) -> Dict[str, int]:
        script = """
        (selector) => {
          const element = document.querySelector(selector);
          if (!element) return { left: 0, top: 0 };
          return { left: element.scrollLeft || 0, top: element.scrollTop || 0 };
        }
        """
        try:
            result = page.evaluate(script, selector)
            return result if isinstance(result, dict) else {"left": 0, "top": 0}
        except Exception:
            return {"left": 0, "top": 0}

    def _set_scroll_position(self, page: Any, selector: str, left: int, top: int) -> None:
        script = """
        (data) => {
          const element = document.querySelector(data.selector);
          if (!element) return;
          element.scrollLeft = data.left;
          element.scrollTop = data.top;
        }
        """
        try:
            page.evaluate(script, {"selector": selector, "left": left, "top": top})
        except Exception:
            pass

    def _search_result_capture_clip(self, page: Any, area: Dict[str, Any]) -> Optional[Dict[str, float]]:
        rect = area.get("rect") if isinstance(area.get("rect"), dict) else {}
        try:
            viewport = page.viewport_size or {"width": 1366, "height": 768}
        except Exception:
            viewport = {"width": 1366, "height": 768}
        x = max(0.0, float(rect.get("x") or 0))
        y = max(0.0, float(rect.get("y") or 0))
        viewport_width = float(viewport.get("width") or 1366)
        viewport_height = float(viewport.get("height") or 768)
        width = min(float(rect.get("width") or viewport_width), viewport_width - x)
        height = min(float(rect.get("height") or viewport_height), viewport_height - y)
        width = min(width, float(os.getenv("E2E_SEARCH_TABLE_SCREENSHOT_MAX_WIDTH", "1600")))
        height = min(height, float(os.getenv("E2E_SEARCH_TABLE_SCREENSHOT_MAX_HEIGHT", "900")))
        if width < 20 or height < 20:
            return None
        return {"x": x, "y": y, "width": width, "height": height}

    def _artifact_public_url(self, object_key: str) -> str:
        public_base_url = os.getenv("E2E_ARTIFACT_PUBLIC_BASE_URL", "http://localhost:8090/artifacts").rstrip("/")
        return f"{public_base_url}/{object_key.replace(os.sep, '/')}"

    def _text_exists_in_dom(self, page: Any, value: str) -> bool:
        script = """
        (target) => {
          const normalize = (text) => String(text || "").replace(/\\s+/g, " ").trim().toLowerCase();
          const needle = normalize(target);
          if (!needle) return true;
          const body = document.body;
          if (!body) return false;
          const fullText = normalize(body.innerText || body.textContent || "");
          if (fullText.includes(needle)) return true;
          return Array.from(document.querySelectorAll("body *")).some((element) => {
            const text = normalize(element.innerText || element.textContent || "");
            return text === needle || text.includes(needle);
          });
        }
        """
        try:
            return bool(page.evaluate(script, value))
        except Exception:
            return False

    def _normalized_text_exists_in_dom(self, page: Any, value: str) -> bool:
        script = """
        (target) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/[\\u00a0\\u200b\\u200c\\u200d]/g, " ")
            .replace(/[’']/g, "'")
            .replace(/\\s+/g, " ")
            .trim()
            .toLowerCase();
          const needle = normalize(target);
          if (!needle) return true;
          const body = document.body;
          if (!body) return false;
          const haystack = normalize(body.innerText || body.textContent || "");
          return haystack.includes(needle);
        }
        """
        try:
            return bool(page.evaluate(script, value))
        except Exception:
            return False

    def _visible_text_snapshot(self, page: Any) -> str:
        script = """
        () => {
          const text = document.body ? (document.body.innerText || "") : "";
          return text.replace(/\\s+/g, " ").trim().slice(0, 300);
        }
        """
        try:
            return str(page.evaluate(script) or "")
        except Exception:
            return ""

    def _is_web_page(self, page: Dict[str, Any]) -> bool:
        source = str(page.get("source") or "").upper()
        url = str(page.get("url") or "").strip()
        web_object = str(page.get("webObjectPath") or "").strip()
        figma_object = str(page.get("figmaObjectPath") or "").strip()
        return source == "WEB" or bool(url or web_object) and not figma_object

    def _host_browser_url(self, url: str) -> str:
        return url

    def _resolve_component(
        self,
        step_text: str,
        catalog: List[Dict[str, Any]],
        project_context: Optional[Dict[str, Any]] = None,
    ) -> Optional[Dict[str, Any]]:
        step_tokens = set(re.sub(r"[^a-z0-9]+", " ", step_text.lower()).split())
        active_page_id = str((project_context or {}).get("activePageId") or "")
        scoped_catalog = [
            component for component in catalog
            if not active_page_id or str(
                component.get("pageId") or (component.get("page") or {}).get("id") or ""
            ) == active_page_id
        ]
        best = None
        best_score = 0
        for component in scoped_catalog:
            role = str(component.get("role") or component.get("semanticRole") or "").lower()
            if any(term in step_text for term in ("enter ", "fill ", "type ")) and role not in ("input", "textbox"):
                continue
            if any(term in step_text for term in ("click", "press", "submit", "choose")) and role in (
                "input", "text", "heading", "container", "form"
            ):
                continue
            identity = " ".join(str(component.get(key) or "") for key in (
                "canonicalName", "uniqueName", "htmlId", "testIdentifier",
            )).lower()
            aliases = " ".join(str(item or "") for item in component.get("actionAliases", [])).lower()
            hints = component.get("e2eHints") if isinstance(component.get("e2eHints"), dict) else {}
            hint_text = " ".join(
                str(value or "")
                for value in (
                    hints.get("primaryIntent"),
                    " ".join(hints.get("actionAliases") or []),
                    " ".join(hints.get("naturalStepExamples") or []),
                )
            ).lower()
            semantics = " ".join(str(component.get(key) or "") for key in (
                "semanticRole", "role", "purpose",
            )).lower()
            context = " ".join(str(component.get(key) or "") for key in (
                "functionalMeaning", "usage", "expectedOutcome", "navigationDestination",
            )).lower()
            identity_tokens = set(re.sub(r"[^a-z0-9]+", " ", identity).split())
            alias_tokens = set(re.sub(r"[^a-z0-9]+", " ", aliases).split())
            hint_tokens = set(re.sub(r"[^a-z0-9]+", " ", hint_text).split())
            semantic_tokens = set(re.sub(r"[^a-z0-9]+", " ", semantics).split())
            context_tokens = set(re.sub(r"[^a-z0-9]+", " ", context).split())
            score = (
                6 * len(step_tokens & identity_tokens)
                + 8 * len(step_tokens & alias_tokens)
                + 5 * len(step_tokens & hint_tokens)
                + 2 * len(step_tokens & semantic_tokens)
                + len(step_tokens & context_tokens)
            )
            if score > best_score:
                best = component
                best_score = score
        return best if best_score > 0 else None

    def _component_locator(self, page: Any, component: Dict[str, Any]) -> Any:
        locator_sources: List[Any] = []
        preferred = component.get("preferredLocator") or {}
        if isinstance(preferred, dict):
            locator_sources.append(preferred)
        elif preferred:
            locator_sources.append({"strategy": "css", "value": str(preferred)})
        for key in ("locatorCandidates", "locatorAlternatives"):
            values = component.get(key)
            if isinstance(values, list):
                locator_sources.extend(item for item in values if isinstance(item, dict))
        hints = component.get("e2eHints") if isinstance(component.get("e2eHints"), dict) else {}
        if isinstance(hints.get("locatorCandidates"), list):
            locator_sources.extend(item for item in hints["locatorCandidates"] if isinstance(item, dict))

        for locator_source in locator_sources:
            candidate = self._locator_from_strategy(page, locator_source)
            if candidate is not None and candidate.count() > 0:
                return candidate.first
        test_id = str(component.get("testIdentifier") or "").strip()
        html_id = str(component.get("htmlId") or "").strip()
        css = str(component.get("cssSelector") or "").strip()
        xpath = str(component.get("xpath") or "").strip()
        if test_id:
            candidate = page.get_by_test_id(test_id)
            if candidate.count() > 0:
                return candidate.first
        if html_id:
            candidate = page.locator(f"#{html_id}")
            if candidate.count() > 0:
                return candidate.first
        if css:
            candidate = page.locator(f"xpath={css}" if css.startswith("/") else css)
            if candidate.count() > 0:
                return candidate.first
        if xpath:
            return page.locator(f"xpath={xpath}").first
        raise AssertionError(f"No usable locator persisted for {component.get('uniqueName') or component.get('canonicalName', 'component')}")

    def _locator_from_strategy(self, page: Any, source: Dict[str, Any]) -> Optional[Any]:
        strategy = str(source.get("strategy") or "").lower()
        value = str(source.get("value") or "").strip()
        if not value:
            return None
        try:
            if strategy in {"testid", "test-id", "data-testid"}:
                return page.get_by_test_id(value)
            if strategy == "id":
                return page.locator(f"#{value}")
            if strategy == "xpath" or value.startswith("/"):
                return page.locator(f"xpath={value}")
            if strategy == "role":
                role_match = re.match(r"([a-z]+)\[name=/(.*?)/([a-z]*)\]", value, re.I)
                if role_match:
                    return page.get_by_role(
                        role_match.group(1),
                        name=re.compile(role_match.group(2), re.I),
                    )
                return None
            if strategy == "text":
                return page.get_by_text(value)
            if strategy == "aria":
                return page.locator(f"[aria-label*='{value}' i]")
            if strategy == "visual":
                return None
            return page.locator(value)
        except Exception:
            return None

    def _live_dom_locator_by_intent(self, page: Any, step_text: str, expected_kind: str) -> Optional[Any]:
        candidates = self._extract_live_dom_candidates(page)
        if not candidates:
            return None

        step_tokens = self._intent_tokens(step_text)
        best: Optional[Dict[str, Any]] = None
        best_score = 0
        for candidate in candidates:
            if expected_kind == "button" and candidate.get("kind") not in {"button", "link", "option"}:
                continue
            if expected_kind == "input" and candidate.get("kind") not in {"input", "textarea", "combobox"}:
                continue
            if expected_kind == "input" and str(candidate.get("type") or "").lower() in {
                "checkbox", "radio", "hidden", "button", "submit", "reset", "file"
            }:
                continue

            candidate_text = " ".join(
                str(candidate.get(key) or "")
                for key in (
                    "id", "name", "text", "ariaLabel", "placeholder", "title",
                    "labelText", "contextText", "role", "kind", "type",
                )
            )
            candidate_tokens = self._intent_tokens(candidate_text)
            score = 3 * len(step_tokens & candidate_tokens)

            lower_step = step_text.lower()
            lower_candidate = candidate_text.lower()
            if expected_kind == "button" and any(token in lower_step for token in ("login", "log in", "sign in", "submit")):
                if any(token in lower_candidate for token in ("login", "connexion", "connect", "submit", "sign in")):
                    score += 12
                if candidate.get("type") == "submit":
                    score += 5
            if expected_kind == "input" and "password" in lower_step and candidate.get("type") == "password":
                score += 12
            if expected_kind == "input" and any(token in lower_step for token in ("username", "login", "user")):
                if any(token in lower_candidate for token in ("username", "login", "user", "identifiant")):
                    score += 10
            if candidate.get("id"):
                score += 2
            if candidate.get("visible"):
                score += 3
            if candidate.get("disabled"):
                score -= 20

            if score > best_score:
                best = candidate
                best_score = score

        if not best or best_score <= 0:
            return None

        selector = str(best.get("selector") or "")
        if not selector:
            return None
        return page.locator(selector).first

    def _extract_live_dom_candidates(self, page: Any) -> List[Dict[str, Any]]:
        script = r"""
        () => {
          const cssEscape = (value) => {
            if (window.CSS && CSS.escape) return CSS.escape(value);
            return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\$&");
          };
          let runtimeId = 0;
          const selectorFor = (element) => {
            if (element.id) return `#${cssEscape(element.id)}`;
            const testId = element.getAttribute("data-testid") || element.getAttribute("data-test-id");
            if (testId) return `[data-testid="${testId.replace(/"/g, '\\"')}"]`;
            const existing = element.getAttribute("data-vplmqa-runtime-id");
            if (existing) return `[data-vplmqa-runtime-id="${existing}"]`;
            const assigned = `runtime-${Date.now()}-${runtimeId++}`;
            element.setAttribute("data-vplmqa-runtime-id", assigned);
            return `[data-vplmqa-runtime-id="${assigned}"]`;
          };
          const nodes = Array.from(document.querySelectorAll([
            "button",
            "a[href]",
            "input",
            "textarea",
            "select",
            "[role='button']",
            "[role='link']",
            "[role='option']",
            "[role='combobox']",
            ".mat-mdc-button-base",
            ".mat-mdc-option",
            ".mat-mdc-select",
            ".mdc-button"
          ].join(",")));
          const nearbyText = (element) => {
            let current = element;
            const parts = [];
            for (let depth = 0; current && depth < 5; depth += 1) {
              parts.push(current.innerText || current.textContent || "");
              current = current.parentElement;
            }
            return parts.join(" ").replace(/\s+/g, " ").trim().slice(0, 400);
          };
          const labelText = (element) => {
            const id = element.id;
            const labelledBy = (element.getAttribute("aria-labelledby") || "")
              .split(/\s+/)
              .map((item) => document.getElementById(item)?.innerText || document.getElementById(item)?.textContent || "")
              .join(" ");
            const explicit = id ? Array.from(document.querySelectorAll(`label[for="${cssEscape(id)}"]`))
              .map((label) => label.innerText || label.textContent || "")
              .join(" ") : "";
            const field = element.closest("mat-form-field,.mat-mdc-form-field,.mat-form-field,.form-group,.field");
            const floating = field ? Array.from(field.querySelectorAll("label,mat-label,.mat-mdc-floating-label,.mat-form-field-label,.mdc-floating-label,legend"))
              .map((label) => label.innerText || label.textContent || "")
              .join(" ") : "";
            return [labelledBy, explicit, floating].join(" ").replace(/\s+/g, " ").trim();
          };
          return nodes.slice(0, 250).map((element) => {
            const rect = element.getBoundingClientRect();
            const tag = element.tagName.toLowerCase();
            const role = element.getAttribute("role") || "";
            const type = (element.getAttribute("type") || "").toLowerCase();
            let kind = tag;
            if (tag === "button" || role === "button" || element.classList.contains("mdc-button")) kind = "button";
            else if (tag === "a" || role === "link") kind = "link";
            else if (role === "option") kind = "option";
            else if (role === "combobox" || tag === "select" || element.classList.contains("mat-mdc-select")) kind = "combobox";
            else if (tag === "textarea") kind = "textarea";
            else if (tag === "input") kind = "input";
            return {
              selector: selectorFor(element),
              id: element.id || "",
              name: element.getAttribute("name") || "",
              text: (element.innerText || element.textContent || "").replace(/\s+/g, " ").trim(),
              ariaLabel: element.getAttribute("aria-label") || "",
              placeholder: element.getAttribute("placeholder") || "",
              title: element.getAttribute("title") || "",
              labelText: labelText(element),
              contextText: nearbyText(element),
              role,
              kind,
              type,
              visible: rect.width > 0 && rect.height > 0,
              disabled: Boolean(element.disabled)
                || element.getAttribute("aria-disabled") === "true"
                || (tag === "input" && ["hidden", "checkbox", "radio", "button", "submit", "reset", "file"].includes(type))
            };
          }).filter((item) => item.selector && item.visible);
        }
        """
        try:
            result = page.evaluate(script)
            return result if isinstance(result, list) else []
        except Exception:
            return []

    def _intent_tokens(self, value: str) -> set[str]:
        normalized = unicodedata.normalize("NFD", str(value or ""))
        normalized = "".join(ch for ch in normalized if unicodedata.category(ch) != "Mn")
        tokens = set(re.sub(r"[^a-z0-9]+", " ", normalized.lower()).split())
        aliases = {
            "connexion": "login",
            "connect": "login",
            "connection": "login",
            "signin": "login",
            "username": "login",
            "identifiant": "login",
            "poste": "workstation",
            "database": "base",
            "createur": "creator",
            "operateur": "operator",
            "critere": "criterion",
            "reference": "ref",
        }
        expanded = set(tokens)
        for token in tokens:
            if token in aliases:
                expanded.add(aliases[token])
        return expanded

    def _safe_click(
        self,
        page: Any,
        locator: Any,
        timeout: int = 30000,
        dismiss_overlays: bool = True,
    ) -> None:
        locator.scroll_into_view_if_needed(timeout=10000)
        if dismiss_overlays:
            self._dismiss_transient_overlays(page)
        try:
            locator.click(timeout=timeout)
            self._post_action_settle(page)
            return
        except Exception as exc:
            message = str(exc).lower()
            if "intercepts pointer events" not in message and "timeout" not in message:
                raise
            if dismiss_overlays:
                self._dismiss_transient_overlays(page, force_escape=True)
            try:
                locator.click(timeout=8000)
                self._post_action_settle(page)
                return
            except Exception:
                try:
                    locator.click(timeout=8000, force=True)
                    self._post_action_settle(page)
                    return
                except Exception:
                    try:
                        locator.evaluate("(element) => element.click()")
                        self._post_action_settle(page)
                        return
                    except Exception:
                        raise exc

    def _post_action_settle(self, page: Any) -> None:
        try:
            page.wait_for_load_state("domcontentloaded", timeout=2500)
        except Exception:
            pass
        try:
            page.wait_for_load_state("networkidle", timeout=1200)
        except Exception:
            pass
        try:
            page.wait_for_timeout(int(os.getenv("E2E_POST_CLICK_SETTLE_MS", "250")))
        except Exception:
            pass

    def _dismiss_blocking_plm_dialog(self, page: Any) -> None:
        try:
            dialog_text = page.get_by_text(re.compile(r"Fonctionnement enrichi indisponible", re.I))
            if dialog_text.count() <= 0 or not self._locator_is_visible(dialog_text.first):
                return
            button = page.get_by_role("button", name=re.compile(r"J['’]ai compris", re.I))
            if button.count() > 0 and self._locator_is_visible(button.first):
                self._safe_click(page, button.first, timeout=3000, dismiss_overlays=False)
                page.wait_for_timeout(300)
        except Exception:
            pass

    def _dismiss_transient_overlays(self, page: Any, force_escape: bool = False) -> None:
        overlay_selector = (
            ".cdk-overlay-backdrop-showing, "
            ".cdk-overlay-pane [role='listbox'], "
            ".mat-mdc-select-panel, "
            ".mat-select-panel, "
            ".MuiPopover-root"
        )
        try:
            overlay = page.locator(overlay_selector)
            if force_escape or overlay.count() > 0:
                page.keyboard.press("Escape")
                page.wait_for_timeout(250)
            backdrop = page.locator(".cdk-overlay-backdrop-showing, .MuiBackdrop-root")
            if backdrop.count() > 0:
                backdrop.first.wait_for(state="hidden", timeout=2000)
        except Exception:
            try:
                page.keyboard.press("Escape")
                page.wait_for_timeout(250)
            except Exception:
                pass

    def _search(self, page: Any, query: str) -> None:
        self._fill_search_field(page, query)
        page.keyboard.press("Enter")
        self._wait_after_search_submit(page)

    def _is_search_field_intent(self, step_text: str) -> bool:
        lower = str(step_text or "").lower()
        return bool(re.search(r"\b(search|recherche)\b", lower)) and bool(re.search(r"\b(field|input|champ|bar|barre)\b", lower))

    def _is_direct_search_action(self, step_text: str) -> bool:
        lower = str(step_text or "").lower()
        if lower.startswith(("then ", "and i should", "but i should")):
            return False
        return bool(re.search(r"\b(search for|search the|perform search|submit search|run search|look for|rechercher|lancer la recherche)\b", lower))

    def _is_enter_key_step(self, step_text: str) -> bool:
        lower = str(step_text or "").lower()
        return bool(re.search(
            r"\b(press|hit|tap|use|appuyer|taper)\b.*\b(enter|entr[eé]e|return)\b"
            r"|\b(enter|entr[eé]e|return)\b.*\b(key|keyboard|touche|clavier)\b",
            lower,
            re.I,
        ))

    def _click_search_icon(self, page: Any) -> None:
        page.wait_for_load_state("domcontentloaded", timeout=30000)
        self._dismiss_transient_overlays(page)
        candidates = [
            page.get_by_role("button", name=re.compile(r"search|recherche", re.I)),
            page.locator("button[aria-label*='search' i], [role='button'][aria-label*='search' i]"),
            page.locator("button[aria-label*='recherche' i], [role='button'][aria-label*='recherche' i]"),
            page.locator("button[title*='search' i], [role='button'][title*='search' i]"),
            page.locator("button[title*='recherche' i], [role='button'][title*='recherche' i]"),
            page.locator("mat-icon").filter(has_text=re.compile(r"^\s*search\s*$", re.I)).locator(
                "xpath=ancestor::*[self::button or @role='button'][1]"
            ),
            page.locator(".material-icons, .mat-icon").filter(has_text=re.compile(r"^\s*search\s*$", re.I)).locator(
                "xpath=ancestor::*[self::button or @role='button'][1]"
            ),
        ]
        if self._click_first_visible(page, candidates):
            page.wait_for_timeout(700)
            return

        icon = page.locator("mat-icon, .material-icons, .mat-icon").filter(has_text=re.compile(r"^\s*search\s*$", re.I))
        if self._locator_is_visible(icon.first):
            self._safe_click(page, icon.first.locator("xpath=ancestor::*[self::button or @role='button'][1]"))
            page.wait_for_timeout(700)
            return
        if self._click_icon_by_intent(page, "search icon"):
            page.wait_for_timeout(700)
            return
        raise AssertionError("Could not find the search icon on the current page")

    def _click_icon_by_intent(self, page: Any, step_text: str) -> bool:
        try:
            payload = page.evaluate(
                """() => {
                    const isVisible = (el) => {
                        const rect = el.getBoundingClientRect();
                        const style = getComputedStyle(el);
                        return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none' && Number(style.opacity || 1) > 0;
                    };
                    const cssEscape = (value) => {
                        if (window.CSS && CSS.escape) return CSS.escape(value);
                        return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\\\$&");
                    };
                    const textOf = (el) => {
                        const iconTexts = Array.from(el.querySelectorAll('mat-icon,.mat-icon,.material-icons,svg,i,use,title'))
                            .map((child) => [
                                child.innerText || child.textContent || '',
                                child.getAttribute('aria-label') || '',
                                child.getAttribute('title') || '',
                                child.getAttribute('class') || '',
                                child.getAttribute('data-icon') || '',
                                child.getAttribute('href') || '',
                                child.getAttribute('xlink:href') || '',
                            ].join(' '));
                        const parent = el.parentElement;
                        const parentText = parent && !parent.querySelector('button,[role="button"],a[href]')
                            ? (parent.innerText || parent.textContent || '')
                            : '';
                        const labelledBy = (el.getAttribute('aria-labelledby') || '')
                            .split(/\\s+/)
                            .map((id) => document.getElementById(id)?.innerText || document.getElementById(id)?.textContent || '')
                            .join(' ');
                        return [
                            el.innerText || el.textContent || '',
                            parentText,
                            labelledBy,
                            el.id || '',
                            el.getAttribute('name') || '',
                            el.getAttribute('aria-label') || '',
                            el.getAttribute('title') || '',
                            el.getAttribute('class') || '',
                            el.getAttribute('data-testid') || '',
                            el.getAttribute('data-test-id') || '',
                            ...iconTexts,
                        ].join(' ').replace(/\\s+/g, ' ').toLowerCase();
                    };
                    const baseClickables = Array.from(document.querySelectorAll('button,[role="button"],a[href],mat-icon,.mat-icon,.material-icons,svg,i'))
                        .filter(isVisible)
                        .map((el) => {
                            const clickable = el.closest('button,[role="button"],a[href]') || el;
                            const rect = el.getBoundingClientRect();
                            const targetRect = clickable.getBoundingClientRect();
                            if (!clickable.getAttribute('data-vplmqa-runtime-id')) {
                                clickable.setAttribute('data-vplmqa-runtime-id', `icon-candidate-${Date.now()}-${Math.random().toString(16).slice(2)}`);
                            }
                            return {
                                selector: `[data-vplmqa-runtime-id="${cssEscape(clickable.getAttribute('data-vplmqa-runtime-id'))}"]`,
                                rect: {
                                    x: targetRect.x, y: targetRect.y, width: targetRect.width, height: targetRect.height,
                                    centerX: targetRect.x + targetRect.width / 2,
                                    centerY: targetRect.y + targetRect.height / 2,
                                },
                                text: textOf(clickable),
                                tag: clickable.tagName.toLowerCase(),
                                role: clickable.getAttribute('role') || '',
                                disabled: Boolean(clickable.disabled) || clickable.getAttribute('aria-disabled') === 'true',
                            };
                        })
                        .filter((item, index, all) =>
                            !item.disabled &&
                            item.rect.width <= 140 &&
                            item.rect.height <= 140 &&
                            all.findIndex((other) => other.selector === item.selector) === index
                        );
                    return {
                        viewport: { width: window.innerWidth, height: window.innerHeight },
                        candidates: baseClickables.slice(0, 180),
                    };
                }"""
            )
            if not isinstance(payload, dict):
                return False
            candidates = payload.get("candidates") or []
            viewport = payload.get("viewport") or {}
            best = self._best_icon_candidate(step_text, candidates, viewport)
            if not best:
                return False
            self._safe_click(page, page.locator(best["selector"]).first)
            return True
        except Exception:
            return False

    def _best_icon_candidate(
        self,
        step_text: str,
        candidates: List[Dict[str, Any]],
        viewport: Dict[str, Any],
    ) -> Optional[Dict[str, Any]]:
        intent_tokens = self._icon_intent_tokens(step_text)
        if not intent_tokens:
            return None
        wants_add = bool(intent_tokens & {"add", "plus", "ajouter", "create", "new", "nouveau", "nouvelle"})
        wants_favorite = bool(intent_tokens & {"favorite", "star", "favori", "favoris"})
        scored: List[tuple[int, Dict[str, Any]]] = []
        for candidate in candidates:
            text = str(candidate.get("text") or "")
            candidate_tokens = self._icon_candidate_tokens(text)
            score = 0
            score += 12 * len(intent_tokens & candidate_tokens)
            for token in intent_tokens:
                for alias in self._icon_aliases(token):
                    if alias in candidate_tokens or alias in text:
                        score += 9
            rect = candidate.get("rect") or {}
            width = float(rect.get("width") or 0)
            height = float(rect.get("height") or 0)
            center_y = float(rect.get("centerY") or 0)
            viewport_height = float(viewport.get("height") or 900)
            if 18 <= width <= 90 and 18 <= height <= 90:
                score += 2
            if center_y <= max(140, viewport_height * 0.18):
                score += 1
            if any(token in candidate_tokens for token in ("button", "mat", "icon", "material", "svg")):
                score += 1
            if wants_add:
                if candidate_tokens & {"add", "plus", "ajouter", "create", "new", "nouveau", "nouvelle"}:
                    score += 18
                if candidate_tokens & {"favorite", "star", "favori", "favoris", "hotel_class"}:
                    score -= 30
                if candidate_tokens & {"notification", "bell", "work_outline", "portfolio", "briefcase", "search"}:
                    score -= 14
            if wants_favorite and candidate_tokens & {"add", "plus", "ajouter"}:
                score -= 18
            if score >= 10:
                scored.append((score, candidate))
        if not scored:
            return None
        scored.sort(key=lambda item: item[0], reverse=True)
        return scored[0][1]

    def _icon_intent_tokens(self, step_text: str) -> set[str]:
        stop = {
            "i", "the", "a", "an", "on", "to", "click", "press", "tap", "button",
            "icon", "icone", "icône", "menu", "open", "show", "visible", "field",
        }
        tokens = {
            token for token in re.sub(r"[^a-z0-9éèêàç]+", " ", str(step_text or "").lower()).split()
            if len(token) > 1 and token not in stop
        }
        if "+" in str(step_text or ""):
            tokens.add("plus")
        return tokens

    def _icon_candidate_tokens(self, text: str) -> set[str]:
        raw_tokens = {
            token for token in re.sub(r"[^a-z0-9éèêàç]+", " ", str(text or "").lower()).split()
            if len(token) > 1
        }
        if "+" in str(text or ""):
            raw_tokens.add("plus")
        expanded = set(raw_tokens)
        for token in list(raw_tokens):
            expanded.update(self._icon_aliases(token))
        return expanded

    def _icon_aliases(self, token: str) -> set[str]:
        aliases = {
            "search": {"search", "recherche", "chercher", "loupe", "magnifier", "magnifying"},
            "recherche": {"search", "recherche", "chercher", "loupe", "magnifier", "magnifying"},
            "refresh": {"refresh", "reload", "actualiser", "rafraichir", "rafraîchir", "cached", "sync"},
            "actualiser": {"refresh", "reload", "actualiser", "rafraichir", "rafraîchir", "cached", "sync"},
            "filter": {"filter", "filtre", "filter_list", "filter_list_off", "funnel"},
            "filtre": {"filter", "filtre", "filter_list", "filter_list_off", "funnel"},
            "add": {"add", "plus", "create", "ajouter", "new", "nouveau", "nouvelle"},
            "plus": {"add", "plus", "create", "ajouter", "new", "nouveau", "nouvelle"},
            "ajouter": {"add", "plus", "create", "ajouter", "new", "nouveau", "nouvelle"},
            "nouveau": {"add", "plus", "create", "ajouter", "new", "nouveau", "nouvelle"},
            "nouvelle": {"add", "plus", "create", "ajouter", "new", "nouveau", "nouvelle"},
            "delete": {"delete", "remove", "trash", "supprimer"},
            "supprimer": {"delete", "remove", "trash", "supprimer"},
            "logout": {"logout", "deconnexion", "déconnexion", "signout", "exit"},
            "deconnexion": {"logout", "deconnexion", "déconnexion", "signout", "exit"},
            "profile": {"profile", "account", "avatar", "user", "compte"},
            "compte": {"profile", "account", "avatar", "user", "compte"},
            "notification": {"notification", "notifications", "bell", "alert"},
            "home": {"home", "accueil", "house"},
            "favorite": {"favorite", "star", "favori", "favoris", "hotel_class"},
            "favori": {"favorite", "star", "favori", "favoris", "hotel_class"},
            "favoris": {"favorite", "star", "favori", "favoris", "hotel_class"},
            "star": {"favorite", "star", "favori", "favoris", "hotel_class"},
            "close": {"close", "cancel", "fermer", "x"},
            "download": {"download", "telecharger", "télécharger"},
            "upload": {"upload", "importer", "televerser", "téléverser"},
            "attach": {"attach", "attachment", "paperclip", "piece", "pièce"},
            "options": {"options", "more", "menu", "ellipsis", "more_vert", "more_horiz", "kebab", "three", "dots"},
            "more": {"options", "more", "menu", "ellipsis", "more_vert", "more_horiz", "kebab", "three", "dots"},
            "ellipsis": {"options", "more", "menu", "ellipsis", "more_vert", "more_horiz", "kebab", "three", "dots"},
            "confirm": {"confirm", "confirmer", "validate", "valider", "yes", "oui", "ok"},
            "confirmer": {"confirm", "confirmer", "validate", "valider", "yes", "oui", "ok"},
            "cancel": {"cancel", "annuler", "no", "non", "close", "fermer"},
            "annuler": {"cancel", "annuler", "no", "non", "close", "fermer"},
        }
        return aliases.get(token, {token})

    def _fill_search_field(self, page: Any, query: str) -> None:
        if not self._visible_search_input(page):
            self._click_search_icon(page)
        locator = self._visible_search_input(page)
        if locator is None:
            locator = self._focused_text_input(page)
        if locator is None:
            raise AssertionError("Could not find a visible search input after opening search")
        locator.fill(query)

    def _visible_search_input(self, page: Any) -> Optional[Any]:
        selectors = [
            "input[type='search']",
            "input[name*='search' i]",
            "input[id*='search' i]",
            "input[placeholder*='search' i]",
            "input[aria-label*='search' i]",
            "input[title*='search' i]",
            "input[name*='recherche' i]",
            "input[id*='recherche' i]",
            "input[placeholder*='recherche' i]",
            "input[aria-label*='recherche' i]",
            "input[title*='recherche' i]",
            "textarea[name*='search' i]",
            "textarea[placeholder*='search' i]",
            "textarea[placeholder*='recherche' i]",
        ]
        for selector in selectors:
            locator = page.locator(selector)
            for index in range(locator.count()):
                candidate = locator.nth(index)
                if self._locator_is_visible(candidate) and self._is_editable_text_locator(candidate):
                    return candidate
        preferred = self._best_text_input_by_intent(page, "search")
        if preferred is not None:
            return preferred
        return self._first_visible_text_input(page)

    def _best_text_input_by_intent(self, page: Any, intent: str) -> Optional[Any]:
        script = """
        ({ intent }) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/\\s+/g, " ")
            .trim()
            .toLowerCase();
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const cssEscape = (value) => {
            if (window.CSS && CSS.escape) return CSS.escape(value);
            return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\\\$&");
          };
          const labelText = (element) => {
            const id = element.id;
            const explicit = id ? Array.from(document.querySelectorAll(`label[for="${cssEscape(id)}"]`))
              .map((label) => label.innerText || label.textContent || "")
              .join(" ") : "";
            const labelledBy = (element.getAttribute("aria-labelledby") || "")
              .split(/\\s+/)
              .map((item) => document.getElementById(item)?.innerText || document.getElementById(item)?.textContent || "")
              .join(" ");
            const container = element.closest("mat-form-field,.mat-mdc-form-field,.mat-form-field,.search,.search-bar,.toolbar,header,nav,.cdk-overlay-pane,form") || element.parentElement;
            const contextual = container ? (container.innerText || container.textContent || "") : "";
            return normalize([
              explicit,
              labelledBy,
              element.id,
              element.name,
              element.getAttribute("aria-label"),
              element.getAttribute("placeholder"),
              element.getAttribute("title"),
              element.getAttribute("role"),
              contextual,
            ].join(" "));
          };
          let runtimeId = 0;
          const mark = (element) => {
            const existing = element.getAttribute("data-vplmqa-runtime-id");
            if (existing) return `[data-vplmqa-runtime-id="${cssEscape(existing)}"]`;
            const assigned = `runtime-input-${Date.now()}-${runtimeId++}`;
            element.setAttribute("data-vplmqa-runtime-id", assigned);
            return `[data-vplmqa-runtime-id="${cssEscape(assigned)}"]`;
          };
          const controls = Array.from(document.querySelectorAll("input:not([type='hidden']), textarea"))
            .filter((element) => {
              const type = (element.getAttribute("type") || "text").toLowerCase();
              return isVisible(element)
                && !["password", "checkbox", "radio", "button", "submit", "reset", "file"].includes(type)
                && !element.disabled
                && !element.readOnly
                && element.getAttribute("aria-disabled") !== "true";
            });
          const scored = controls.map((element) => {
            const rect = element.getBoundingClientRect();
            const text = labelText(element);
            let score = 0;
            if (intent === "search") {
              if (/search|recherche|chercher|loupe/.test(text)) score += 40;
              if (document.activeElement === element) score += 30;
              if (rect.top < Math.max(180, window.innerHeight * 0.24)) score += 14;
              if (rect.width > 160) score += 8;
              if (/classe|classes d.objets|object class|objet|base|poste|workstation|database/.test(text)) score -= 45;
              if ((element.getAttribute("role") || "").toLowerCase() === "combobox") score -= 20;
              if (/reference|designation|password|username|login/.test(text)) score -= 15;
            }
            return { selector: mark(element), score, text };
          }).filter((item) => item.score > 0);
          scored.sort((a, b) => b.score - a.score);
          return scored[0]?.selector || "";
        }
        """
        try:
            selector = str(page.evaluate(script, {"intent": intent}) or "")
        except Exception:
            return None
        if not selector:
            return None
        locator = page.locator(selector).first
        if self._locator_is_visible(locator) and self._is_editable_text_locator(locator):
            return locator
        return None

    def _focused_text_input(self, page: Any) -> Optional[Any]:
        try:
            handle = page.evaluate_handle(
                """() => {
                    const element = document.activeElement;
                    if (!element) return null;
                    const tag = element.tagName.toLowerCase();
                    const type = (element.getAttribute("type") || "text").toLowerCase();
                    if ((tag === "input" || tag === "textarea") && !["checkbox", "radio", "hidden", "button", "submit"].includes(type)) {
                        if (!element.getAttribute("data-vplmqa-runtime-id")) {
                            element.setAttribute("data-vplmqa-runtime-id", `focused-${Date.now()}`);
                        }
                        return element.getAttribute("data-vplmqa-runtime-id");
                    }
                    return null;
                }"""
            )
            runtime_id = handle.json_value()
            if runtime_id:
                locator = page.locator(f"[data-vplmqa-runtime-id='{runtime_id}']").first
                if self._locator_is_visible(locator) and self._is_editable_text_locator(locator):
                    return locator
        except Exception:
            return None
        return None

    def _first_visible_text_input(self, page: Any) -> Optional[Any]:
        locator = page.locator(
            "input:not([type='hidden']):not([type='password']):not([type='checkbox']):not([type='radio']):not([type='button']):not([type='submit']), textarea"
        )
        for index in range(locator.count()):
            candidate = locator.nth(index)
            if self._locator_is_visible(candidate) and self._is_editable_text_locator(candidate):
                text = self._locator_identity_text(candidate).lower()
                if "stayconnected" in text or "stay-connected" in text:
                    continue
                return candidate
        return None

    def _is_editable_text_locator(self, locator: Any) -> bool:
        try:
            return bool(locator.evaluate(
                """(element) => {
                    const tag = element.tagName.toLowerCase();
                    const type = (element.getAttribute("type") || "text").toLowerCase();
                    return (tag === "textarea" || tag === "input")
                        && !["hidden", "password", "checkbox", "radio", "button", "submit"].includes(type)
                        && !element.disabled
                        && element.getAttribute("aria-disabled") !== "true"
                        && !element.readOnly;
                }"""
            ))
        except Exception:
            return False

    def _locator_identity_text(self, locator: Any) -> str:
        try:
            return str(locator.evaluate(
                """(element) => [
                    element.id || "",
                    element.getAttribute("name") || "",
                    element.getAttribute("aria-label") || "",
                    element.getAttribute("placeholder") || "",
                    element.getAttribute("title") || ""
                ].join(" ")"""
            ))
        except Exception:
            return ""

    def _wait_after_search_submit(self, page: Any) -> None:
        try:
            page.wait_for_load_state("domcontentloaded", timeout=15000)
        except Exception:
            pass
        try:
            page.wait_for_load_state("networkidle", timeout=8000)
        except Exception:
            pass
        page.wait_for_timeout(1000)

    def _fill_by_intent(self, page: Any, step_text: str, value: str) -> None:
        lower = step_text.lower()
        label_patterns: List[str] = []
        selectors: List[str] = []
        if "problem title" in lower or re.search(r"\btitle\b", lower):
            label_patterns = [r"problem\s*title", r"\btitle\b"]
            selectors = [
                "input[name='title']",
                "input[id*='title' i]",
                "input[placeholder*='problem' i]",
                "input[placeholder*='title' i]",
                "input[placeholder*='short problem' i]",
            ]
        elif "description" in lower or "textarea" in lower:
            label_patterns = [r"description"]
            selectors = [
                "textarea[name='description']",
                "textarea[id*='description' i]",
                "textarea[placeholder*='steps' i]",
                "textarea[placeholder*='actual result' i]",
                "textarea",
            ]
        elif "assigned" in lower or "assignee" in lower:
            label_patterns = [r"assigned\s*to", r"assignee"]
            selectors = [
                "input[name='assignedTo']",
                "input[id*='assigned' i]",
                "input[placeholder*='name or email' i]",
                "input[placeholder*='assigned' i]",
            ]
        elif "project id" in lower or "project uuid" in lower:
            label_patterns = [r"project\s*id", r"project\s*uuid"]
            selectors = [
                "input[name='projectId']",
                "input[id*='project' i]",
                "input[placeholder*='project uuid' i]",
            ]
        elif "reference" in lower or "rÃ©fÃ©rence" in lower:
            label_patterns = [r"r[eÃ©]f[eÃ©]rence"]
            selectors = [
                "input[name*='reference' i]",
                "input[id*='reference' i]",
                "input[aria-label*='reference' i]",
                "input[placeholder*='reference' i]",
                "input[placeholder*='rÃ©fÃ©rence' i]",
            ]
        elif "designation" in lower or "dÃ©signation" in lower:
            label_patterns = [r"d[eÃ©]signation"]
            selectors = [
                "input[name*='designation' i]",
                "input[id*='designation' i]",
                "input[aria-label*='designation' i]",
                "input[placeholder*='designation' i]",
                "input[placeholder*='dÃ©signation' i]",
            ]
        elif "password" in lower:
            label_patterns = [r"password"]
            selectors = [
                "input[type='password']",
                "input[name='password']",
                "input[id='password']",
                "input[autocomplete='current-password']",
            ]
        elif "email" in lower or "e-mail" in lower:
            label_patterns = [r"email", r"e-mail"]
            selectors = [
                "input[type='email']",
                "input[name='email']",
                "input[id='email']",
                "input[autocomplete='email']",
                "input[placeholder*='email' i]",
            ]
        else:
            selectors = [
                "input:not([type='hidden']):not([type='password']):not([type='checkbox']):not([type='radio']):not([type='button']):not([type='submit']):not([type='reset']):not([type='file'])",
                "textarea",
            ]

        if any(term in lower for term in ("problem title", "description", "assigned", "assignee", "project id")):
            self._ensure_ticket_form(page)

        material_label = ""
        if "reference" in lower or "rÃƒÂ©fÃƒÂ©rence" in lower or "rÃ©fÃ©rence" in lower:
            material_label = "reference"
        elif "designation" in lower or "dÃƒÂ©signation" in lower or "dÃ©signation" in lower:
            material_label = "designation"
        if material_label and self._fill_material_field_by_label_precise(page, material_label, value):
            return

        generic_label = self._field_label_from_fill_step(step_text)
        if generic_label and self._fill_text_field_by_normalized_label(page, generic_label, value):
            return

        for pattern in label_patterns:
            locator = page.get_by_label(re.compile(pattern, re.I))
            if self._locator_is_visible(locator.first):
                locator.first.fill(value)
                return

        for selector in selectors:
            locator = page.locator(selector)
            for index in range(locator.count()):
                candidate = locator.nth(index)
                if not self._locator_is_visible(candidate):
                    continue
                if not self._locator_is_fillable(candidate):
                    continue
                candidate.fill(value)
                return
        raise AssertionError(f"Could not find an input for step: {step_text}")

    def _field_label_from_fill_step(self, step_text: str) -> str:
        text = re.sub(r"\s+", " ", str(step_text or "").strip())
        patterns = [
            r"\b(?:in|dans)\s+(?:the\s+|le\s+|la\s+|l['’])?(.+?)\s+(?:field|champ)\b",
            r"\b(?:field|champ)\s+(.+)$",
        ]
        for pattern in patterns:
            match = re.search(pattern, text, re.I)
            if match:
                label = match.group(1).strip().strip("\"'")
                return label
        return ""

    def _fill_text_field_by_normalized_label(self, page: Any, field_label: str, value: str) -> bool:
        clean_field_label, ordinal_index = self._field_label_and_ordinal(field_label)
        script = """
        ({ fieldLabel, ordinalIndex }) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/[^a-z0-9]+/gi, "")
            .toLowerCase();
          const wanted = normalize(fieldLabel);
          if (!wanted) return "";
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const isEditableTextControl = (element) => {
            const tag = element.tagName.toLowerCase();
            const type = (element.getAttribute("type") || "text").toLowerCase();
            return (tag === "textarea" || tag === "input")
              && !["hidden", "password", "checkbox", "radio", "button", "submit", "reset", "file"].includes(type)
              && !element.disabled
              && !element.readOnly
              && element.getAttribute("aria-disabled") !== "true"
              && isVisible(element);
          };
          const cssEscape = (value) => {
            if (window.CSS && CSS.escape) return CSS.escape(value);
            return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\\\$&");
          };
          const mark = (element) => {
            const existing = element.getAttribute("data-vplmqa-runtime-id");
            if (existing) return `[data-vplmqa-runtime-id="${cssEscape(existing)}"]`;
            const assigned = `runtime-fill-${Date.now()}-${Math.random().toString(36).slice(2)}`;
            element.setAttribute("data-vplmqa-runtime-id", assigned);
            return `[data-vplmqa-runtime-id="${cssEscape(assigned)}"]`;
          };
          const controls = Array.from(document.querySelectorAll("input:not([type='hidden']), textarea"))
            .filter(isEditableTextControl);
          const scored = controls.map((control) => {
            const rect = control.getBoundingClientRect();
            const field = control.closest("mat-form-field,.mat-mdc-form-field,.mat-form-field,.field,.form-group,[class*='field']")
              || control.parentElement
              || control;
            const id = control.id || "";
            const explicitLabel = id ? Array.from(document.querySelectorAll(`label[for="${cssEscape(id)}"]`))
              .map((label) => label.innerText || label.textContent || "")
              .join(" ") : "";
            const labelledBy = (control.getAttribute("aria-labelledby") || "")
              .split(/\\s+/)
              .map((item) => document.getElementById(item)?.innerText || document.getElementById(item)?.textContent || "")
              .join(" ");
            const labelNodes = Array.from(field.querySelectorAll("label,mat-label,.mat-mdc-floating-label,.mat-form-field-label,.mdc-floating-label,legend"))
              .map((label) => label.innerText || label.textContent || "")
              .join(" ");
            const context = [
              explicitLabel,
              labelledBy,
              labelNodes,
              control.id,
              control.name,
              control.getAttribute("aria-label"),
              control.getAttribute("placeholder"),
              control.getAttribute("title"),
              control.getAttribute("formcontrolname"),
              field.innerText || field.textContent || ""
            ].join(" ");
            const normalizedContext = normalize(context);
            let score = 0;
            if (normalizedContext.includes(wanted)) score += 50;
            if (normalize(control.getAttribute("aria-label") || "") === wanted) score += 30;
            if (normalize(control.getAttribute("placeholder") || "") === wanted) score += 20;
            score += Math.max(0, 8 - Math.floor(rect.top / 220));
            return { selector: mark(control), score, top: rect.top, left: rect.left };
          }).filter((item) => item.score >= 50);
          scored.sort((a, b) => {
            if (Number(ordinalIndex || 0) > 0) {
              return (a.top - b.top) || (a.left - b.left) || (b.score - a.score);
            }
            return (b.score - a.score) || (a.top - b.top) || (a.left - b.left);
          });
          const selected = scored[Math.min(Number(ordinalIndex || 0), Math.max(0, scored.length - 1))];
          return selected?.selector || "";
        }
        """
        try:
            selector = str(page.evaluate(script, {"fieldLabel": clean_field_label, "ordinalIndex": ordinal_index}) or "")
        except Exception:
            return False
        if not selector:
            return False
        locator = page.locator(selector).first
        if not self._locator_is_fillable(locator):
            return False
        try:
            locator.fill(value, timeout=5000)
            locator.evaluate(
                """element => {
                  element.dispatchEvent(new Event('input', { bubbles: true }));
                  element.dispatchEvent(new Event('change', { bubbles: true }));
                  element.dispatchEvent(new Event('blur', { bubbles: true }));
                }"""
            )
            try:
                locator.press("Tab", timeout=1000)
            except Exception:
                pass
            return True
        except Exception:
            return False

    def _is_form_field_intent(self, step_text: str) -> bool:
        lower = step_text.lower()
        return any(
            term in lower
            for term in (
                "reference",
                "rÃƒÂ©fÃƒÂ©rence",
                "rÃ©fÃ©rence",
                "designation",
                "dÃƒÂ©signation",
                "dÃ©signation",
            )
        )

    def _fill_material_field_by_label(self, page: Any, label_key: str, value: str) -> bool:
        script = """
        ({ labelKey, value }) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/\\s+/g, " ")
            .trim()
            .toLowerCase();
          const labelAliases = {
            reference: ["reference", "rÃ©fÃ©rence", "rÃƒÂ©fÃƒÂ©rence"],
            designation: ["designation", "dÃ©signation", "dÃƒÂ©signation"]
          };
          const aliases = (labelAliases[labelKey] || [labelKey]).map(normalize);
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.visibility !== "hidden" && style.display !== "none";
          };
          const setNativeValue = (element, nextValue) => {
            const proto = element.tagName === "TEXTAREA" ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
            const descriptor = Object.getOwnPropertyDescriptor(proto, "value");
            if (descriptor && descriptor.set) descriptor.set.call(element, nextValue);
            else element.value = nextValue;
            element.dispatchEvent(new Event("input", { bubbles: true }));
            element.dispatchEvent(new Event("change", { bubbles: true }));
            element.dispatchEvent(new Event("blur", { bubbles: true }));
          };
          const controls = Array.from(document.querySelectorAll("input:not([type='hidden']), textarea"));
          for (const control of controls) {
            if (!isVisible(control)) continue;
            let current = control;
            let contextText = "";
            for (let depth = 0; current && depth < 7; depth += 1) {
              contextText += " " + (current.innerText || current.textContent || "");
              current = current.parentElement;
            }
            const normalizedContext = normalize(contextText);
            if (aliases.some((alias) => normalizedContext.includes(alias))) {
              control.focus();
              setNativeValue(control, value);
              return true;
            }
          }
          return false;
        }
        """
        try:
            return bool(page.evaluate(script, {"labelKey": label_key, "value": value}))
        except Exception:
            return False

    def _fill_material_field_by_label_precise(self, page: Any, label_key: str, value: str) -> bool:
        script = """
        ({ labelKey }) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/\\s+/g, " ")
            .trim()
            .toLowerCase();
          const aliases = {
            reference: ["reference", "rÃ©fÃ©rence"],
            designation: ["designation", "dÃ©signation"]
          }[labelKey] || [labelKey];
          const wanted = aliases.map(normalize);
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const mark = (element) => {
            const id = `field-${Date.now()}-${Math.random().toString(36).slice(2)}`;
            element.setAttribute("data-vplmqa-material-field", id);
            return `[data-vplmqa-material-field="${id}"]`;
          };
          const labelSelectors = [
            "label",
            "mat-label",
            ".mat-mdc-floating-label",
            ".mat-form-field-label",
            ".mdc-floating-label",
            "legend"
          ].join(",");
          const fields = Array.from(document.querySelectorAll("mat-form-field, .mat-mdc-form-field, .mat-form-field"));
          for (const field of fields) {
            if (!isVisible(field)) continue;
            const labelText = Array.from(field.querySelectorAll(labelSelectors))
              .map((node) => node.innerText || node.textContent || "")
              .join(" ");
            const normalizedLabel = normalize(labelText || field.innerText || field.textContent || "");
            if (!wanted.some((alias) => normalizedLabel.includes(alias))) continue;
            const control = Array.from(field.querySelectorAll("input:not([type='hidden']), textarea"))
              .find((item) => isVisible(item) && !item.disabled && item.getAttribute("aria-disabled") !== "true");
            if (control) return mark(control);
          }
          const controls = Array.from(document.querySelectorAll("input:not([type='hidden']), textarea"));
          for (const control of controls) {
            if (!isVisible(control) || control.disabled || control.getAttribute("aria-disabled") === "true") continue;
            const attrs = [
              control.id,
              control.name,
              control.getAttribute("aria-label"),
              control.getAttribute("placeholder"),
              control.getAttribute("formcontrolname")
            ].map(normalize).join(" ");
            if (wanted.some((alias) => attrs.includes(alias))) return mark(control);
          }
          return "";
        }
        """
        try:
            selector = str(page.evaluate(script, {"labelKey": label_key}) or "")
        except Exception:
            return False
        if not selector:
            return False
        try:
            locator = page.locator(selector).first
            locator.fill(value, timeout=5000)
            locator.evaluate(
                """element => {
                  element.dispatchEvent(new Event('input', { bubbles: true }));
                  element.dispatchEvent(new Event('change', { bubbles: true }));
                  element.dispatchEvent(new Event('blur', { bubbles: true }));
                }"""
            )
            try:
                locator.press("Tab", timeout=1000)
            except Exception:
                pass
            page.wait_for_timeout(300)
            try:
                return str(locator.input_value(timeout=1000)) == str(value)
            except Exception:
                return True
        except Exception:
            return False

    def _click_text_target(self, page: Any, value: str) -> None:
        label = value.strip()
        if not label:
            raise AssertionError("Click step requires a target label")
        if self._is_logout_intent(label):
            self._click_logout(page)
            return
        if self._is_save_intent(label):
            self._click_save_and_assert(page)
            return
        if re.search(r"create\s+(?:the\s+)?ticket", label, re.I):
            candidates = [
                page.get_by_role("button", name=re.compile(r"^create\s+(?:the\s+)?ticket$", re.I)),
                page.locator("button").filter(has_text=re.compile(r"create\s+(?:the\s+)?ticket", re.I)),
            ]
            for locator in candidates:
                count = locator.count()
                if count > 0:
                    self._safe_click(page, locator.nth(count - 1))
                    self._wait_for_ticket_form_or_result(page)
                    return
        button = page.get_by_role("button", name=re.compile(re.escape(label), re.I))
        if button.count() > 0:
            self._safe_click(page, button.first)
            if re.search(r"ajouter\s+une\s+condition|add\s+(?:a\s+)?condition", label, re.I):
                self._wait_for_advanced_condition_row(page)
            return
        normalized_target = self._normalized_text_click_locator(page, label)
        if normalized_target is not None:
            self._safe_click(page, normalized_target)
            if re.search(r"ajouter\s+une\s+condition|add\s+(?:a\s+)?condition", label, re.I):
                self._wait_for_advanced_condition_row(page)
            return
        self._safe_click(page, page.get_by_text(label, exact=False).first)

    def _wait_for_advanced_condition_row(self, page: Any) -> None:
        script = """
        () => Array.from(document.querySelectorAll(
          "input:not([type='hidden']), textarea, select, [role='combobox'], .mat-mdc-select, .mat-select"
        )).filter((element) => {
          const rect = element.getBoundingClientRect();
          const style = window.getComputedStyle(element);
          return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
        }).length
        """
        try:
            before = int(page.evaluate(script) or 0)
        except Exception:
            before = 0
        deadline = time.time() + 4
        last = before
        while time.time() < deadline:
            try:
                current = int(page.evaluate(script) or 0)
                if current > before:
                    page.wait_for_timeout(500)
                    return
                last = current
            except Exception:
                pass
            page.wait_for_timeout(200)
        if last <= before:
            page.wait_for_timeout(700)

    def _normalized_text_click_locator(self, page: Any, label: str) -> Optional[Any]:
        script = """
        (target) => {
          const normalize = (text) => String(text || "")
            .normalize("NFD")
            .replace(/[\\u0300-\\u036f]/g, "")
            .replace(/[\\u00a0\\u200b\\u200c\\u200d]/g, " ")
            .replace(/\\s+/g, " ")
            .trim()
            .toLowerCase();
          const needle = normalize(target);
          if (!needle) return "";
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const cssEscape = (value) => {
            if (window.CSS && CSS.escape) return CSS.escape(value);
            return String(value).replace(/[^a-zA-Z0-9_-]/g, "\\\\$&");
          };
          const mark = (element) => {
            const clickable = element.closest("button,a[href],[role='button'],[role='menuitem'],[role='option']") || element;
            const existing = clickable.getAttribute("data-vplmqa-runtime-id");
            if (existing) return `[data-vplmqa-runtime-id="${cssEscape(existing)}"]`;
            const assigned = `runtime-click-${Date.now()}-${Math.random().toString(36).slice(2)}`;
            clickable.setAttribute("data-vplmqa-runtime-id", assigned);
            return `[data-vplmqa-runtime-id="${cssEscape(assigned)}"]`;
          };
          const selectors = [
            "button",
            "a[href]",
            "[role='button']",
            "[role='menuitem']",
            "[role='option']",
            ".mat-mdc-option",
            ".mat-mdc-menu-item",
            ".mat-menu-item",
            "li",
            "div",
            "span"
          ];
          const candidates = Array.from(document.querySelectorAll(selectors.join(",")))
            .filter(isVisible)
            .map((element) => {
              const text = normalize(element.innerText || element.textContent || "");
              const clickable = element.closest("button,a[href],[role='button'],[role='menuitem'],[role='option']") || element;
              const rect = clickable.getBoundingClientRect();
              let score = 0;
              if (text === needle) score += 40;
              else if (text.includes(needle)) score += 22;
              if (["BUTTON", "A"].includes(clickable.tagName) || clickable.getAttribute("role")) score += 8;
              if (rect.width > 0 && rect.height > 0) score += 2;
              return { element, score };
            })
            .filter((item) => item.score >= 22)
            .sort((a, b) => b.score - a.score);
          return candidates.length ? mark(candidates[0].element) : "";
        }
        """
        try:
            selector = str(page.evaluate(script, label) or "")
        except Exception:
            return None
        if not selector:
            return None
        locator = page.locator(selector).first
        return locator if self._locator_is_visible(locator) else None

    def _is_save_intent(self, value: str) -> bool:
        return bool(re.search(r"enregistrer|save|submit", value, re.I))

    def _click_save_and_assert(self, page: Any) -> None:
        self._fail_if_form_has_validation_errors(page)
        candidates = [
            page.get_by_role("button", name=re.compile(r"enregistrer|save", re.I)),
            page.locator("button").filter(has_text=re.compile(r"enregistrer|save", re.I)),
            page.locator("button[type='submit']"),
        ]
        button = self._first_usable_button(candidates)
        if button is None:
            raise AssertionError("Could not find an enabled Save/Enregistrer button. The form is probably still invalid.")
        self._safe_click(page, button)
        try:
            page.wait_for_load_state("domcontentloaded", timeout=8000)
        except Exception:
            pass
        self._wait_for_save_completion(page)

    def _wait_for_save_completion(self, page: Any) -> None:
        timeout_ms = int(os.getenv("E2E_SAVE_TIMEOUT_MS", "45000"))
        deadline = time.time() + (timeout_ms / 1000)
        last_state = "waiting"
        while time.time() < deadline:
            try:
                self._fail_if_form_has_validation_errors(page)
            except AssertionError:
                raise
            except Exception:
                pass

            if self._save_success_observed(page):
                return

            dialog_open = self._creation_dialog_is_still_open(page)
            busy = self._save_busy_observed(page)
            if not dialog_open:
                page.wait_for_timeout(700)
                if self._save_success_observed(page) or not self._creation_dialog_is_still_open(page):
                    return

            last_state = "still saving" if busy else "form still open"
            page.wait_for_timeout(700)

        self._fail_if_form_has_validation_errors(page)
        if self._save_success_observed(page):
            return
        if self._creation_dialog_is_still_open(page):
            raise AssertionError(
                f"Save was clicked, but the creation form is still open after {timeout_ms // 1000}s ({last_state}). "
                "The object may still be saving or may not have been created."
            )

    def _save_busy_observed(self, page: Any) -> bool:
        script = """
        () => {
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const busySelectors = [
            "mat-progress-bar",
            ".mat-mdc-progress-bar",
            ".mat-progress-bar",
            "[role='progressbar']",
            ".mat-mdc-progress-spinner",
            ".mat-spinner",
            ".loading",
            ".spinner"
          ];
          if (Array.from(document.querySelectorAll(busySelectors.join(","))).some(isVisible)) {
            return true;
          }
          const saveButtons = Array.from(document.querySelectorAll("button"))
            .filter((button) => /enregistrer|save/i.test(button.innerText || button.textContent || ""));
          return saveButtons.some((button) =>
            isVisible(button) && (
              button.disabled ||
              button.getAttribute("aria-disabled") === "true" ||
              button.classList.contains("mat-mdc-button-disabled") ||
              button.classList.contains("mat-button-disabled") ||
              button.classList.contains("disabled")
            )
          );
        }
        """
        try:
            return bool(page.evaluate(script))
        except Exception:
            return False

    def _first_usable_button(self, candidates: List[Any]) -> Optional[Any]:
        for locator in candidates:
            try:
                count = locator.count()
                for index in range(min(count, 8)):
                    candidate = locator.nth(index)
                    if not self._locator_is_visible(candidate):
                        continue
                    disabled = bool(candidate.evaluate(
                        """element => Boolean(
                          element.disabled ||
                          element.getAttribute('aria-disabled') === 'true' ||
                          element.classList.contains('mat-mdc-button-disabled') ||
                          element.classList.contains('mat-button-disabled') ||
                          element.classList.contains('disabled')
                        )"""
                    ))
                    if not disabled:
                        return candidate
            except Exception:
                continue
        return None

    def _fail_if_form_has_validation_errors(self, page: Any) -> None:
        script = """
        () => {
          const normalize = (text) => String(text || "").replace(/\\s+/g, " ").trim();
          const selectors = [
            ".mat-mdc-form-field-error",
            ".mat-error",
            "[role='alert']",
            ".error",
            ".invalid-feedback"
          ];
          const errors = Array.from(document.querySelectorAll(selectors.join(",")))
            .map((element) => normalize(element.innerText || element.textContent || ""))
            .filter(Boolean);
          const bodyText = normalize(document.body ? document.body.innerText : "");
          if (/obligatoire|required|invalid/i.test(bodyText)) {
            const lines = bodyText.split(/(?=[A-ZÃ€-Ã¿])/).filter((line) => /obligatoire|required|invalid/i.test(line));
            errors.push(...lines.slice(0, 3));
          }
          return Array.from(new Set(errors)).slice(0, 5);
        }
        """
        try:
            errors = page.evaluate(script)
        except Exception:
            errors = []
        if errors:
            raise AssertionError("The form still has validation errors: " + " | ".join(str(item) for item in errors))

    def _creation_form_is_still_open(self, page: Any) -> bool:
        try:
            return bool(page.evaluate(
                """() => {
                  const text = document.body ? (document.body.innerText || "") : "";
                  return /CrÃ©ation d'objet|Creation d'objet|Article conÃ§u|Article concu|Projet|Project/i.test(text)
                    && /RÃ©fÃ©rence|Reference|DÃ©signation|Designation/i.test(text);
                }"""
            ))
        except Exception:
            return False

    def _save_success_observed(self, page: Any) -> bool:
        script = """
        () => {
          const normalize = (text) => String(text || "").replace(/\\s+/g, " ").trim();
          const bodyText = normalize(document.body ? document.body.innerText : "");
          const successPattern = /cr. avec succ.s|crÃ©Ã© avec succÃ¨s|created successfully|a .t. cr.. avec succ.s/i;
          if (successPattern.test(bodyText)) return true;

          const overlayText = normalize(Array.from(document.querySelectorAll([
            ".cdk-overlay-pane",
            ".mat-mdc-snack-bar-container",
            ".mat-snack-bar-container",
            "[role='status']",
            "[aria-live]"
          ].join(","))).map((element) => element.innerText || element.textContent || "").join(" "));
          if (successPattern.test(overlayText)) return true;

          return /Mes projets|Mes derni.res cr.ations|Mes derniÃ¨res crÃ©ations/i.test(bodyText)
            && /Ajouter/i.test(bodyText)
            && /R.f.rence|RÃ©fÃ©rence|Reference/i.test(bodyText)
            && !/Cr.ation d.objet|Creation d.objet/i.test(bodyText);
        }
        """
        try:
            return bool(page.evaluate(script))
        except Exception:
            return False

    def _creation_dialog_is_still_open(self, page: Any) -> bool:
        script = """
        () => {
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const containers = Array.from(document.querySelectorAll([
            "[role='dialog']",
            ".mat-mdc-dialog-container",
            ".mat-dialog-container",
            ".cdk-overlay-pane",
            "form"
          ].join(","))).filter(isVisible);
          return containers.some((container) => {
            const text = container.innerText || container.textContent || "";
            return /Cr.ation d.objet|Creation d.objet|Article con.u|Article concu|Projet|Project/i.test(text)
              && /R.f.rence|Reference|D.signation|Designation/i.test(text)
              && /Enregistrer|Save/i.test(text);
          });
        }
        """
        try:
            return bool(page.evaluate(script))
        except Exception:
            return False

    def _recover_project_module_navigation(self, page: Any, step_text: str, value: str) -> None:
        requested_page = self._requested_page_name(step_text, value)
        if not requested_page:
            return
        requested_key = self._page_match_key(requested_page)
        if requested_key in {"", "login", "home"}:
            return
        try:
            page.wait_for_load_state("domcontentloaded", timeout=10000)
            home_heading = page.get_by_text(re.compile(r"Welcome to VPLMQA", re.I)).first
            if not self._locator_is_visible(home_heading):
                return
            card = page.get_by_role("button", name=re.compile(re.escape(requested_page), re.I))
            if card.count() > 0:
                self._safe_click(page, card.first)
                page.wait_for_load_state("domcontentloaded", timeout=15000)
                page.wait_for_timeout(800)
                return
            card_by_text = page.locator("button").filter(has_text=re.compile(re.escape(requested_page), re.I))
            if card_by_text.count() > 0:
                self._safe_click(page, card_by_text.first)
                page.wait_for_load_state("domcontentloaded", timeout=15000)
                page.wait_for_timeout(800)
            if requested_key == "tickets" and self._locator_is_visible(home_heading):
                raise AssertionError(f"Could not open Tickets page; still showing Home at {page.url}")
        except Exception:
            if requested_key == "tickets":
                raise
            return

    def _locator_is_visible(self, locator: Any) -> bool:
        try:
            return locator.count() > 0 and locator.is_visible()
        except Exception:
            return False

    def _locator_is_fillable(self, locator: Any) -> bool:
        if not self._locator_is_visible(locator):
            return False
        try:
            return bool(locator.evaluate(
                """element => {
                  const tag = element.tagName.toLowerCase();
                  const type = (element.getAttribute("type") || "text").toLowerCase();
                  if (element.disabled || element.readOnly || element.getAttribute("aria-disabled") === "true") return false;
                  if (tag === "textarea") return true;
                  if (tag !== "input") return false;
                  return !["hidden", "checkbox", "radio", "button", "submit", "reset", "file"].includes(type);
                }"""
            ))
        except Exception:
            return False

    def _locator_is_clickable(self, locator: Any) -> bool:
        if not self._locator_is_visible(locator):
            return False
        try:
            return bool(locator.evaluate(
                """element => {
                  const clickable = element.closest("button,a[href],[role='button'],[role='menuitem'],[role='option']") || element;
                  const tag = clickable.tagName.toLowerCase();
                  const role = clickable.getAttribute("role") || "";
                  const type = (clickable.getAttribute("type") || "").toLowerCase();
                  if (clickable.disabled || clickable.getAttribute("aria-disabled") === "true") return false;
                  if (tag === "input" && !["button", "submit", "reset"].includes(type)) return false;
                  return tag === "button" || tag === "a" || ["button", "menuitem", "option", "link"].includes(role)
                    || clickable.tabIndex >= 0
                    || typeof clickable.onclick === "function";
                }"""
            ))
        except Exception:
            return False

    def _ensure_ticket_form(self, page: Any) -> None:
        problem_title = page.get_by_text(re.compile(r"problem\s*title", re.I)).first
        if self._locator_is_visible(problem_title):
            return
        home_heading = page.get_by_text(re.compile(r"Welcome to VPLMQA", re.I)).first
        if self._locator_is_visible(home_heading):
            try:
                page.evaluate("() => window.location.assign('/tickets')")
                page.wait_for_load_state("domcontentloaded", timeout=15000)
                page.wait_for_timeout(1200)
            except Exception:
                pass
        if self._locator_is_visible(home_heading):
            tickets_card = page.locator("button").filter(has_text=re.compile(r"Tickets", re.I))
            if tickets_card.count() > 0:
                self._safe_click(page, tickets_card.first)
                page.wait_for_timeout(1200)
                if self._locator_is_visible(home_heading):
                    tickets_card.first.evaluate("(element) => element.click()")
                    page.wait_for_timeout(1200)
        if self._locator_is_visible(home_heading):
            raise AssertionError(f"Could not open Tickets page; still showing Home at {page.url}")
        if self._locator_is_visible(problem_title):
            return
        create_ticket = page.get_by_role("button", name=re.compile(r"^create\s+(?:the\s+)?ticket$", re.I))
        if create_ticket.count() > 0:
            self._safe_click(page, create_ticket.first, timeout=15000)
            page.wait_for_timeout(1200)

    def _click_locator_center(self, page: Any, locator: Any) -> None:
        locator.scroll_into_view_if_needed(timeout=10000)
        box = locator.bounding_box(timeout=10000)
        if box:
            page.mouse.click(box["x"] + box["width"] / 2, box["y"] + box["height"] / 2)
            return
        locator.click(timeout=10000)

    def _wait_for_ticket_form_or_result(self, page: Any) -> None:
        try:
            page.get_by_text(re.compile(r"problem\s*title|ticket\s*created", re.I)).first.wait_for(
                state="visible",
                timeout=8000,
            )
        except Exception:
            page.wait_for_timeout(1000)

    def _click_priority_intent(self, page: Any, step_text: str) -> bool:
        lower = step_text.lower()
        if re.search(r"\b(search|recherche)\b", lower) and any(term in lower for term in ("icon", "button", "loupe", "icone", "icône")):
            self._click_search_icon(page)
            return True
        if any(term in lower for term in (" icon", " icone", " icône", " button", " bouton")) and self._click_icon_by_intent(page, step_text):
            page.wait_for_timeout(700)
            return True
        if any(term in lower for term in ("options", "more", "three dot", "three-dot", "ellipsis", "menu actions", "menu d")):
            if self._click_icon_by_intent(page, step_text):
                page.wait_for_timeout(700)
                return True
        if any(term in lower for term in ("confirm", "confirmer", "validate", "valider", "yes", "oui")):
            self._click_confirmation_action(page, accept=True)
            return True
        if any(term in lower for term in ("cancel", "annuler", "no", "non")) and any(term in lower for term in ("dialog", "confirmation", "modal")):
            self._click_confirmation_action(page, accept=False)
            return True
        if re.search(r"\benter\s+key\b|\btouche\s+entr[eÃ©]e\b|\bpress\s+enter\b", lower):
            page.keyboard.press("Enter")
            self._wait_after_search_submit(page)
            return True
        if any(term in lower for term in ("refresh icon", "refresh button", "reload icon", "actualiser", "rafraichir", "rafraÃ®chir")):
            self._click_refresh(page)
            return True
        if any(term in lower for term in ("profile menu", "user menu", "account menu", "avatar", "mon compte")):
            self._click_profile_menu(page)
            return True
        if any(term in lower for term in ("dÃ©connexion", "deconnexion", "logout", "log out", "sign out")):
            self._click_logout(page)
            return True
        return False

    def _is_logout_intent(self, value: str) -> bool:
        return bool(re.search(r"d.*connexion|logout|log\s*out|sign\s*out", value, re.I))

    def _click_profile_menu(self, page: Any) -> None:
        page.wait_for_load_state("domcontentloaded", timeout=30000)
        self._dismiss_transient_overlays(page)
        candidates = [
            page.get_by_role("button", name=re.compile(r"profile|account|user|vplm|mon\s+compte", re.I)),
            page.locator("button[aria-haspopup='menu']"),
            page.locator("button[aria-haspopup='true']"),
            page.locator("[role='button'][aria-haspopup='menu']"),
            page.locator("[role='button'][aria-haspopup='true']"),
            page.locator(".mat-mdc-menu-trigger"),
            page.locator("button:has(img)"),
            page.locator("[role='button']:has(img)"),
            page.locator("button").filter(has_text=re.compile(r"\bvplm\b|profile|account|mon\s+compte", re.I)),
        ]
        if self._click_first_visible(page, candidates):
            self._wait_for_profile_menu(page)
            return

        # Final visual fallback: profile menus are usually in the top-right header.
        header_buttons = page.locator("header button, nav button, .mat-toolbar button, .toolbar button, button")
        best = self._rightmost_visible_locator(header_buttons)
        if best is not None:
            self._safe_click(page, best)
            self._wait_for_profile_menu(page)
            return

        raise AssertionError("Could not find the profile menu on the current page")

    def _click_logout(self, page: Any) -> None:
        logout_pattern = re.compile(r"d.*connexion|logout|log\s*out|sign\s*out", re.I)
        candidates = [
            page.get_by_role("menuitem", name=logout_pattern),
            page.locator("[role='menuitem'], button, a, .mat-mdc-menu-item, .mat-menu-item, [mat-menu-item]").filter(has_text=logout_pattern),
            page.get_by_text(logout_pattern).locator("xpath=ancestor::*[self::button or self::a or @role='menuitem'][1]"),
            page.get_by_text(logout_pattern),
            page.get_by_role("menuitem", name=re.compile(r"d[eÃ©]connexion|logout|log\s*out|sign\s*out", re.I)),
            page.get_by_role("button", name=re.compile(r"d[eÃ©]connexion|logout|log\s*out|sign\s*out", re.I)),
            page.get_by_role("link", name=re.compile(r"d[eÃ©]connexion|logout|log\s*out|sign\s*out", re.I)),
            page.locator("[role='menuitem']").filter(has_text=re.compile(r"d[eÃ©]connexion|logout|log\s*out|sign\s*out", re.I)),
            page.get_by_text(re.compile(r"^\s*d[eÃ©]connexion\s*$|^\s*logout\s*$|^\s*log\s*out\s*$|^\s*sign\s*out\s*$", re.I)),
        ]
        if not self._any_visible_locator(candidates):
            self._click_profile_menu(page)
        if self._click_first_visible(page, candidates, dismiss_overlays=False):
            self._wait_after_logout(page)
            return
        raise AssertionError("Could not find the logout action in the current menu/page")

    def _click_confirmation_action(self, page: Any, accept: bool = True) -> None:
        pattern = re.compile(
            r"^(confirmer|valider|oui|yes|ok|confirm|validate)$" if accept
            else r"^(annuler|non|no|cancel|fermer|close)$",
            re.I,
        )
        containers = [
            page.locator("[role='dialog']"),
            page.locator(".mat-mdc-dialog-container"),
            page.locator(".mat-dialog-container"),
            page.locator(".cdk-overlay-pane"),
            page.locator("body"),
        ]
        candidates: List[Any] = []
        for container in containers:
            candidates.extend([
                container.get_by_role("button", name=pattern),
                container.locator("button, [role='button'], .mat-mdc-button, .mat-button").filter(has_text=pattern),
                container.get_by_text(pattern).locator("xpath=ancestor::*[self::button or @role='button'][1]"),
            ])
        if self._click_first_visible(page, candidates, dismiss_overlays=False):
            self._wait_for_ui_idle(page)
            return
        raise AssertionError("Could not find the confirmation action button")

    def _wait_after_logout(self, page: Any) -> None:
        try:
            page.wait_for_url(re.compile(r".*/(?:login|signin)(?:[/?#].*)?$", re.I), timeout=5000)
            return
        except Exception:
            pass
        try:
            page.get_by_text(re.compile(r"sign\s*in|username|password|connexion", re.I)).first.wait_for(
                state="visible",
                timeout=4000,
            )
            return
        except Exception as exc:
            raise AssertionError(f"Logout click did not navigate to Login page. Current URL is {page.url}") from exc

    def _any_visible_locator(self, candidates: List[Any]) -> bool:
        for locator in candidates:
            try:
                count = locator.count()
                for index in range(min(count, 4)):
                    if self._locator_is_visible(locator.nth(index)):
                        return True
            except Exception:
                continue
        return False

    def _wait_for_profile_menu(self, page: Any) -> None:
        try:
            page.get_by_text(re.compile(r"d[eÃ©]connexion|logout|mon\s+compte|conditions\s+g[eÃ©]n[eÃ©]rales", re.I)).first.wait_for(
                state="visible",
                timeout=5000,
            )
        except Exception:
            page.wait_for_timeout(500)

    def _click_first_visible(
        self,
        page: Any,
        candidates: List[Any],
        dismiss_overlays: bool = True,
    ) -> bool:
        for locator in candidates:
            try:
                count = locator.count()
                for index in range(min(count, 8)):
                    candidate = locator.nth(index)
                    if self._locator_is_visible(candidate):
                        self._safe_click(page, candidate, dismiss_overlays=dismiss_overlays)
                        return True
            except Exception:
                continue
        return False

    def _rightmost_visible_locator(self, locator: Any) -> Optional[Any]:
        best = None
        best_x = -1.0
        try:
            count = locator.count()
            for index in range(min(count, 40)):
                candidate = locator.nth(index)
                if not self._locator_is_visible(candidate):
                    continue
                box = candidate.bounding_box(timeout=1000)
                if not box:
                    continue
                x = float(box.get("x", 0)) + float(box.get("width", 0))
                if x > best_x:
                    best = candidate
                    best_x = x
        except Exception:
            return best
        return best

    def _click_by_intent(self, page: Any, step_text: str) -> None:
        lower = step_text.lower()
        if any(term in lower for term in ("refresh icon", "refresh button", "reload icon", "actualiser", "rafraichir", "rafraÃ®chir")):
            self._click_refresh(page)
            return
        if any(term in lower for term in ("profile menu", "user menu", "account menu", "avatar", "mon compte")):
            self._click_profile_menu(page)
            return
        if any(term in lower for term in ("dÃ©connexion", "deconnexion", "logout", "log out", "sign out")):
            self._click_logout(page)
            return
        if any(term in lower for term in ("options", "more", "three dot", "three-dot", "ellipsis", "menu actions", "menu d")):
            if self._click_icon_by_intent(page, step_text):
                page.wait_for_timeout(700)
                return
        if any(term in lower for term in ("confirm", "confirmer", "validate", "valider", "yes", "oui")):
            self._click_confirmation_action(page, accept=True)
            return
        if any(term in lower for term in ("cancel", "annuler", "no", "non")) and any(term in lower for term in ("dialog", "confirmation", "modal")):
            self._click_confirmation_action(page, accept=False)
            return
        if any(term in lower for term in ("sign in", "log in", "login", "submit")):
            candidates = [
                page.get_by_role("button", name=re.compile(r"sign\s*in|log\s*in|login|submit", re.I)),
                page.locator("button[type='submit']"),
                page.locator("input[type='submit']"),
            ]
            for locator in candidates:
                if locator.count() > 0:
                    self._safe_click(page, locator.first)
                    self._wait_after_submit(page)
                    return
        raise AssertionError(f"Could not find a clickable target for step: {step_text}")

    def _click_refresh(self, page: Any) -> None:
        page.wait_for_load_state("domcontentloaded", timeout=30000)
        candidates = [
            page.get_by_role("button", name=re.compile(r"refresh|reload|actualiser|rafra[iÃ®]chir", re.I)),
            page.locator("button[aria-label*='refresh' i], button[title*='refresh' i]"),
            page.locator("button[aria-label*='reload' i], button[title*='reload' i]"),
            page.locator("button[aria-label*='actualiser' i], button[title*='actualiser' i]"),
            page.locator("mat-icon").filter(has_text=re.compile(r"^\s*(refresh|cached|sync)\s*$", re.I)).locator(
                "xpath=ancestor::button[1]"
            ),
            page.locator(".material-icons, .mat-icon").filter(has_text=re.compile(r"^\s*(refresh|cached|sync)\s*$", re.I)).locator(
                "xpath=ancestor::*[self::button or @role='button'][1]"
            ),
        ]
        if self._click_first_visible(page, candidates):
            self._wait_after_refresh(page)
            return

        # VPLM grids sometimes expose the refresh icon only as a textless icon button
        # immediately after the Ajouter button.
        try:
            add_button = page.get_by_role("button", name=re.compile(r"ajouter|add", re.I)).first
            if self._locator_is_visible(add_button):
                sibling_button = add_button.locator("xpath=following::button[1]")
                if self._locator_is_visible(sibling_button):
                    self._safe_click(page, sibling_button)
                    self._wait_after_refresh(page)
                    return
        except Exception:
            pass

        page.reload(wait_until="domcontentloaded", timeout=30000)
        self._wait_after_refresh(page)

    def _wait_after_refresh(self, page: Any) -> None:
        try:
            page.wait_for_load_state("networkidle", timeout=8000)
        except Exception:
            try:
                page.wait_for_load_state("domcontentloaded", timeout=8000)
            except Exception:
                pass
        page.wait_for_timeout(1200)

    def _wait_for_ui_idle(self, page: Any) -> None:
        timeout_ms = int(os.getenv("E2E_UI_IDLE_TIMEOUT_MS", "20000"))
        deadline = time.time() + timeout_ms / 1000
        while time.time() < deadline:
            try:
                page.wait_for_load_state("networkidle", timeout=1500)
            except Exception:
                pass
            if not self._save_busy_observed(page) and not self._generic_busy_observed(page):
                try:
                    page.wait_for_timeout(350)
                except Exception:
                    pass
                return
            try:
                page.wait_for_timeout(500)
            except Exception:
                pass

    def _generic_busy_observed(self, page: Any) -> bool:
        script = """
        () => {
          const isVisible = (element) => {
            const rect = element.getBoundingClientRect();
            const style = window.getComputedStyle(element);
            return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
          };
          const selectors = [
            "[aria-busy='true']",
            ".loading",
            ".loader",
            ".spinner",
            ".mat-mdc-progress-spinner",
            ".mat-progress-spinner",
            ".mat-mdc-progress-bar",
            ".mat-progress-bar",
            "[role='progressbar']"
          ];
          return Array.from(document.querySelectorAll(selectors.join(","))).some(isVisible);
        }
        """
        try:
            return bool(page.evaluate(script))
        except Exception:
            return False

    def _wait_after_submit(self, page: Any) -> None:
        try:
            page.wait_for_timeout(500)
        except Exception:
            pass
        if "/login" in str(page.url or "").lower() or "signin" in str(page.url or "").lower():
            try:
                form = page.locator("form").first
                if form.count() > 0:
                    form.evaluate("(element) => element.requestSubmit()")
            except Exception:
                pass
        self._wait_for_login_completion(page)

    def _wait_for_login_completion(self, page: Any) -> None:
        timeout_ms = int(os.getenv("E2E_LOGIN_TIMEOUT_MS", "45000"))
        deadline = time.time() + timeout_ms / 1000
        last_url = str(page.url or "")
        while time.time() < deadline:
            try:
                page.wait_for_load_state("domcontentloaded", timeout=2000)
            except Exception:
                pass
            try:
                page.wait_for_load_state("networkidle", timeout=1200)
            except Exception:
                pass

            last_url = str(page.url or "")
            lower_url = last_url.lower()
            if self._home_state_reached(page):
                return
            if "login" not in lower_url and "signin" not in lower_url:
                try:
                    page.wait_for_timeout(800)
                except Exception:
                    pass
                if self._home_state_reached(page) or not self._login_form_visible(page):
                    return

            try:
                page.wait_for_timeout(700)
            except Exception:
                pass
        if self._home_state_reached(page):
            return
        raise AssertionError(f"Login did not complete within {timeout_ms // 1000}s. Current URL is {last_url}")

    def _home_state_reached(self, page: Any) -> bool:
        url = str(page.url or "").lower()
        if "login" not in url and any(token in url for token in ("/home", "/portal/home", "/dashboard")):
            return True
        return self._any_visible_text(page, [
            r"\bbonjour\s+vplm\b",
            r"\bbienvenue\s+sur\s+visIativ\s+plm\b",
            r"\bmes\s+activit[eé]s\s+r[eé]centes\b",
            r"\bmes\s+derniers\s+objets\s+verrouill[eé]s\b",
            r"\bmes\s+services\b",
            r"\bmon\s+portail\b",
            r"\bmes\s+applications\b",
        ]) and not self._login_form_visible(page)

    def _login_form_visible(self, page: Any) -> bool:
        try:
            if "login" in str(page.url or "").lower() or "signin" in str(page.url or "").lower():
                if self._any_visible_text(page, [r"\busername\b", r"\bpassword\b", r"\bsign\s*in\b"]):
                    return True
            return bool(page.evaluate(
                """() => {
                  const isVisible = (element) => {
                    const rect = element.getBoundingClientRect();
                    const style = window.getComputedStyle(element);
                    return rect.width > 0 && rect.height > 0 && style.display !== "none" && style.visibility !== "hidden";
                  };
                  const password = Array.from(document.querySelectorAll("input[type='password']")).some(isVisible);
                  const text = document.body ? (document.body.innerText || document.body.textContent || "") : "";
                  return password && /username|password|sign\\s*in|connexion/i.test(text);
                }"""
            ))
        except Exception:
            return False

    def _first_arg(self, args: Dict[str, Any], project_context: Optional[Dict[str, Any]] = None) -> str:
        for value in args.values():
            if isinstance(value, str) and value.startswith("${SECRET."):
                return self._resolve_secret(value, project_context or {})
            return str(value)
        raise ValueError("Step requires an argument but none was provided")

    def _resolve_step_arguments(
        self,
        args: Dict[str, Any],
        project_context: Dict[str, Any],
        runtime_values: Dict[str, str],
    ) -> Dict[str, Any]:
        return {
            key: self._resolve_step_value(value, project_context, runtime_values)
            for key, value in args.items()
        }

    def _resolve_step_value(
        self,
        value: Any,
        project_context: Dict[str, Any],
        runtime_values: Dict[str, str],
    ) -> Any:
        if not isinstance(value, str):
            return value
        stripped = value.strip()
        if re.fullmatch(r"\$\{SECRET\.[^}]+\}", stripped, re.I):
            return self._resolve_secret(stripped, project_context)

        def replace_runtime(match: re.Match[str]) -> str:
            return self._runtime_value(match.group(1), runtime_values)

        return re.sub(r"\$\{(?:RUN|LLM)\.([^}]+)\}", replace_runtime, value, flags=re.I)

    def _runtime_value(self, key: str, runtime_values: Dict[str, str]) -> str:
        canonical = self._canonical_runtime_key(key)
        if canonical not in runtime_values:
            if canonical == "password":
                raise ValueError("Password values must come from Vault secrets, for example ${SECRET.password}.")
            if canonical in {"articleReference", "articleDesignation"}:
                self._ensure_llm_entity_values(runtime_values, "article")
            elif canonical in {"projectReference", "projectDesignation"}:
                self._ensure_llm_entity_values(runtime_values, "project")
            elif canonical in {"objectReference", "objectDesignation"}:
                self._ensure_llm_entity_values(
                    runtime_values,
                    "object",
                    runtime_values.get("currentObjectType") or "Objet VPLM",
                )
            else:
                runtime_values[canonical] = self._generate_llm_runtime_value(canonical, runtime_values)
                for alias in self._runtime_aliases(canonical):
                    runtime_values.setdefault(alias, runtime_values[canonical])
        return runtime_values[canonical]

    def _canonical_runtime_key(self, key: str) -> str:
        normalized = re.sub(r"[^a-z0-9]+", "", key.lower())
        if "password" in normalized:
            return "password"
        if "email" in normalized or "mail" in normalized:
            return "email"
        if "username" in normalized or "login" in normalized or "user" in normalized:
            return "username"
        if "description" in normalized or "comment" in normalized:
            return "description"
        if "project" in normalized and ("reference" in normalized or "ref" in normalized):
            return "projectReference"
        if "project" in normalized and ("designation" in normalized or "title" in normalized or "name" in normalized):
            return "projectDesignation"
        if "article" in normalized and ("reference" in normalized or "ref" in normalized):
            return "articleReference"
        if "article" in normalized and ("designation" in normalized or "title" in normalized or "name" in normalized):
            return "articleDesignation"
        if "reference" in normalized or normalized in {"ref", "createdref"}:
            return "objectReference"
        if "designation" in normalized or "title" in normalized or "name" in normalized:
            return "objectDesignation"
        return key.strip() or "value"

    def _runtime_aliases(self, canonical: str) -> List[str]:
        if canonical == "articleReference":
            return ["reference", "ref", "createdArticleReference"]
        if canonical == "articleDesignation":
            return ["designation", "name", "title", "createdArticleDesignation"]
        if canonical == "projectReference":
            return ["createdProjectReference", "projectRef"]
        if canonical == "projectDesignation":
            return ["createdProjectDesignation", "projectName", "projectTitle"]
        if canonical == "objectReference":
            return ["reference", "ref", "createdReference", "createdObjectReference"]
        if canonical == "objectDesignation":
            return ["designation", "name", "title", "createdDesignation", "createdObjectDesignation"]
        if canonical == "email":
            return ["mail", "generatedEmail", "createdEmail"]
        if canonical == "username":
            return ["login", "user", "generatedUsername"]
        if canonical == "description":
            return ["comment", "generatedDescription"]
        return []

    def _llm_input_value_for_step(self, page: Any, step_text: str, runtime_values: Dict[str, str]) -> str:
        key = self._runtime_key_from_step_text(step_text)
        if key == "password":
            raise ValueError("The test asks the LLM to invent a password. Use a Vault secret instead, such as ${SECRET.password}.")
        if key in {"objectReference", "objectDesignation"}:
            object_type = self._detect_current_object_type(page, runtime_values)
            self._ensure_llm_entity_values(runtime_values, "object", object_type)
            return runtime_values[key]
        return self._runtime_value(key, runtime_values)

    def _runtime_key_from_step_text(self, text: str) -> str:
        lower = str(text or "").lower()
        if re.search(r"\b(r[eÃ©]f[eÃ©]rence|reference|ref)\b", lower, re.I):
            if "project" in lower or "projet" in lower:
                return "projectReference"
            if "article" in lower:
                return "articleReference"
            return "objectReference"
        if re.search(r"\b(d[eÃ©]signation|designation)\b", lower, re.I):
            if "project" in lower or "projet" in lower:
                return "projectDesignation"
            if "article" in lower:
                return "articleDesignation"
            return "objectDesignation"
        if re.search(r"\b(password|mot de passe)\b", lower, re.I):
            return "password"
        if re.search(r"\b(e-?mail|courriel)\b", lower, re.I):
            return "email"
        if re.search(r"\b(username|user name|login|utilisateur)\b", lower, re.I):
            return "username"
        if re.search(r"\b(description|comment|commentaire)\b", lower, re.I):
            return "description"
        if re.search(r"\b(title|titre|name|nom)\b", lower, re.I):
            if "project" in lower or "projet" in lower:
                return "projectDesignation"
            return "articleDesignation" if "article" in lower else "objectDesignation"
        cleaned = re.sub(r"[^a-z0-9]+", "_", lower).strip("_")
        return cleaned[:48] or "value"

    def _ensure_llm_entity_values(
        self,
        runtime_values: Dict[str, str],
        entity: str,
        object_label: Optional[str] = None,
    ) -> None:
        reference_key = f"{entity}Reference"
        designation_key = f"{entity}Designation"
        if runtime_values.get(reference_key) and runtime_values.get(designation_key):
            return
        entity_label = object_label or ("Article concu" if entity == "article" else "Projet")
        if entity == "object":
            runtime_values["currentObjectType"] = entity_label
        prompt = f"""
You are the test-data agent for a VPLM quality-assurance E2E test.

Choose realistic input values for creating one new "{entity_label}" object.
The values must be unique enough for an automated test run and logically related.

Context:
- Current UTC time: {datetime.now(timezone.utc).isoformat()}
- Target field 1: Reference
- Target field 2: Designation
- The reference will be typed into the Reference field.
- The designation will be typed into the Designation field.
- Later test steps will search for exactly these two values in the grid.

Return JSON only with this exact shape:
{{
  "reference": "short uppercase alphanumeric reference without spaces",
  "designation": "human-readable designation linked to the reference",
  "fallback": {{
    "referencePrefix": "short prefix that fits this object class",
    "designationTemplate": "short human template for this object class"
  }}
}}
"""
        try:
            data, _response = generate_json(prompt)
            self._remember_llm_fallback_strategy(runtime_values, entity_label, data.get("fallback"))
        except Exception as exc:
            data = self._fallback_entity_test_inputs(entity, runtime_values, str(exc), entity_label)

        reference = self._clean_llm_test_value(data.get("reference") or data.get(reference_key), uppercase=True)
        designation = self._clean_llm_test_value(data.get("designation") or data.get(designation_key), uppercase=False)
        if not reference or not re.fullmatch(r"[A-Z0-9_-]{3,32}", reference):
            raise AssertionError(f"LLM generated an invalid {entity} reference: {reference!r}")
        if not designation or len(designation) > 80:
            raise AssertionError(f"LLM generated an invalid {entity} designation: {designation!r}")

        runtime_values[reference_key] = reference
        runtime_values[designation_key] = designation
        runtime_values["llmGeneratedData"] = str(data.get("_source") or "llm")
        if entity == "object":
            self._mirror_object_values_to_specific_aliases(entity_label, reference, designation, runtime_values)
        for alias in self._runtime_aliases(reference_key):
            runtime_values.setdefault(alias, reference)
        for alias in self._runtime_aliases(designation_key):
            runtime_values.setdefault(alias, designation)

    def _remember_llm_fallback_strategy(
        self,
        runtime_values: Dict[str, str],
        object_label: str,
        fallback: Any,
    ) -> None:
        if not isinstance(fallback, dict):
            return
        prefix = self._clean_llm_test_value(fallback.get("referencePrefix"), uppercase=True)
        template = self._clean_llm_test_value(fallback.get("designationTemplate"), uppercase=False)
        if not prefix and not template:
            return
        strategy = {
            "referencePrefix": re.sub(r"[^A-Z0-9]", "", prefix)[:8] or "TST",
            "designationTemplate": template[:60] or f"{object_label} test",
        }
        runtime_values[f"llmFallbackStrategy.{self._object_type_slug(object_label)}"] = json.dumps(
            strategy,
            ensure_ascii=False,
        )

    def _mirror_object_values_to_specific_aliases(
        self,
        object_label: str,
        reference: str,
        designation: str,
        runtime_values: Dict[str, str],
    ) -> None:
        normalized = self._object_type_slug(object_label)
        if "article" in normalized:
            runtime_values.setdefault("articleReference", reference)
            runtime_values.setdefault("articleDesignation", designation)
        if "projet" in normalized or "project" in normalized:
            runtime_values.setdefault("projectReference", reference)
            runtime_values.setdefault("projectDesignation", designation)

    def _detect_current_object_type(self, page: Any, runtime_values: Dict[str, str]) -> str:
        detected = self._object_type_from_page(page)
        if detected:
            runtime_values["currentObjectType"] = detected
            return detected
        return runtime_values.get("currentObjectType") or "Objet VPLM"

    def _object_type_from_page(self, page: Any) -> str:
        try:
            text = page.locator("body").inner_text(timeout=2000)
        except Exception:
            return ""
        patterns = (
            r"Cr[ée]ation d['’]objet\s*-\s*([^\n\r]+)",
            r"Creation d['’]objet\s*-\s*([^\n\r]+)",
            r"Création d.objet\s*-\s*([^\n\r]+)",
        )
        for pattern in patterns:
            match = re.search(pattern, text, re.I)
            if match:
                return self._clean_object_type_label(match.group(1))
        return ""

    def _remember_object_context_from_value(
        self,
        step_text: str,
        value: str,
        runtime_values: Dict[str, str],
    ) -> None:
        candidate = self._clean_object_type_label(value)
        if not candidate:
            return
        lower_step = str(step_text or "").lower()
        lower_candidate = candidate.lower()
        control_words = {
            "ajouter", "add", "confirmer", "confirm", "enregistrer", "save", "login",
            "déconnexion", "deconnexion", "logout", "annuler", "cancel",
        }
        if lower_candidate in control_words:
            return
        class_terms = (
            "article", "projet", "project", "document", "contact", "piece", "pièce",
            "plan", "dossier", "donnee", "donnée", "classe", "sav", "cao",
        )
        if "classe" in lower_step or any(term in lower_candidate for term in class_terms):
            runtime_values["currentObjectType"] = candidate

    def _clean_object_type_label(self, value: str) -> str:
        cleaned = re.sub(r"\s+", " ", str(value or "").strip().strip("\"'"))
        cleaned = re.sub(r"\s*(confirmer|ajouter|favori|favorite)\s*$", "", cleaned, flags=re.I).strip()
        return cleaned[:80]

    def _object_type_slug(self, object_label: str) -> str:
        return re.sub(r"[^a-z0-9]+", "", str(object_label or "").lower()) or "objet"

    def _fallback_entity_test_inputs(
        self,
        entity: str,
        runtime_values: Dict[str, str],
        reason: str = "",
        object_label: Optional[str] = None,
    ) -> Dict[str, str]:
        suffix = self._runtime_suffix(runtime_values)
        label = object_label or ("Projet" if entity == "project" else "Article concu" if entity == "article" else "Objet VPLM")
        strategy = self._fallback_strategy_for_object(label, runtime_values)
        prefix = strategy["referencePrefix"]
        template = strategy["designationTemplate"]
        reference = f"{prefix}{suffix}"[:32]
        designation = f"{template} {suffix}"[:80]
        fallback = {
            "reference": reference,
            "designation": designation,
            "_source": "fallback",
        }
        if reason:
            runtime_values.setdefault("llmFallbackReason", reason[:240])
        return fallback

    def _fallback_strategy_for_object(self, object_label: str, runtime_values: Dict[str, str]) -> Dict[str, str]:
        cached = runtime_values.get(f"llmFallbackStrategy.{self._object_type_slug(object_label)}")
        if cached:
            try:
                payload = json.loads(cached)
                prefix = re.sub(r"[^A-Z0-9]", "", str(payload.get("referencePrefix") or "").upper())[:8]
                template = self._clean_llm_test_value(payload.get("designationTemplate"), uppercase=False)
                if prefix and template:
                    return {"referencePrefix": prefix, "designationTemplate": template[:60]}
            except Exception:
                pass
        return {
            "referencePrefix": self._default_reference_prefix(object_label),
            "designationTemplate": self._default_designation_template(object_label),
        }

    def _default_reference_prefix(self, object_label: str) -> str:
        normalized = self._object_type_slug(object_label)
        if "projet" in normalized or "project" in normalized:
            return "PRJ"
        if "document" in normalized:
            return "DOC"
        if "contact" in normalized:
            return "CTC"
        if "dossier" in normalized:
            return "DOS"
        if "plan" in normalized:
            return "PLN"
        if "piece" in normalized or "pièce" in normalized or "article" in normalized:
            return "PRT"
        if "donnee" in normalized or "donnée" in normalized:
            return "DAT"
        return "TST"

    def _default_designation_template(self, object_label: str) -> str:
        label = self._clean_object_type_label(object_label) or "Objet VPLM"
        return f"{label} test"

    def _runtime_suffix(self, runtime_values: Dict[str, str]) -> str:
        existing = "|".join(f"{key}={value}" for key, value in sorted(runtime_values.items()))
        seed = f"{datetime.now(timezone.utc).isoformat()}|{existing}|{random.random()}"
        return re.sub(r"[^A-Z0-9]", "", hashlib.sha1(seed.encode("utf-8")).hexdigest().upper())[:8]

    def _generate_llm_runtime_value(self, key: str, runtime_values: Dict[str, str]) -> str:
        prompt = f"""
You are the test-data agent for a VPLM quality-assurance E2E test.

Choose one realistic value for this dynamic test input.

Input intent/key: {key}
- Current UTC time: {datetime.now(timezone.utc).isoformat()}
- Already chosen values: {json.dumps(runtime_values, ensure_ascii=False)}

Return JSON only with this exact shape:
{{"value": "the generated value"}}
"""
        try:
            data, _response = generate_json(prompt)
        except Exception as exc:
            data = {"value": self._fallback_runtime_value(key, runtime_values), "_source": "fallback"}
            runtime_values.setdefault("llmFallbackReason", str(exc)[:240])
        value = self._clean_llm_test_value(data.get("value"), uppercase=False)
        if not value:
            raise AssertionError(f"LLM generated an empty runtime input for '{key}'")
        return value

    def _fallback_runtime_value(self, key: str, runtime_values: Dict[str, str]) -> str:
        suffix = self._runtime_suffix(runtime_values)
        normalized = re.sub(r"[^a-z0-9]+", "", str(key).lower())
        if "email" in normalized or "mail" in normalized:
            return f"test.{suffix.lower()}@example.com"
        if "username" in normalized or "login" in normalized or "user" in normalized:
            return f"user_{suffix.lower()}"
        if "description" in normalized or "comment" in normalized:
            return f"Description de test {suffix}"
        if "date" in normalized:
            return datetime.now(timezone.utc).strftime("%d/%m/%Y")
        if "reference" in normalized or normalized in {"ref"}:
            return f"REF{suffix}"
        if "designation" in normalized or "title" in normalized or "name" in normalized or "nom" in normalized:
            return f"Element test {suffix}"
        return f"Test {suffix}"

    def _clean_llm_test_value(self, value: Any, uppercase: bool = False) -> str:
        cleaned = str(value or "").strip().strip("\"'")
        cleaned = re.sub(r"\s+", " ", cleaned)
        if uppercase:
            cleaned = cleaned.upper().replace(" ", "_")
        return cleaned

    def _remember_runtime_input(self, step_text: str, value: str, runtime_values: Dict[str, str]) -> None:
        if not value:
            return
        lower = str(step_text or "").lower()
        if re.search(r"\b(r[eÃ©]f[eÃ©]rence|reference|ref)\b", step_text, re.I):
            if "project" in lower or "projet" in lower:
                key = "projectReference"
            elif "article" in lower:
                key = "articleReference"
            else:
                key = "objectReference"
            runtime_values.setdefault(key, value)
            for alias in self._runtime_aliases(key):
                runtime_values.setdefault(alias, value)
        if re.search(r"\b(d[eÃ©]signation|designation|title|name)\b", step_text, re.I):
            if "project" in lower or "projet" in lower:
                key = "projectDesignation"
            elif "article" in lower:
                key = "articleDesignation"
            else:
                key = "objectDesignation"
            runtime_values.setdefault(key, value)
            for alias in self._runtime_aliases(key):
                runtime_values.setdefault(alias, value)

    def _resolve_runtime_phrase(self, text: str, runtime_values: Dict[str, str]) -> str:
        lower = str(text or "").lower()
        if not any(term in lower for term in (
            "created", "generated", "new", "example", "valid", "sample", "random", "matching", "same"
        )):
            return ""
        key = self._runtime_key_from_step_text(lower)
        if not key or key == "value":
            return ""
        return self._runtime_value(key, runtime_values)

    def _resolve_secret(self, placeholder: str, project_context: Dict[str, Any]) -> str:
        alias = placeholder.removeprefix("${SECRET.").removesuffix("}").strip().lower()
        project_id = str(project_context.get("projectId") or "").strip()
        if not project_id:
            raise ValueError("A project is required to resolve confidential E2E values")
        token_path = Path(os.getenv("VAULT_TOKEN_FILE", "/run/secrets/vault_token"))
        if not token_path.exists():
            raise ValueError(f"Vault token is not mounted at {token_path}")
        vault_addr = os.getenv("VAULT_ADDR", "http://vault:8200").rstrip("/")
        request = urllib.request.Request(
            f"{vault_addr}/v1/secret/data/vplmqa/projects/{project_id}/e2e/{alias}",
            headers={"X-Vault-Token": token_path.read_text(encoding="utf-8").strip()},
        )
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            if exc.code == 404:
                raise ValueError(
                    f"Missing Vault E2E secret alias '{alias}' for project {project_id}. "
                    f"Create secret/vplmqa/projects/{project_id}/e2e/{alias} with field 'value'."
                ) from exc
            raise ValueError(f"Vault E2E secret alias '{alias}' could not be read: HTTP {exc.code}") from exc
        return str(payload["data"]["data"]["value"])

    def _capture_screenshot(self, page: Any, step: Dict[str, Any], full_page: bool = True) -> str:
        step_id = step.get("step_id", "unknown")
        path = Path(os.getenv("STEP_FUNCTION_STORAGE_ROOT", "/app/output")) / "execution-artifacts" / step_id / "failure.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        try:
            page.bring_to_front()
        except Exception:
            pass
        page.screenshot(path=str(path), full_page=full_page, timeout=10000)
        return str(path)

    def _capture_cdp_screenshot(self, page: Any, step: Dict[str, Any]) -> str:
        step_id = step.get("step_id", "unknown")
        path = Path(os.getenv("STEP_FUNCTION_STORAGE_ROOT", "/app/output")) / "execution-artifacts" / step_id / "failure.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        session = page.context.new_cdp_session(page)
        payload = session.send("Page.captureScreenshot", {"format": "png", "captureBeyondViewport": False})
        path.write_bytes(base64.b64decode(payload["data"]))
        return str(path)

    def _write_screenshot_failure_card(self, step: Dict[str, Any], reason: str) -> str:
        step_id = step.get("step_id", "unknown")
        path = Path(os.getenv("STEP_FUNCTION_STORAGE_ROOT", "/app/output")) / "execution-artifacts" / step_id / "failure.svg"
        path.parent.mkdir(parents=True, exist_ok=True)
        message = html.escape(reason[:320])
        step_text = html.escape(str(step.get("raw_text") or "Failed step"))
        path.write_text(
            f"""<svg xmlns="http://www.w3.org/2000/svg" width="960" height="420" viewBox="0 0 960 420">
  <rect width="960" height="420" fill="#19002b"/>
  <rect x="36" y="36" width="888" height="348" rx="18" fill="#fff7ed" stroke="#fb923c" stroke-width="3"/>
  <text x="72" y="110" font-family="Arial, sans-serif" font-size="30" font-weight="700" fill="#9a3412">Browser screenshot could not be captured</text>
  <text x="72" y="170" font-family="Arial, sans-serif" font-size="22" fill="#1c0538">{step_text}</text>
  <foreignObject x="72" y="210" width="820" height="120">
    <div xmlns="http://www.w3.org/1999/xhtml" style="font-family:Consolas,monospace;font-size:18px;color:#7f1d1d;line-height:1.35;word-break:break-word;">{message}</div>
  </foreignObject>
</svg>
""",
            encoding="utf-8",
        )
        return str(path)

    def _hold_browser_open(self) -> None:
        seconds = int(os.getenv("E2E_BROWSER_HOLD_OPEN_SECONDS", "15"))
        if seconds > 0:
            time.sleep(seconds)

    def _first_step(self, plan: Dict[str, Any]) -> Dict[str, Any]:
        for scenario in plan.get("scenarios", []):
            for step in scenario.get("steps", []):
                return {"scenario": scenario, "step": step}
        return {"scenario": {}, "step": {}}


def default_cost_policy() -> Dict[str, Any]:
    return {
        "flash": [
            "normalization", "classification", "simple_step_generation",
            "complex_generation", "repair", "semantic_matching",
        ],
        "pro": [],
    }


def model_for_step(step: Dict[str, Any]) -> str:
    complex_terms = ("table", "upload", "download", "payment", "checkout", "file")
    if any(term in step["normalized_text"].lower() for term in complex_terms):
        return "gemini-flash"
    return "gemini-flash"


