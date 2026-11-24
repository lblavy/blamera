"""Quality evidence for the local PC component, separate from mobile and scripts."""

import argparse
import hashlib
import json
import os
import subprocess
import sys
from importlib.metadata import metadata
from pathlib import Path

from packaging.requirements import Requirement
from quality_gates import (
    QualityError,
    changed_lines,
    check_licenses,
    check_vulnerabilities,
    duplication_metrics,
    python_coverage_metrics,
    require_coverage,
    require_duplication,
)
from scan_dependencies import scan_dependencies


def declared_license(installed, name, version, overrides):
    declared = {
        "expression": installed.get("License-Expression"),
        "license": installed.get("License"),
        "classifiers": installed.get_all("Classifier", []),
    }
    fingerprint = hashlib.sha256(json.dumps(declared, sort_keys=True).encode()).hexdigest()
    override = (overrides or {}).get(name)
    if override:
        if override["version"] != version or override["metadata_sha256"] != fingerprint:
            raise QualityError(f"Reviewed license metadata changed: {name}")
        return override["license"]
    license = declared["expression"] or declared["license"]
    if not license:
        raise QualityError(f"Missing license: {name}")
    return license


def runtime_inventory(requirements, provider=None, overrides=None):
    provider = provider or metadata
    modules, licenses = [], []
    for line in requirements.splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        requirement = Requirement(line)
        if requirement.marker and not requirement.marker.evaluate():
            continue
        specifiers = list(requirement.specifier)
        if len(specifiers) != 1 or specifiers[0].operator != "==":
            raise QualityError("Runtime requirements must be pinned")
        installed = provider(requirement.name)
        version = specifiers[0].version
        if installed["Version"] != version:
            raise QualityError(f"Installed/locked version mismatch: {requirement.name}")
        license = declared_license(installed, requirement.name, version, overrides)
        modules.append({"ecosystem": "PyPI", "name": requirement.name, "version": version})
        licenses.append(
            {"moduleName": requirement.name, "moduleVersion": version, "moduleLicense": license}
        )
    if not modules:
        raise QualityError("Empty PC runtime inventory")
    return modules, licenses


def execute(root, name, command, cwd=None):
    report = root / "build/reports/quality/pc"
    report.mkdir(parents=True, exist_ok=True)
    result = subprocess.run(
        [str(x) for x in command],
        cwd=cwd or root,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        env={
            **os.environ,
            "UV_CACHE_DIR": str(root / ".tools/uv-cache"),
            "UV_PYTHON_INSTALL_DIR": str(root / ".tools/python"),
            "UV_PYTHON_INSTALL_BIN": "0",
            "UV_PYTHON_NO_REGISTRY": "1",
        },
    )
    (report / (name + ".log")).write_text(result.stdout + result.stderr, encoding="utf-8")
    if result.returncode:
        raise QualityError(f"{name} exited {result.returncode}; see {report / (name + '.log')}")


def check_runtime(root, report):
    policy = json.loads((root / "pc/config/licenses.json").read_text(encoding="utf-8"))
    requirements = (report / "runtime.txt").read_text(encoding="utf-8")
    modules, licenses = runtime_inventory(requirements, overrides=policy["overrides"])
    inventory = {
        "scope": "PC runtime installed on this platform; direct and transitive",
        "packages": modules,
        "licenses": licenses,
    }
    (report / "inventory.json").write_text(json.dumps(inventory, indent=2), encoding="utf-8")
    check_licenses(licenses, set(policy["allowed"]))
    try:
        osv = scan_dependencies(modules)
        osv["scope"] = inventory["scope"]
    except Exception as error:
        (report / "osv.json").write_text(
            json.dumps({"status": "incomplete", "error": str(error)}), encoding="utf-8"
        )
        raise
    (report / "osv.json").write_text(json.dumps(osv, indent=2), encoding="utf-8")
    check_vulnerabilities(osv)
    return {
        "runtime_packages": len(modules),
        "license_entries": len(licenses),
        "active_vulnerabilities": len(osv["findings"]),
    }


