import json
import os
import socket
import subprocess
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ENV_FILE = ROOT / ".env"
PORT = int(os.environ.get("E2E_CHROME_LAUNCHER_PORT", "9223"))
CDP_PORT = int(os.environ.get("E2E_CHROME_DEBUG_PORT", "9222"))
BIND_ADDRESS = os.environ.get("E2E_CHROME_LAUNCHER_BIND_ADDRESS", "127.0.0.1")
CDP_ADDRESS = os.environ.get("E2E_CHROME_DEBUG_ADDRESS", "127.0.0.1")
CDP_PROBE_ADDRESS = "127.0.0.1" if CDP_ADDRESS in {"0.0.0.0", "::"} else CDP_ADDRESS
DOCKER_CDP_URL = os.environ.get("E2E_BROWSER_CDP_URL", f"http://192.168.65.254:{CDP_PORT}")
PROFILE_DIR = Path(os.environ.get("E2E_CHROME_PROFILE_DIR", r"C:\tmp\vplmqa-chrome-profile"))
PID_FILE = PROFILE_DIR / "runner.pid"
LOG_FILE = Path(os.environ.get("E2E_CHROME_RUNNER_LOG", r"C:\tmp\vplmqa-e2e-chrome-runner.log"))
CHROME_PATHS = [
    Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe"),
    Path(r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"),
]

chrome_process: subprocess.Popen | None = None


def log(message: str) -> None:
    LOG_FILE.parent.mkdir(parents=True, exist_ok=True)
    timestamp = time.strftime("%Y-%m-%d %H:%M:%S")
    with LOG_FILE.open("a", encoding="utf-8") as handle:
        handle.write(f"[{timestamp}] {message}\n")


def find_chrome() -> Path:
    configured = os.environ.get("E2E_CHROME_PATH")
    if configured and Path(configured).exists():
        return Path(configured)
    for path in CHROME_PATHS:
        if path.exists():
            return path
    raise RuntimeError("Chrome was not found. Install Google Chrome or set E2E_CHROME_PATH.")


def port_open(host: str, port: int) -> bool:
    try:
        with socket.create_connection((host, port), timeout=0.5):
            return True
    except OSError:
        return False


def start_chrome() -> None:
    global chrome_process
    PROFILE_DIR.mkdir(parents=True, exist_ok=True)
    if not port_open(CDP_PROBE_ADDRESS, CDP_PORT):
        chrome = find_chrome()
        log(f"Launching Chrome with CDP on {CDP_ADDRESS}:{CDP_PORT}.")
        chrome_process = subprocess.Popen(
            [
                str(chrome),
                f"--remote-debugging-port={CDP_PORT}",
                f"--remote-debugging-address={CDP_ADDRESS}",
                f"--user-data-dir={PROFILE_DIR}",
                "--ignore-certificate-errors",
                "--new-window",
                "about:blank",
            ],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        PID_FILE.write_text(str(chrome_process.pid), encoding="ascii")

    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        if port_open(CDP_PROBE_ADDRESS, CDP_PORT):
            return
        time.sleep(0.25)
    raise RuntimeError(f"Chrome was launched, but debug port {CDP_ADDRESS}:{CDP_PORT} is not reachable.")


def stop_chrome() -> None:
    global chrome_process
    process_id = chrome_process.pid if chrome_process and chrome_process.poll() is None else None
    if process_id is None and PID_FILE.exists():
        try:
            process_id = int(PID_FILE.read_text(encoding="ascii").strip())
        except (OSError, ValueError):
            process_id = None
    if process_id:
        log(f"Closing Chrome process {process_id}.")
        subprocess.run(["taskkill.exe", "/PID", str(process_id), "/T", "/F"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    PID_FILE.unlink(missing_ok=True)
    chrome_process = None


class ReusableThreadingHTTPServer(ThreadingHTTPServer):
    allow_reuse_address = True


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format: str, *args: object) -> None:
        return

    def send_json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        if self.path == "/health":
            self.send_json(200, {"status": "ok"})
        else:
            self.send_json(404, {"status": "not_found"})

    def do_POST(self) -> None:
        try:
            if self.path == "/launch":
                start_chrome()
                self.send_json(200, {"status": "launched", "cdpUrl": DOCKER_CDP_URL})
            elif self.path == "/close":
                stop_chrome()
                self.send_json(200, {"status": "closed"})
            else:
                self.send_json(404, {"status": "not_found"})
        except Exception as exc:
            log(f"Request {self.path} failed: {exc}")
            self.send_json(500, {"status": "failed", "message": str(exc)})


if __name__ == "__main__":
    try:
        server = ReusableThreadingHTTPServer((BIND_ADDRESS, PORT), Handler)
        log(f"VPLMQA E2E Chrome runner listening on {BIND_ADDRESS}:{PORT}.")
        log(f"Chrome remote debugging will bind to {CDP_ADDRESS}:{CDP_PORT}.")
        print(f"VPLMQA E2E Chrome launcher is listening on {BIND_ADDRESS}:{PORT}.", flush=True)
        print(f"Chrome remote debugging will bind to {CDP_ADDRESS}:{CDP_PORT}.", flush=True)
        print("Chrome will open only when the pipeline needs it.", flush=True)
        server.serve_forever()
    except Exception as exc:
        log(f"Runner failed to start: {exc}")
        raise
