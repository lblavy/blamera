"""Validate generated quality reports; missing evidence must fail closed."""

import xml.etree.ElementTree as ET
import json
from pathlib import Path
import re
import subprocess


class QualityError(RuntimeError):
    pass


def require_coverage(metrics):
    for name in ("line", "branch", "new_line", "new_branch"):
        value = metrics.get(name)
        if value is None:
            if name.startswith("new_"):
                continue  # No executable lines/branches in the changed range; reported as N/A.
            raise QualityError(f"Coverage {name}: no measurement")
        if value < 80:
            raise QualityError(f"Coverage {name}: {value:.2f}% < 80%")


def check_complexity(path):
    try:
        report = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as error:
        raise QualityError(f"Complexity report unavailable: {path}") from error
    if report.tag != "checkstyle":
        raise QualityError("Invalid detekt report")
    findings = report.findall(".//error")
    if findings:
        raise QualityError("Static analysis: " + "; ".join(x.get("message", "finding") for x in findings))


def require_duplication(duplicated_lines, new_lines):
    if new_lines <= 0:
        raise QualityError("Duplication: no input lines")
    if duplicated_lines / new_lines > 0.03:
        raise QualityError(f"New-code duplication: {duplicated_lines}/{new_lines} > 3%")


def check_licenses(modules, allowed):
    if not modules:
        raise QualityError("No licensed dependencies in the report")
    for module in modules:
        declared = module.get("moduleLicenses", [{"moduleLicense": module.get("moduleLicense")}])
        licenses = {x.get("moduleLicense") for x in declared}
        if not licenses or not licenses <= allowed:
            raise QualityError(f"Unapproved/unknown license: {module.get('moduleName')}: {licenses}")


def check_vulnerabilities(report):
    if report.get("status") != "complete":
        raise QualityError("Vulnerability scan is incomplete")
    if "findings" not in report:
        raise QualityError("Vulnerability findings missing")
    if report["findings"]:
        raise QualityError("Untriaged vulnerabilities: " + ", ".join(x["id"] for x in report["findings"]))


def _changed_hunks(diff):
    changed = {}
    current = None
    for line in diff.splitlines():
        if line.startswith("+++ b/"):
            current = line[6:]
            changed.setdefault(current, set())
        match = re.match(r"@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@", line)
        if match and current:
            start, count = int(match[1]), int(match[2] or 1)
            changed[current].update(range(start, start + count))
    return changed


def changed_lines(repository, base_ref, scope="app/src/main/java", suffix=".kt"):
    repository = Path(repository).resolve()
    source = repository / scope
    if base_ref == "EMPTY":
        return {p.relative_to(repository).as_posix(): set(range(1, len(p.read_text(encoding="utf-8").splitlines()) + 1))
                for p in source.rglob("*" + suffix)}
    def git(*args):
        try:
            return subprocess.check_output(["git", "-C", str(repository), "-c", "core.quotepath=false",
                                            *args], text=True, encoding="utf-8", stderr=subprocess.PIPE)
        except subprocess.CalledProcessError as error:
            raise QualityError(f"Git evidence unavailable: {error.stderr.strip()}") from error
    git("rev-parse", "--verify", f"{base_ref}^{{commit}}")
    changed = _changed_hunks(git("diff", "--no-renames", "--unified=0", base_ref, "--", scope))
    for name in git("ls-files", "--others", "--exclude-standard", "--", scope).splitlines():
        if name.endswith(suffix):
            changed[name] = set(range(1, len((repository / name).read_text(encoding="utf-8").splitlines()) + 1))
    return {name: lines for name, lines in changed.items() if name.endswith(suffix) and lines}


def python_coverage_metrics(path, changed):
    try:
        report = json.loads(Path(path).read_text(encoding="utf-8"))
        total = report["totals"]
        files = report["files"]
    except (OSError, ValueError, KeyError) as error:
        raise QualityError("Python coverage report unavailable/invalid") from error
    if report.get("canonical_statement_source") != "coverage.py.analysis2":
        raise QualityError("Canonical Python statements unavailable")
    def percent(covered, count):
        return 100 * covered / count if count else None
    result = {"line": percent(total["covered_lines"], total["num_statements"]),
              "branch": percent(total["covered_branches"], total["num_branches"])}
    covered_lines = missed_lines = covered_branches = missed_branches = 0
    measured = set()
    for name, data in files.items():
        name = name.replace("\\", "/")
        measured.add(name)
        selected = changed.get(name, set())
        try:
            statements = set(data["statements"])
            expected_count = data["summary"]["num_statements"]
        except (KeyError, TypeError) as error:
            raise QualityError(f"Canonical Python statements missing: {name}") from error
        if len(statements) != expected_count or any(type(line) is not int for line in statements):
            raise QualityError(f"Canonical Python statement count inconsistent: {name}")
        selected_statements = selected.intersection(statements)
        executed = set(data["executed_lines"])
        covered_lines += len(selected_statements.intersection(executed))
        missed_lines += len(selected_statements - executed)
        covered_branches += sum(arc[0] in selected for arc in data["executed_branches"])
        missed_branches += sum(arc[0] in selected for arc in data["missing_branches"])
    if set(changed) - measured:
        raise QualityError("New Python source missing from coverage")
    result["new_line"] = percent(covered_lines, covered_lines + missed_lines)
    result["new_branch"] = percent(covered_branches, covered_branches + missed_branches)
    return result


