from contextlib import contextmanager, redirect_stdout
from io import StringIO, BytesIO
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import quality
from quality_gates import QualityError, export_python_statements, python_coverage_metrics, require_coverage
from scan_dependencies import request_json

TEMP_ROOT = Path(__file__).resolve().parents[2] / ".gradle-user-home"
TEMP_ROOT.mkdir(exist_ok=True)


@contextmanager
def workspace():
    previous = Path.cwd()
    with tempfile.TemporaryDirectory(dir=TEMP_ROOT) as directory:
        try:
            yield Path(directory).resolve()
        finally:
            os.chdir(previous)


class RunnerTests(unittest.TestCase):
    def test_installs_duplication_tool_before_gate_tests_on_clean_checkout(self):
        with workspace() as root, patch.object(
            quality.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)
        ) as run, patch.object(quality, "evaluate", return_value={}), redirect_stdout(StringIO()):
            self.assertEqual(0, quality.main(root, []))

        commands = [call.args[0] for call in run.call_args_list]
        install = next(
            index for index, command in enumerate(commands)
            if command[0] in {"npm", "npm.cmd"} and command[1] == "ci"
        )
        tests = next(index for index, command in enumerate(commands) if "scripts/tests" in command)
        self.assertLess(install, tests, "Gate tests require jscpd to be installed first")

    def test_pc_component_is_run_before_canonical_script_coverage(self):
        with workspace() as root, patch.object(quality.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)) as run, \
                patch.object(quality, "evaluate", return_value={}), redirect_stdout(StringIO()):
            self.assertEqual(0, quality.main(root, []))
            commands = [list(map(str, call.args[0])) for call in run.call_args_list]
            pc = [i for i,c in enumerate(commands) if any(x.endswith('quality_pc.py') for x in c)]
            self.assertTrue(pc, 'Le composant PC doit participer a la commande globale')
            canonical = next(i for i,c in enumerate(commands) if '--canonical-python-report' in c)
            self.assertLess(pc[0],canonical)

    def test_successful_tools_and_evidence_produce_a_pass_and_ci_summary(self):
        with workspace() as root, patch.object(quality.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)), \
                patch.object(quality, "evaluate", return_value={"coverage": {"line": 100}}), \
                patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": str(root / "summary.md")}), redirect_stdout(StringIO()):
            self.assertEqual(0, quality.main(root, []))
            result = json.loads((root / "build/reports/quality/summary.json").read_text())
            self.assertEqual("passed", result["status"])
            self.assertIn("passed", (root / "summary.md").read_text())

    def test_failed_tool_cannot_be_hidden_by_successful_report_evaluation(self):
        def run(command, **_):
            return subprocess.CompletedProcess(command, 1 if "qualityReports" in command else 0)
        with workspace() as root, patch.object(quality.subprocess, "run", side_effect=run), \
                patch.object(quality, "evaluate", return_value={"coverage": {"line": 100}}), redirect_stdout(StringIO()):
            self.assertEqual(1, quality.main(root, []))
            result = json.loads((root / "build/reports/quality/summary.json").read_text())
            self.assertEqual("failed", result["status"])
            self.assertTrue(any("Android" in error for error in result["failures"]))

    def test_missing_executable_and_evidence_are_reported_as_failure(self):
        with workspace() as root, patch.object(quality.subprocess, "run", side_effect=FileNotFoundError("tool absent")), \
                patch.object(quality, "evaluate", side_effect=QualityError("report absent")), redirect_stdout(StringIO()):
            self.assertEqual(1, quality.main(root, []))
            result = json.loads((root / "build/reports/quality/summary.json").read_text())
            self.assertIn("report absent", result["failures"])

    def test_unsupported_python_version_is_not_clean(self):
        with workspace() as root, patch.object(quality.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)), \
                patch.object(quality.sys, "version_info", (3, 11)), \
                patch.object(quality, "evaluate", return_value={}), redirect_stdout(StringIO()):
            self.assertEqual(1, quality.main(root, []))


