"""Explicit live collection of Blamera's own report tag; never launches the app."""

import argparse
import re
import subprocess
from pathlib import Path


def stop_reader(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)


def live_lines(process, target):
    transfer = None
    written = 0
    for line in process.stdout:
        written += len(line.encode("utf-8"))
        if len(line) > 1801 or written > 20 * 1024 * 1024:
            raise ValueError("Capture limit exceeded")
        target.write(line)
        target.flush()
        if line.startswith("report begin "):
            transfer = re.search(r" transfer=([A-Za-z0-9-]+) ", line)
        if transfer and line.startswith(f"report end transfer={transfer[1]} "):
            return
    raise ValueError("Reader ended without a complete transfer; partial capture retained")


def collect(adb, serial, output, run=None, popen=None, ready=print):
    run = run or subprocess.run
    popen = popen or subprocess.Popen
    output = Path(output)
    if output.exists():
        raise FileExistsError("Choose a new capture file")
    pid = run(
        [adb, "-s", serial, "shell", "pidof", "-s", "dev.blamera"],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=15,
    ).stdout.strip()
    if not re.fullmatch(r"[1-9][0-9]{0,8}", pid):
        raise ValueError("Blamera must already be open manually on the selected device")
    command = [
        adb,
        "-s",
        serial,
        "logcat",
        f"--pid={pid}",
        "-v",
        "raw",
        "-T",
        "1",
        "BlameraBenchmark:I",
        "*:S",
    ]
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("x", encoding="utf-8", newline="\n") as target:
        process = popen(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            stdin=subprocess.DEVNULL,
            text=True,
            encoding="utf-8",
            errors="replace",
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        try:
            ready(
                "READY: touch Réémettre le dernier résultat in Blamera; no inference is started by this collector."
            )
            live_lines(process, target)
        finally:
            stop_reader(process)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument(
        "--serial", required=True, help="Explicit local target, never stored in report"
    )
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    collect(args.adb, args.serial, args.output)


if __name__ == "__main__":
    main()
