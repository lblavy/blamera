"""Real localhost HTTP smoke check; owns and closes only its child process."""

import os
import socket
import subprocess
import sys
import time
import unittest
from pathlib import Path
from urllib.error import URLError
from urllib.request import urlopen


class LocalServerTests(unittest.TestCase):
    def test_local_health_without_opening_a_browser(self):
        pc = Path(__file__).resolve().parents[1]
        with socket.socket() as reservation:
            reservation.bind(("127.0.0.1", 0))
            port = reservation.getsockname()[1]
        flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
        report = pc.parent / "build/reports/quality/pc/server.log"
        report.parent.mkdir(parents=True, exist_ok=True)
        with report.open("w") as log:
            process = subprocess.Popen(
                [
                    sys.executable,
                    "-m",
                    "streamlit",
                    "run",
                    "src/blamera_pc/app.py",
                    "--server.port",
                    str(port),
                ],
                cwd=pc,
                stdout=log,
                stderr=log,
                creationflags=flags,
                env={**os.environ, "PYTHONPATH": str(pc / "src")},
            )
            try:
                deadline = time.monotonic() + 20
                while time.monotonic() < deadline:
                    self.assertIsNone(process.poll(), "Le serveur local ne doit pas quitter")
                    try:
                        with urlopen(
                            f"http://127.0.0.1:{port}/_stcore/health", timeout=1
                        ) as response:
                            self.assertEqual(response.read(), b"ok")
                        return
                    except URLError:
                        time.sleep(0.1)
                self.fail("Serveur local indisponible apres 20 secondes")
            finally:
                process.terminate()
                process.wait(timeout=10)