class PythonCoverageTests(unittest.TestCase):
    def test_executed_docstring_cannot_turn_seventy_five_percent_into_eighty(self):
        with workspace() as root:
            report = root / "coverage.json"
            report.write_text(json.dumps({
                "canonical_statement_source": "coverage.py.analysis2",
                "totals": {"covered_lines": 9, "num_statements": 10,
                           "covered_branches": 2, "num_branches": 2},
                "files": {"scripts/gate.py": {
                    "summary": {"num_statements": 4},
                    "statements": [2, 3, 4, 5],
                    "executed_lines": [1, 2, 3, 4], "missing_lines": [5],
                    "executed_branches": [[2, 3], [2, 4]], "missing_branches": [],
                }},
            }), encoding="utf-8")
            metrics = python_coverage_metrics(report, {"scripts/gate.py": {1, 2, 3, 4, 5}})
            with self.assertRaises(QualityError):
                require_coverage(metrics)

    def test_missing_canonical_statements_cannot_certify_new_python_code(self):
        with workspace() as root:
            report = root / "coverage.json"
            report.write_text(json.dumps({
                "totals": {"covered_lines": 1, "num_statements": 1,
                           "covered_branches": 2, "num_branches": 2},
                "files": {"scripts/gate.py": {
                    "executed_lines": [1], "missing_lines": [],
                    "executed_branches": [[1, 2], [1, 3]], "missing_branches": [],
                }},
            }), encoding="utf-8")
            with self.assertRaises(QualityError):
                python_coverage_metrics(report, {"scripts/gate.py": {1}})
            data = json.loads(report.read_text(encoding="utf-8"))
            data["canonical_statement_source"] = "coverage.py.analysis2"
            for statements, count in ((None, 1), (["1"], 1), ([1], 2)):
                with self.subTest(statements=statements, count=count):
                    file_data = data["files"]["scripts/gate.py"]
                    file_data["summary"] = {"num_statements": count}
                    if statements is not None:
                        file_data["statements"] = statements
                    report.write_text(json.dumps(data), encoding="utf-8")
                    with self.assertRaises(QualityError):
                        python_coverage_metrics(report, {"scripts/gate.py": {1}})

    def test_python_lines_and_branches_are_separate_and_new_ranges_are_honored(self):
        with workspace() as root:
            report = root / "coverage.json"
            report.write_text(json.dumps({"canonical_statement_source": "coverage.py.analysis2",
                                          "totals": {"covered_lines": 9, "num_statements": 10,
                                                    "covered_branches": 8, "num_branches": 10},
                                          "files": {"scripts/gate.py": {"summary": {"num_statements": 2},
                                                                       "statements": [1, 2],
                                                                       "executed_lines": [1], "missing_lines": [2],
                                                                       "executed_branches": [[1, 2]],
                                                                       "missing_branches": [[2, 3], [2, 4]]}}}), encoding="utf-8")
            result = python_coverage_metrics(report, {"scripts/gate.py": {2}})
            self.assertEqual(90, result.get("line"))
            self.assertEqual(80, result.get("branch"))
            self.assertEqual(0, result.get("new_line"))
            self.assertEqual(0, result.get("new_branch"))

    def test_export_uses_analysis_statements_and_checks_their_count(self):
        with workspace() as root:
            report = root / "coverage.json"
            original = {"totals": {"covered_lines": 9, "num_statements": 10,
                                    "covered_branches": 2, "num_branches": 2},
                        "files": {"scripts/gate.py": {"summary": {"num_statements": 4},
                                                     "executed_lines": [1, 2, 3, 4], "missing_lines": [5],
                                                     "executed_branches": [[2, 3], [2, 4]], "missing_branches": []}}}
            report.write_text(json.dumps(original), encoding="utf-8")
            export_python_statements(report, lambda _: ("scripts/gate.py", [2, 3, 4, 5], [], [5], "5"))
            metrics = python_coverage_metrics(report, {"scripts/gate.py": {1, 2, 3, 4, 5}})
            self.assertEqual(75, metrics["new_line"])
            before = report.read_bytes()
            with self.assertRaises(QualityError):
                export_python_statements(report, lambda _: ("scripts/gate.py", [2, 3], [], [], ""))
            self.assertEqual(before, report.read_bytes())


