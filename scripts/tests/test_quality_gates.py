from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
TEST_TEMP_ROOT = Path(__file__).resolve().parents[2] / ".gradle-user-home"
TEST_TEMP_ROOT.mkdir(exist_ok=True)
from quality_gates import (
    QualityError, check_complexity, check_licenses, check_vulnerabilities,
    require_coverage, require_duplication,
)


class QualityGateTests(unittest.TestCase):
    def test_line_coverage_below_eighty_is_rejected(self):
        with self.assertRaises(QualityError):
            require_coverage({"line": 79.9, "branch": 100, "new_line": 100, "new_branch": 100})

    def test_branch_coverage_below_eighty_is_rejected(self):
        with self.assertRaises(QualityError):
            require_coverage({"line": 100, "branch": 79.9, "new_line": 100, "new_branch": 100})

    def test_low_new_code_coverage_is_not_hidden_by_global_coverage(self):
        with self.assertRaises(QualityError):
            require_coverage({"line": 99, "branch": 99, "new_line": 50, "new_branch": 100})

    def test_missing_global_branch_measurement_is_rejected(self):
        with self.assertRaises(QualityError):
            require_coverage({"line": 100, "branch": None, "new_line": 100, "new_branch": None})

    def test_eighty_is_accepted_and_no_new_branch_is_explicitly_unmeasurable(self):
        require_coverage({"line": 80, "branch": 80, "new_line": 80, "new_branch": None})

    def test_missing_complexity_report_is_rejected(self):
        with tempfile.TemporaryDirectory(dir=TEST_TEMP_ROOT) as directory:
            with self.assertRaises(QualityError):
                check_complexity(Path(directory) / "absent.xml")

    def test_excessive_cognitive_complexity_report_is_rejected(self):
        with tempfile.TemporaryDirectory(dir=TEST_TEMP_ROOT) as directory:
            report = Path(directory) / "detekt.xml"
            report.write_text('<checkstyle><file name="Example.kt"><error line="4" '
                              'source="detekt.CognitiveComplexMethod" message="complexity 16" />'
                              '</file></checkstyle>', encoding="utf-8")
            with self.assertRaises(QualityError):
                check_complexity(report)

    def test_duplication_over_three_percent_is_rejected(self):
        with self.assertRaises(QualityError):
            require_duplication(4, 100)

    def test_empty_duplication_input_is_not_a_success(self):
        with self.assertRaises(QualityError):
            require_duplication(0, 0)

    def test_three_percent_duplication_is_accepted(self):
        require_duplication(3, 100)

    def test_unapproved_license_is_rejected(self):
        modules = [{"moduleName": "example:library", "moduleVersion": "1.0",
                    "moduleLicenses": [{"moduleLicense": "AGPL-3.0-only"}]}]
        with self.assertRaises(QualityError):
            check_licenses(modules, {"Apache-2.0", "MIT"})

    def test_missing_license_is_rejected(self):
        with self.assertRaises(QualityError):
            check_licenses([{"moduleName": "example:unknown", "moduleLicenses": []}], {"MIT"})

    def test_declared_permissive_license_is_accepted(self):
        check_licenses([{"moduleName": "example:library", "moduleLicenses": [
            {"moduleLicense": "Apache-2.0"}]}], {"Apache-2.0"})

    def test_live_scan_incomplete_is_rejected(self):
        with self.assertRaises(QualityError):
            check_vulnerabilities({"status": "incomplete", "findings": []})

    def test_known_vulnerability_is_rejected_even_without_a_severity_score(self):
        with self.assertRaises(QualityError):
            check_vulnerabilities({"status": "complete", "findings": [
                {"id": "GHSA-example", "severity": "UNKNOWN"}]})

    def test_complete_empty_scan_is_accepted(self):
        check_vulnerabilities({"status": "complete", "findings": []})


if __name__ == "__main__":
    unittest.main()
