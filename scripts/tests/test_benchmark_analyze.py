import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from benchmark_analyze import analyze, checked_reference, main, markdown, reference_index
from benchmark_report import ReportError
from test_benchmark_metrics import fixture_report
from test_benchmark_report import wire


def reference_policy(root, doc):
    catalog = {}
    for model in doc["models"]:
        catalog[model["id"] + ".sha256"] = model["sha256"]
        catalog[model["id"] + ".size"] = str(model["bytes"])
    for fixture in doc["fixtures"]:
        catalog[fixture["id"] + ".sha256"] = fixture["sha256"]
        catalog[fixture["id"] + ".tensor.sha256"] = fixture["referenceTensorSha256"]
    catalog_path = root / "catalog.properties"
    catalog_path.write_text(
        "\n".join(key + "=" + value for key, value in catalog.items()), encoding="utf-8"
    )
    refs = []
    for row in doc["quality"]:
        path = root / "references" / row["model"] / (row["fixture"] + ".json")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            json.dumps(
                dict(
                    model=row["model"],
                    input=row["fixture"],
                    tensorSha256=row["tensorSha256"],
                    detections=[],
                )
            ),
            encoding="utf-8",
        )
        refs.append(
            dict(
                model=row["model"],
                input=row["fixture"],
                file=path.relative_to(root).as_posix(),
                sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
            )
        )
    catalog_sha = hashlib.sha256(catalog_path.read_bytes()).hexdigest()
    index = root / "index.json"
    index.write_text(json.dumps(dict(catalogSha256=catalog_sha, references=refs)), encoding="utf-8")
    policy = root / "config" / "reference-policy.json"
    policy.parent.mkdir()
    policy.write_text(
        json.dumps(
            {
                "index.file": "index.json",
                "index.sha256": hashlib.sha256(index.read_bytes()).hexdigest(),
                "catalog.file": "catalog.properties",
                "catalog.sha256": catalog_sha,
                "reference.root": "references",
            }
        ),
        encoding="utf-8",
    )
    return policy