def run_checks(root, base_ref):
    report = root / "build/reports/quality/pc"
    pc = root / "pc"
    python = pc / (".venv/Scripts/python.exe" if sys.platform == "win32" else ".venv/bin/python")
    uv = root / (".tools/uv/0.13.5/uv.exe" if sys.platform == "win32" else ".tools/uv/uv")
    if not python.is_file() or not uv.is_file():
        raise QualityError("PC environment absent; follow docs/pc-development.md")
    execute(
        root,
        "locked-environment",
        [uv, "sync", "--project", pc, "--locked", "--python", "3.12.14", "--managed-python"],
    )
    execute(
        root,
        "format",
        [
            python,
            "-m",
            "ruff",
            "format",
            "--config",
            "pc/pyproject.toml",
            "--check",
            "pc/src",
            "pc/tests",
            "scripts/quality_pc.py",
        ],
    )
    execute(
        root,
        "lint",
        [
            python,
            "-m",
            "ruff",
            "check",
            "--config",
            "pc/pyproject.toml",
            "pc/src",
            "pc/tests",
            "scripts/quality_pc.py",
        ],
    )
    execute(root, "types", [python, "-m", "mypy", "--config-file", "pc/pyproject.toml", "pc/src"])
    script_sources = sorted(str(path.relative_to(root)) for path in (root / "scripts").glob("*.py"))
    execute(
        root,
        "script-cyclomatic",
        [
            python,
            "-m",
            "ruff",
            "check",
            "--select",
            "C901",
            "--config",
            "lint.mccabe.max-complexity=10",
            *script_sources,
        ],
    )
    complexipy = python.parent / ("complexipy.exe" if sys.platform == "win32" else "complexipy")
    execute(
        root,
        "complexity",
        [
            complexipy,
            "pc/src",
            *script_sources,
            "--max-complexity-allowed",
            "15",
            "--output-format",
            "json",
            "--output",
            report / "complexity.json",
            "--cache-dir",
            root / ".tools/complexipy-cache",
        ],
    )
    complexity = json.loads((report / "complexity.json").read_text(encoding="utf-8"))
    if not complexity or any(row["complexity"] > 15 for row in complexity):
        raise QualityError("PC cognitive complexity missing or above 15")
    pc_complexity = [
        row["complexity"] for row in complexity if "/pc/src/" in row["path"].replace("\\", "/")
    ]
    script_complexity = [
        row["complexity"] for row in complexity if "/pc/src/" not in row["path"].replace("\\", "/")
    ]
    if not pc_complexity or not script_complexity:
        raise QualityError("PC or quality script complexity scope absent")
    execute(
        root,
        "tests",
        [python, "-m", "coverage", "run", "-m", "unittest", "discover", "-s", "tests", "-v"],
        pc,
    )
    for format in ("json", "xml", "html"):
        execute(root, "coverage-" + format, [python, "-m", "coverage", format], pc)
    execute(
        root,
        "canonical-statements",
        [
            python,
            root / "scripts/quality_gates.py",
            "--canonical-python-report",
            report / "coverage.json",
        ],
        pc,
    )
    changed = changed_lines(root, base_ref, "pc/src", ".py")
    metrics = python_coverage_metrics(
        report / "coverage.json", {k.removeprefix("pc/"): v for k, v in changed.items()}
    )
    require_coverage(metrics)
    execute(
        root,
        "duplication",
        ["node", "node_modules/jscpd/bin/jscpd", "pc/src", "--config", "pc/config/jscpd.json"],
    )
    duplication = duplication_metrics(
        report / "duplication/jscpd-report.json", changed, root, "pc/src", ".py"
    )
    if duplication["new_lines"]:
        require_duplication(duplication["new_duplicated_lines"], duplication["new_lines"])
    execute(
        root,
        "runtime-export",
        [
            uv,
            "export",
            "--project",
            pc,
            "--frozen",
            "--no-dev",
            "--no-hashes",
            "--no-emit-project",
            "--output-file",
            report / "runtime.txt",
        ],
    )
    return {
        "coverage": metrics,
        "duplication": duplication,
        "base_ref": base_ref,
        "cognitive_max": max(pc_complexity),
        "quality_script_cognitive_max": max(script_complexity),
        **check_runtime(root, report),
    }


def main(root=None, argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-ref", default="HEAD")
    args = parser.parse_args(argv)
    root = Path(root or Path(__file__).resolve().parents[1]).resolve()
    report = root / "build/reports/quality/pc"
    report.mkdir(parents=True, exist_ok=True)
    summary = {"status": "failed"}
    try:
        summary["metrics"] = run_checks(root, args.base_ref)
        summary["status"] = "passed"
    except Exception as error:
        summary["error"] = str(error)
    (report / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))
    return int(summary["status"] != "passed")


def read_pc_summary(path):
    try:
        report = json.loads(Path(path).read_text(encoding="utf-8"))
        if report["status"] != "passed" or not report["metrics"]:
            raise QualityError("PC checks did not pass")
        return report["metrics"]
    except (OSError, ValueError, KeyError) as error:
        raise QualityError("PC summary absent or invalid") from error


if __name__ == "__main__":
    sys.exit(main())
