"""One local/CI entry point: run tools, query OSV, then fail closed on report gates."""

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import subprocess
import sys

from quality_gates import (
    QualityError, changed_lines, check_complexity, check_licenses, check_vulnerabilities,
    coverage_metrics, duplication_metrics, require_coverage, require_duplication,
    python_coverage_metrics,
)
from scan_dependencies import scan_dependencies
from quality_pc import read_pc_summary


def evaluate(root, report_dir, base_ref):
    changed = changed_lines(root, base_ref)
    metrics = coverage_metrics(root / "app/build/reports/jacoco/jacocoDebugReport/jacocoDebugReport.xml", changed)
    require_coverage(metrics)
    python_changed = {name: lines for name, lines in changed_lines(root, base_ref, "scripts", ".py").items()
                      if not name.startswith("scripts/tests/")}
    python_metrics = python_coverage_metrics(report_dir / "python/coverage.json", python_changed)
    require_coverage(python_metrics)
    python_duplication = duplication_metrics(report_dir / "python/duplication/jscpd-report.json", python_changed,
                                            root, "scripts", ".py", recursive=False)
    if python_duplication["new_lines"]:
        require_duplication(python_duplication["new_duplicated_lines"], python_duplication["new_lines"])
    check_complexity(report_dir / "detekt.xml")
    duplication = duplication_metrics(report_dir / "duplication/jscpd-report.json", changed, root)
    if duplication["new_lines"]:
        require_duplication(duplication["new_duplicated_lines"], duplication["new_lines"])
    inventory_dir = root / "app/build/reports/dependency-license"
    modules = json.loads((inventory_dir / "runtime-inventory.json").read_text(encoding="utf-8"))
    licenses = json.loads((inventory_dir / "index.json").read_text(encoding="utf-8"))["dependencies"]
    policy = json.loads((root / "config/allowed-licenses.json").read_text(encoding="utf-8"))
    check_licenses(licenses, set(policy["allowed"]))
    licensed = {(m["moduleName"], m["moduleVersion"]) for m in licenses}
    if not {(m["name"], m["version"]) for m in modules} <= licensed:
        raise QualityError("Runtime artifacts missing from license report")
    try:
        osv = scan_dependencies(modules)
    except Exception as error:
        (report_dir / "osv.json").write_text(json.dumps({"status": "incomplete", "error": str(error)}, indent=2), encoding="utf-8")
        raise
    (report_dir / "osv.json").write_text(json.dumps(osv, indent=2), encoding="utf-8")
    check_vulnerabilities(osv)
    return {"coverage": metrics, "python_coverage": python_metrics, "pc": read_pc_summary(report_dir / "pc/summary.json"),
            "duplication": duplication, "python_duplication": python_duplication, "base_ref": base_ref,
            "changed_files": sorted(changed), "runtime_packages": len(modules),
            "license_entries": len(licenses), "active_vulnerabilities": len(osv["findings"]),
            "complexity": {"cyclomatic_limit": 10, "cognitive_limit": 15, "violations": 0}}


def main(root=None, argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-ref", default=os.getenv("QUALITY_BASE_REF") or "HEAD")
    args = parser.parse_args(argv)
    root = Path(root or Path(__file__).resolve().parents[1]).resolve()
    os.chdir(root)
    report_dir = root / "build/reports/quality"
    report_dir.mkdir(parents=True, exist_ok=True)
    failures = []
    summary = {"status": "failed", "generated_at": datetime.now(timezone.utc).isoformat(), "steps": []}

    def run(name, command):
        print(f"\n=== {name} ===", flush=True)
        try:
            result = subprocess.run([str(x) for x in command], check=False)
            code = result.returncode
        except OSError as error:
            failures.append(f"{name}: {error}")
            code = 127
        summary["steps"].append({"name": name, "exit_code": code})
        if code:
            failures.append(f"{name}: exit {code}")

    # Prevent an old report from certifying a failed or skipped tool invocation.
    for path in [report_dir / "detekt.xml", report_dir / "ktlint.xml", report_dir / "osv.json", report_dir / "pc/summary.json",
                 report_dir / "python/duplication/jscpd-report.json",
                 report_dir / "duplication/jscpd-report.json",
                 root / "app/build/reports/jacoco/jacocoDebugReport/jacocoDebugReport.xml",
                 root / "app/build/reports/dependency-license/index.json",
                 root / "app/build/reports/dependency-license/runtime-inventory.json"]:
        path.unlink(missing_ok=True)
    if sys.version_info < (3, 12):
        failures.append("Python 3.12 or newer required")
    tools = root / ".tools/python-quality"
    run("pinned Python coverage tool", [sys.executable, "-m", "pip", "install", "--target", tools,
                                       "--upgrade", "--only-binary=:all:", "-r", root / "requirements-quality.txt",
                                       "--disable-pip-version-check"])
    os.environ["PYTHONPATH"] = str(tools) + os.pathsep + os.environ.get("PYTHONPATH", "")
    (report_dir / "python").mkdir(parents=True, exist_ok=True)
    npm = "npm.cmd" if os.name == "nt" else "npm"
    run("pinned duplication tool", [npm, "ci", "--ignore-scripts", "--no-audit", "--no-fund", "--cache", ".npm-cache"])
    run("gate tests", [sys.executable, "-m", "coverage", "run", "-m", "unittest", "discover", "-s", "scripts/tests", "-v"])
    if os.name == "nt":
        gradle = ["powershell.exe", "-NoProfile", "-File", root / "scripts/gradle.ps1"]
    else:
        os.environ.setdefault("GRADLE_USER_HOME", str(root / ".gradle-user-home"))
        gradle = ["bash", root / "gradlew"]
    run("Android and Kotlin reports", [*gradle, "qualityReports", "--continue", "--console=plain", "--no-daemon"])
    run("duplication", ["node", "node_modules/jscpd/bin/jscpd", "app/src/main/java", "--config", ".jscpd.json"])
    run("Python script duplication", ["node", "node_modules/jscpd/bin/jscpd", "scripts", "--config", ".jscpd-scripts.json"])
    run("duplication gate proof", [sys.executable, "-m", "coverage", "run", "--append", root / "scripts/prove_duplication.py"])
    run("PC quality", [sys.executable, "-m", "coverage", "run", "--append", root / "scripts/quality_pc.py", "--base-ref", args.base_ref])
    for format in ("json", "xml", "html"):
        run("Python coverage " + format, [sys.executable, "-m", "coverage", format])
    run("canonical Python statements", [sys.executable, root / "scripts/quality_gates.py",
                                        "--canonical-python-report", report_dir / "python/coverage.json"])
    try:
        summary["metrics"] = evaluate(root, report_dir, args.base_ref)
    except Exception as error:
        failures.append(str(error))
    summary["failures"] = failures
    summary["status"] = "failed" if failures else "passed"
    (report_dir / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    markdown = "# Blamera quality: " + summary["status"] + "\n\n"
    markdown += "```json\n" + json.dumps(summary, indent=2) + "\n```\n"
    (report_dir / "summary.md").write_text(markdown, encoding="utf-8")
    if os.getenv("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as stream:
            stream.write(markdown)
    print(markdown)
    return int(bool(failures))


if __name__ == "__main__":
    sys.exit(main())