class EvaluationTests(unittest.TestCase):
    def test_evaluation_checks_all_reports_and_preserves_failed_scan_evidence(self):
        with workspace() as root:
            report_dir = root / "build/reports/quality"
            report_dir.mkdir(parents=True)
            (report_dir / 'pc').mkdir()
            (report_dir / 'pc/summary.json').write_text('{"status":"passed","metrics":{"coverage":{"line":100}}}', encoding='utf-8')
            source = root / "app/src/main/java/example/One.kt"
            source.parent.mkdir(parents=True)
            source.write_text("fun one() = 1\n", encoding="utf-8")
            java = root / "app/build/reports/jacoco/jacocoDebugReport/jacocoDebugReport.xml"
            java.parent.mkdir(parents=True)
            java.write_text('<report><package name="example"><sourcefile name="One.kt">'
                            '<line nr="1" ci="1" mi="0" cb="2" mb="0" /></sourcefile></package>'
                            '<counter type="LINE" covered="1" missed="0" />'
                            '<counter type="BRANCH" covered="2" missed="0" /></report>', encoding="utf-8")
            python = report_dir / "python/coverage.json"
            python.parent.mkdir()
            (root / 'scripts').mkdir()
            (root / 'scripts/gate.py').write_text('print("gate")\n')
            (report_dir / 'python/duplication').mkdir()
            (report_dir / 'python/duplication/jscpd-report.json').write_text('{"statistics":{"total":{"sources":1,"lines":1,"duplicatedLines":0}},"duplicates":[]}',encoding='utf-8')
            python.write_text(json.dumps({"canonical_statement_source": "coverage.py.analysis2",
                                           "totals": {"covered_lines": 1, "num_statements": 1,
                                                     "covered_branches": 2, "num_branches": 2},
                                           "files": {"scripts/gate.py": {"summary": {"num_statements": 1},
                                                                        "statements": [1],
                                                                        "executed_lines": [1], "missing_lines": [],
                                                                        "executed_branches": [[1, 2], [1, 3]],
                                                                        "missing_branches": []}}}), encoding="utf-8")
            (report_dir / "detekt.xml").write_text('<checkstyle/>', encoding="utf-8")
            clones = report_dir / "duplication/jscpd-report.json"
            clones.parent.mkdir()
            clones.write_text('{"statistics":{"total":{"sources":1,"lines":1,"duplicatedLines":0}},"duplicates":[]}', encoding="utf-8")
            inventory = root / "app/build/reports/dependency-license"
            inventory.mkdir(parents=True)
            modules = [{"name": "example:library", "version": "1.0", "ecosystem": "Maven"}]
            (inventory / "runtime-inventory.json").write_text(json.dumps(modules), encoding="utf-8")
            license_file = inventory / "index.json"
            license_file.write_text(json.dumps({"dependencies": [{"moduleName": "example:library",
                                                                    "moduleVersion": "1.0", "moduleLicense": "Apache-2.0"}]}), encoding="utf-8")
            (root / "config").mkdir()
            (root / "config/allowed-licenses.json").write_text('{"allowed":["Apache-2.0"]}', encoding="utf-8")
            def changes(_, __, scope="app/src/main/java", suffix=".kt"):
                return {"app/src/main/java/example/One.kt": {1}} if scope != "scripts" else {"scripts/gate.py": {1}}
            with patch.object(quality, "changed_lines", side_effect=changes), \
                    patch.object(quality, "scan_dependencies", return_value={"status": "complete", "findings": []}):
                self.assertEqual(1, quality.evaluate(root, report_dir, "HEAD")["runtime_packages"])
            with patch.object(quality, "changed_lines", side_effect=changes), \
                    patch.object(quality, "scan_dependencies", side_effect=OSError("offline")):
                with self.assertRaises(OSError):
                    quality.evaluate(root, report_dir, "HEAD")
                self.assertEqual("incomplete", json.loads((report_dir / "osv.json").read_text())["status"])
            license_file.write_text(json.dumps({"dependencies": [{"moduleName": "wrong:library",
                                                                    "moduleVersion": "1.0", "moduleLicense": "Apache-2.0"}]}), encoding="utf-8")
            with patch.object(quality, "changed_lines", side_effect=changes):
                with self.assertRaises(QualityError):
                    quality.evaluate(root, report_dir, "HEAD")


class HttpAdapterTests(unittest.TestCase):
    def test_post_and_get_encode_requests_and_decode_json(self):
        for payload in (None, {"queries": []}):
            with self.subTest(payload=payload), patch("scan_dependencies.urlopen", return_value=BytesIO(b'{"ok":true}')) as transport:
                self.assertEqual({"ok": True}, request_json("https://example.invalid/api", payload))
                request = transport.call_args.args[0]
                self.assertEqual("GET" if payload is None else "POST", request.get_method())


if __name__ == "__main__":
    unittest.main()
