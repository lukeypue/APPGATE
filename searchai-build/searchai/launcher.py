from __future__ import annotations

import os
import socket
import sys
import threading
import time
import webbrowser
from pathlib import Path


def app_dir() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent.parent


def configure_runtime() -> None:
    base = app_dir()
    os.environ.setdefault("SEARCHAI_DATA_DIR", str(base / "data"))
    bundled_browser = base / "ms-playwright"
    if bundled_browser.exists():
        os.environ.setdefault("PLAYWRIGHT_BROWSERS_PATH", str(bundled_browser))


def wait_for_server(host: str, port: int, timeout: float = 20.0) -> bool:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with socket.create_connection((host, port), timeout=0.4):
                return True
        except OSError:
            time.sleep(0.2)
    return False


def main() -> None:
    configure_runtime()
    import uvicorn
    from searchai.app import app

    host, port = "127.0.0.1", 8765
    server = uvicorn.Server(uvicorn.Config(app, host=host, port=port, log_level="warning"))
    thread = threading.Thread(target=server.run, daemon=True)
    thread.start()
    if wait_for_server(host, port):
        webbrowser.open(f"http://{host}:{port}")
    else:
        raise SystemExit("SearchAI could not start its local web interface.")
    try:
        while thread.is_alive():
            time.sleep(0.5)
    except KeyboardInterrupt:
        server.should_exit = True


if __name__ == "__main__":
    main()