def export_python_statements(path, analyzer=None):
    """Attach coverage.py's executable statements; trace events also contain docstrings."""
    path = Path(path)
    report = json.loads(path.read_text(encoding="utf-8"))
    if analyzer is None:
        from coverage import Coverage
        measured = Coverage(data_file=str(path.parent / ".coverage"))
        measured.load()
        analyzer = measured.analysis2
    for name, data in report["files"].items():
        _, executable, excluded, _, _ = analyzer(name)
        statements = set(executable) - set(excluded)
        if len(statements) != data["summary"]["num_statements"]:
            raise QualityError(f"Coverage source/data mismatch: {name}")
        data["statements"] = sorted(statements)
    report["canonical_statement_source"] = "coverage.py.analysis2"
    path.write_text(json.dumps(report, indent=2), encoding="utf-8")


def _changed_source_counters(source, selected):
    """Count executed lines and arcs only inside the selected source ranges."""
    line_covered = line_missed = branch_covered = branch_missed = 0
    for line in source.findall("line"):
        if int(line.get("nr")) in selected:
            line_covered += int(int(line.get("ci")) > 0)
            line_missed += int(int(line.get("ci")) == 0)
            branch_covered += int(line.get("cb"))
            branch_missed += int(line.get("mb"))
    return line_covered, line_missed, branch_covered, branch_missed


def coverage_metrics(path, changed):
    try:
        report = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as error:
        raise QualityError(f"Coverage report unavailable: {path}") from error
    if report.tag != "report":
        raise QualityError("Invalid JaCoCo report")
    def percentage(covered, missed):
        return 100 * covered / (covered + missed) if covered + missed else None
    metrics = {}
    for name, kind in (("line", "LINE"), ("branch", "BRANCH")):
        counter = report.find(f"counter[@type='{kind}']")
        if counter is None:
            raise QualityError(f"Coverage counter missing: {kind}")
        metrics[name] = percentage(int(counter.get("covered")), int(counter.get("missed")))
    line_covered = line_missed = branch_covered = branch_missed = 0
    measured_sources = set()
    for package in report.findall("package"):
        for source in package.findall("sourcefile"):
            name = "app/src/main/java/" + package.get("name") + "/" + source.get("name")
            measured_sources.add(name)
            lc, lm, bc, bm = _changed_source_counters(source, changed.get(name, set()))
            line_covered += lc
            line_missed += lm
            branch_covered += bc
            branch_missed += bm
    if set(changed) - measured_sources:
        raise QualityError("New source missing from coverage: " + ", ".join(sorted(set(changed) - measured_sources)))
    metrics["new_line"] = percentage(line_covered, line_missed)
    metrics["new_branch"] = percentage(branch_covered, branch_missed)
    metrics["new_executable_lines"] = line_covered + line_missed
    return metrics


def _clone_intersections(clones, nonempty, repository):
    """Union clone ranges so overlapping clones never count a source line twice."""
    duplicates = {name: set() for name in nonempty}
    for clone in clones:
        for side in ("firstFile", "secondFile"):
            segment = clone[side]
            file = Path(segment["name"])
            name = (file if file.is_absolute() else repository / file).resolve().relative_to(repository).as_posix()
            if name in nonempty:
                duplicates[name].update(nonempty[name].intersection(range(segment["start"], segment["end"] + 1)))
    return duplicates


def duplication_metrics(path, changed, repository, scope="app/src/main/java", suffix=".kt", recursive=True):
    try:
        report = json.loads(Path(path).read_text(encoding="utf-8"))
        total = report["statistics"]["total"]
        clones = report["duplicates"]
        if total["sources"] <= 0 or total["lines"] <= 0:
            raise QualityError("Duplication: no analyzed source files")
    except (OSError, ValueError, KeyError) as error:
        raise QualityError(f"Duplication report unavailable/invalid: {path}") from error
    repository = Path(repository).resolve()
    source_glob = (repository / scope).rglob if recursive else (repository / scope).glob
    expected_sources = list(source_glob("*" + suffix))
    if total["sources"] != len(expected_sources):
        raise QualityError("Duplication report does not cover every production source file")
    nonempty = {}
    for name, lines in changed.items():
        text = (repository / name).read_text(encoding="utf-8").splitlines()
        nonempty[name] = {number for number in lines if number <= len(text) and text[number - 1].strip()}
    duplicates = _clone_intersections(clones, nonempty, repository)
    return {"global_percent": 100 * total.get("duplicatedLines", 0) / total["lines"],
            "analyzed_files": total["sources"], "analyzed_lines": total["lines"],
            "new_lines": sum(map(len, nonempty.values())),
            "new_duplicated_lines": sum(map(len, duplicates.values()))}


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Export canonical Python statement evidence")
    parser.add_argument("--canonical-python-report", required=True, type=Path)
    export_python_statements(parser.parse_args().canonical_python_report)