class AnalyzeTests(unittest.TestCase):
    def test_supported_app_updates_share_the_same_pinned_reference_analysis(self):
        with tempfile.TemporaryDirectory(
            dir=Path(__file__).resolve().parents[2] / "build"
        ) as directory:
            root = Path(directory)
            doc = fixture_report()
            policy = reference_policy(root, doc)
            capture = root / "capture.log"
            summaries = []
            for version, code in [("0.6.0", 10), ("0.7.0", 11), ("0.8.0", 12)]:
                with self.subTest(version=version):
                    doc.update(appVersion=version, appVersionCode=code)
                    capture.write_text("\n".join(wire(doc)), encoding="utf-8")
                    report, summary = analyze(capture, root / "references", policy)
                    self.assertEqual(report["appVersion"], version)
                    self.assertEqual(len(summary["comparisons"]), 56)
                    summaries.append(summary)
            self.assertEqual(summaries[0], summaries[1])

    def test_pinned_index_identity_catalog_and_directory_are_checked_independently(self):
        with tempfile.TemporaryDirectory(
            dir=Path(__file__).resolve().parents[2] / "build"
        ) as directory:
            root = Path(directory)
            doc = fixture_report()
            policy_path = reference_policy(root, doc)
            refs = root / "references"
            self.assertRaises(ValueError, reference_index, policy_path, root / "other")
            index_path = root / "index.json"
            original = index_path.read_bytes()
            policy = json.loads(policy_path.read_text())
            for transform in [
                lambda d: d.update(catalogSha256="0" * 64),
                lambda d: d["references"].pop(),
            ]:
                index = json.loads(original)
                transform(index)
                index_path.write_text(json.dumps(index))
                policy["index.sha256"] = hashlib.sha256(index_path.read_bytes()).hexdigest()
                policy_path.write_text(json.dumps(policy))
                self.assertRaises(ValueError, reference_index, policy_path, refs)
            entry = json.loads(original)["references"][0]
            row = doc["quality"][0]
            wrong = {**entry, "file": "other/nasa.json"}
            self.assertRaises(
                ValueError, checked_reference, row, refs, root, {("baseline", "nasa"): wrong}
            )
            target = root / entry["file"]
            reference = json.loads(target.read_text())
            reference["model"] = "small"
            target.write_text(json.dumps(reference))
            entry["sha256"] = hashlib.sha256(target.read_bytes()).hexdigest()
            self.assertRaises(
                ValueError, checked_reference, row, refs, root, {("baseline", "nasa"): entry}
            )

    def test_wrong_index_altered_reference_and_same_tensor_wrong_model_are_rejected(self):
        with tempfile.TemporaryDirectory(
            dir=Path(__file__).resolve().parents[2] / "build"
        ) as directory:
            root = Path(directory)
            doc = fixture_report()
            policy = reference_policy(root, doc)
            log = root / "capture.log"
            log.write_text("\n".join(wire(doc)), encoding="utf-8")
            analyze(log, root / "references", policy)
            target = root / "references" / "baseline" / "nasa.json"
            original = target.read_bytes()
            altered = json.loads(original)
            altered["model"] = "small"
            target.write_text(json.dumps(altered), encoding="utf-8")
            self.assertRaises((ValueError, ReportError), analyze, log, root / "references", policy)
            target.write_bytes(original)
            index = root / "index.json"
            index.write_bytes(index.read_bytes() + b" ")
            self.assertRaises((ValueError, ReportError), analyze, log, root / "references", policy)

    def test_oversized_capture_is_refused_before_reading(self):
        with tempfile.TemporaryDirectory(
            dir=Path(__file__).resolve().parents[2] / "build"
        ) as directory:
            path = Path(directory) / "large.log"
            with path.open("wb") as stream:
                stream.seek(20 * 1024 * 1024)
                stream.write(b"x")
            self.assertRaises(ValueError, analyze, path, Path(directory))

    def test_complete_capture_recomputes_all_blocks_and_requires_each_pc_reference(self):
        with tempfile.TemporaryDirectory(
            dir=Path(__file__).resolve().parents[2] / "build"
        ) as directory:
            root = Path(directory)
            log = root / "capture.log"
            refs = root / "references"
            doc = fixture_report()
            log.write_text("\n".join(wire(doc)), encoding="utf-8")
            for row in doc["quality"]:
                path = refs / row["model"] / (row["fixture"] + ".json")
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(
                    json.dumps(dict(tensorSha256=row["tensorSha256"], detections=[])),
                    encoding="utf-8",
                )
            policy = reference_policy(root, doc)
            report, summary = analyze(log, refs, policy)
            self.assertEqual(report, doc)
            self.assertEqual(len(summary["comparisons"]), 56)
            self.assertTrue(all(r["sameTensor"] for r in summary["comparisons"]))
            self.assertIn("Total apparié", markdown(summary))
            catalog = {}
            for model in doc["models"]:
                catalog[model["id"] + ".sha256"] = model["sha256"]
                catalog[model["id"] + ".size"] = str(model["bytes"])
            for fixture in doc["fixtures"]:
                catalog[fixture["id"] + ".sha256"] = fixture["sha256"]
                catalog[fixture["id"] + ".tensor.sha256"] = fixture["referenceTensorSha256"]
            catalog_path = root / "catalog.properties"
            catalog_path.write_text(
                "\n".join(key + "=" + value for key, value in catalog.items()), encoding="utf-8"
            )
            self.assertEqual(
                main(
                    [
                        str(log),
                        "--references",
                        str(refs),
                        "--output",
                        str(root / "out"),
                        "--catalog",
                        str(catalog_path),
                        "--reference-policy",
                        str(policy),
                    ]
                ),
                0,
            )
            self.assertTrue((root / "out" / "report.json").exists())
            path.unlink()
            self.assertRaises(FileNotFoundError, analyze, log, refs, policy)
            log.write_text("\n".join(wire({**doc, "status": "cancelled"})), encoding="utf-8")
            self.assertRaises(ReportError, analyze, log, refs, policy)
