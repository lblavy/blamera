import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from quality_gates import QualityError, changed_lines, coverage_metrics, duplication_metrics
from scan_dependencies import scan_dependencies

TEST_TEMP_ROOT = Path(__file__).resolve().parents[2] / ".gradle-user-home"
TEST_TEMP_ROOT.mkdir(exist_ok=True)


class EvidenceTests(unittest.TestCase):
    def test_untracked_production_file_is_new_code(self):
        with tempfile.TemporaryDirectory(dir=TEST_TEMP_ROOT) as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            subprocess.run(["git", "-C", str(root), "-c", "user.name=Fixture", "-c",
                            "user.email=fixture@example.invalid", "commit", "--allow-empty", "-qm", "fixture"], check=True)
            file = root / "app/src/main/java/Example.kt"
            file.parent.mkdir(parents=True)
            file.write_text("fun one() = 1\nfun two() = 2\n", encoding="utf-8")
            self.assertEqual({"app/src/main/java/Example.kt": {1, 2}}, changed_lines(root, "HEAD"))
            script = root / "scripts/gate.py"
            script.parent.mkdir()
            script.write_text("print('new script')\n", encoding="utf-8")
            self.assertEqual({"scripts/gate.py": {1}}, changed_lines(root, "HEAD", "scripts", ".py"))
            subprocess.run(["git", "-C", str(root), "add", "app", "scripts"], check=True)
            subprocess.run(["git", "-C", str(root), "-c", "user.name=Fixture", "-c",
                            "user.email=fixture@example.invalid", "commit", "-qm", "tracked fixture"], check=True)
            file.write_text("fun one() = 1\nfun two() = 3\nfun three() = 3\n", encoding="utf-8")
            self.assertEqual({"app/src/main/java/Example.kt": {2, 3}}, changed_lines(root, "HEAD"))

    def test_invalid_base_ref_cannot_report_no_new_code(self):
        with self.assertRaises(QualityError):
            changed_lines(Path(__file__).resolve().parents[2], "missing-ref-for-quality-test")

    def test_coverage_maps_changed_lines_and_branches_without_using_global_average(self):
        with tempfile.TemporaryDirectory(dir=TEST_TEMP_ROOT) as directory:
            path = Path(directory) / "coverage.xml"
            path.write_text('<report><package name="dev/blamera"><sourcefile name="Example.kt">'
                            '<line nr="1" mi="0" ci="10" mb="0" cb="2" />'
                            '<line nr="2" mi="3" ci="0" mb="2" cb="0" />'
                            '</sourcefile></package><counter type="LINE" missed="1" covered="9" />'
                            '<counter type="BRANCH" missed="2" covered="8" /></report>', encoding="utf-8")
            metrics = coverage_metrics(path, {"app/src/main/java/dev/blamera/Example.kt": {2}})
            self.assertEqual(90, metrics.get("line"))
            self.assertEqual(80, metrics.get("branch"))
            self.assertEqual(0, metrics.get("new_line"))
            self.assertEqual(0, metrics.get("new_branch"))

    def test_missing_coverage_report_is_rejected(self):
        with self.assertRaises(QualityError):
            coverage_metrics(Path("nonexistent-quality-report.xml"), {})

    def test_new_source_missing_from_coverage_report_is_rejected(self):
        with tempfile.TemporaryDirectory(dir=TEST_TEMP_ROOT) as directory:
            path = Path(directory) / "coverage.xml"
            path.write_text('<report><counter type="LINE" missed="0" covered="9" />'
                            '<counter type="BRANCH" missed="0" covered="8" /></report>', encoding="utf-8")
            with self.assertRaises(QualityError):
                coverage_metrics(path, {"app/src/main/java/New.kt": {1}})

    def test_new_duplication_intersects_and_deduplicates_clone_ranges(self):
        with tempfile.TemporaryDirectory(dir=TEST_TEMP_ROOT) as directory:
            root = Path(directory)
            source = root / "app/src/main/java/A.kt"
            source.parent.mkdir(parents=True)
            source.write_text("one\ntwo\nthree\nfour\n", encoding="utf-8")
            clone = {"firstFile": {"name": str(source), "start": 1, "end": 3},
                     "secondFile": {"name": str(source), "start": 2, "end": 4}}
            report = root / "clones.json"
            report.write_text(json.dumps({"statistics": {"total": {"lines": 4, "sources": 1,
                                                                 "duplicatedLines": 4}},
                                          "duplicates": [clone, clone]}), encoding="utf-8")
            metrics = duplication_metrics(report, {"app/src/main/java/A.kt": {2, 3, 4}}, root)
            self.assertEqual(3, metrics.get("new_lines"))
            self.assertEqual(3, metrics.get("new_duplicated_lines"))

            (source.parent / "Skipped.kt").write_text("fun skipped() = 1\n", encoding="utf-8")
            with self.assertRaises(QualityError):
                duplication_metrics(report, {}, root)

    def test_empty_clone_report_cannot_be_clean(self):
        with tempfile.TemporaryDirectory(dir=TEST_TEMP_ROOT) as directory:
            path = Path(directory) / "clones.json"
            path.write_text('{"statistics":{"total":{"lines":0,"sources":0}},"duplicates":[]}', encoding="utf-8")
            with self.assertRaises(QualityError):
                duplication_metrics(path, {}, Path(directory))


class OsvTests(unittest.TestCase):
    modules = [{"name": "example:library", "version": "1.0", "ecosystem": "Maven"}]

    def test_paginated_response_blocks_instead_of_ignoring_the_next_page(self):
        with self.assertRaises(QualityError):
            scan_dependencies(self.modules, lambda *_: {"results": [{"next_page_token": "more"}]})

    def test_response_count_mismatch_blocks(self):
        with self.assertRaises(QualityError):
            scan_dependencies(self.modules, lambda *_: {"results": []})

    def test_advisory_details_are_fetched_and_retained(self):
        calls = []
        def request(url, payload=None):
            calls.append(url)
            if payload is not None:
                return {"results": [{"vulns": [{"id": "GHSA-example"}]}]}
            return {"id": "GHSA-example", "database_specific": {"severity": "HIGH"}}
        report = scan_dependencies(self.modules, request)
        self.assertEqual(2, len(calls))
        self.assertEqual("HIGH", report["findings"][0]["severity"])

    def test_withdrawn_advisory_is_retained_as_withdrawn_not_an_active_finding(self):
        def request(url, payload=None):
            return ({"results": [{"vulns": [{"id": "GHSA-withdrawn"}]}]} if payload is not None
                    else {"id": "GHSA-withdrawn", "withdrawn": "2026-01-01T00:00:00Z"})
        report = scan_dependencies(self.modules, request)
        self.assertEqual([], report["findings"])
        self.assertEqual(1, len(report.get("withdrawn", [])))

    def test_network_error_is_not_converted_to_clean(self):
        def offline(*_):
            raise OSError("network unavailable")
        with self.assertRaises((OSError, QualityError)):
            scan_dependencies(self.modules, offline)


if __name__ == "__main__":
    unittest.main()
