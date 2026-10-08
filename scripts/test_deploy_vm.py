import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location("deploy_vm", Path(__file__).with_name("deploy-vm.py"))
deploy_vm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy_vm)


class DeploymentTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="vplmqa-vm-test-")
        self.addCleanup(self.directory.cleanup)
        self.repo = Path(self.directory.name)
        self.key = self.repo / "input-key.txt"
        self.key.write_text("test-key-not-a-real-credential", encoding="utf-8")

    def prepare(self, origin="http://10.0.0.50:3000"):
        return deploy_vm.prepare(self.repo, origin, "mistral-small-latest", self.key)

    def test_generated_settings_and_internal_browser(self):
        env, override = self.prepare()
        values = deploy_vm.read_env(env)
        self.assertGreaterEqual(len(values["JWT_SECRET"]), 64)
        config = json.loads(override.read_text())
        self.assertEqual(config["name"], "vplmqa-vm")
        agents = config["services"]["langgraph-agents"]["environment"]
        self.assertEqual(agents["E2E_BROWSER_CDP_URL"], "")
        self.assertEqual(agents["E2E_BROWSER_HEADLESS"], "true")
        self.assertEqual(agents["E2E_ARTIFACT_PUBLIC_BASE_URL"], "http://10.0.0.50:3000/artifacts")
        for name, (_, database) in deploy_vm.SPRING.items():
            service = config["services"][name]["environment"]
            self.assertEqual(service["DB_HOST"], "postgres")
            self.assertEqual(service["DB_NAME"], database)
            self.assertIn("config-service:8087", service["SPRING_CONFIG_IMPORT"])
        self.assertNotIn("test-key-not-a-real-credential", override.read_text())
        self.assertNotIn("test-key-not-a-real-credential", env.read_text())

    def test_repeat_prepare_preserves_passwords_and_local_env(self):
        local_env = self.repo / ".env"
        local_env.write_text("LOCAL_ONLY=keep-this\n", encoding="utf-8")
        env, _ = self.prepare()
        before = deploy_vm.read_env(env)
        self.prepare("http://10.0.0.51:3000")
        after = deploy_vm.read_env(env)
        for name in ("POSTGRES_PASSWORD", "JWT_SECRET", "VAULT_TOKEN"):
            self.assertEqual(before[name], after[name])
        self.assertEqual(local_env.read_text(), "LOCAL_ONLY=keep-this\n")

    def test_input_validation(self):
        for value in ("https://example.com", "http://example.com", "http://user:pass@vm:3000", "http://vm:3000/path"):
            with self.assertRaises(ValueError):
                deploy_vm.public_origin(value)
        self.assertEqual(deploy_vm.public_origin("http://vm:3000/"), "http://vm:3000")
        self.key.write_text("", encoding="utf-8")
        with self.assertRaises(ValueError):
            self.prepare()
        self.assertFalse((self.repo / ".deployment/vm.env").exists())

    def test_readiness_rejects_down_gateway(self):
        response = mock.MagicMock()
        response.__enter__.return_value = response
        response.status = 200
        with mock.patch.object(deploy_vm.urllib.request, "urlopen", return_value=response), \
             mock.patch.object(deploy_vm.json, "load", return_value={"status": "DOWN"}), \
             mock.patch.object(deploy_vm.time, "monotonic", side_effect=[0, 2]):
            with self.assertRaisesRegex(RuntimeError, "gateway"):
                deploy_vm.check_ready(1)

    @unittest.skipUnless(shutil.which("docker"), "Docker Compose is not installed")
    def test_nginx_configuration(self):
        image_name = next((name for name in ("nginx:alpine", "vplmqa/front-end-local")
                           if subprocess.run(["docker", "image", "inspect", name], capture_output=True).returncode == 0), None)
        if not image_name:
            self.skipTest("No local Nginx image is available")
        self.prepare()
        config = self.repo / "front-end/.vm-nginx.conf"
        result = subprocess.run([
            "docker", "run", "--rm", "--pull", "never",
            "--add-host", "gateway:127.0.0.1", "--add-host", "minio:127.0.0.1",
            "--add-host", "langgraph-agents:127.0.0.1",
            "--mount", f"type=bind,source={config},target=/etc/nginx/conf.d/default.conf,readonly",
            image_name, "nginx", "-t",
        ], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    @unittest.skipUnless(shutil.which("docker"), "Docker Compose is not installed")
    def test_real_compose_merge(self):
        # Validate the real includes and override without touching the running stack.
        source = deploy_vm.REPO
        files = [source / "docker-compose.yml"]
        files.extend(source.glob("*/docker-compose.yml"))
        files.extend(source.glob("infra/*/docker-compose.*.yml"))
        for file in files:
            target = self.repo / file.relative_to(source)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(file, target)
        self.prepare()
        result = subprocess.run(deploy_vm.compose_command(self.repo) + ["config", "--format", "json"],
                                capture_output=True, text=True)
        # Only stderr is reported; resolved stdout includes generated secrets.
        self.assertEqual(result.returncode, 0, result.stderr)
        resolved = json.loads(result.stdout)
        gateway = resolved["services"]["gateway"]["environment"]
        self.assertEqual(gateway["DB_HOST"], "postgres")
        self.assertEqual(gateway["SECURITY_JWT_SECRET"], deploy_vm.read_env(self.repo / ".deployment/vm.env")["JWT_SECRET"])
        frontend = resolved["services"]["front-end"]["build"]["args"]
        self.assertEqual(frontend["NGINX_CONF"], ".vm-nginx.conf")
        cors = json.loads(gateway["SPRING_APPLICATION_JSON"])
        origins = cors["spring"]["cloud"]["gateway"]["globalcors"]["cors-configurations"]["[/**]"]["allowedOrigins"]
        self.assertEqual(origins, ["http://10.0.0.50:3000"])


if __name__ == "__main__":
    unittest.main()
