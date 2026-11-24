import base64
import copy
import hashlib
import sys
import unittest
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from benchmark_metrics import compare_objects, unpack_mask, validate_catalog, validate_report
from benchmark_report import ReportError


def fixture_report():
    sample = dict(runNs=10, copyNs=2, postNs=3, totalNs=15, detections=0, emptyMasks=0, segments=0)

    def stats(n):
        return dict(count=10, medianNs=float(n), minNs=n, maxNs=n)

    ids = ["nasa", "synthetic", "lvis_371699", *[f"fixture{i}" for i in range(11)]]
    models = [
        dict(
            id=name,
            sha256="a" * 64,
            sourceSha256="b" * 64,
            bytes=1,
            classes=80 if name == "baseline" else 4585,
            agnostic=name != "baseline",
        )
        for name in ["baseline", "small", "large", "xlarge"]
    ]
    blocks = [
        dict(
            model=m["id"],
            fixture=f,
            fixtureHash="c" * 64,
            tensorSha256="d" * 64,
            sessionFirst=f == "nasa",
            first=sample.copy(),
            samples=[sample.copy() for _ in range(10)],
            run=stats(10),
            copy=stats(2),
            post=stats(3),
            total=stats(15),
        )
        for m in models
        for f in ["nasa", "lvis_371699"]
    ]
    quality = [
        dict(
            model=m["id"],
            fixture=f,
            tensorSha256="d" * 64,
            width=100,
            height=50,
            sample=sample.copy(),
            objects=[],
        )
        for m in models
        for f in ids
    ]
    return dict(
        schema=2,
        protocol="capacity-phone-v1",
        runId="run-test",
        status="complete",
        appVersion="0.6.0",
        appVersionCode=10,
        settings=dict(
            cpu=True,
            intra=2,
            inter=1,
            edge=640,
            confidence=0.35,
            iou=0.5,
            maxDetections=20,
            preCap=None,
            nmsEmbedded=True,
            warmups=3,
            measured=10,
            performanceInputs=["nasa", "lvis_371699"],
        ),
        models=models,
        fixtures=[dict(id=f, sha256="c" * 64, referenceTensorSha256="d" * 64) for f in ids],
        blocks=blocks,
        quality=quality,
        timings=[
            dict(
                model=m["id"],
                readHashNs=1,
                sessionNs=2,
                cacheHit=False,
                pssBeforeModelKiB=None,
                pssAfterSessionCloseKiB=3,
            )
            for m in models
        ],
    )


class BenchmarkMetricsTests(unittest.TestCase):
    def test_exact_supported_app_version_pairs_preserve_the_protocol_summary(self):
        expected = validate_report(fixture_report())
        for version, code in [
            ("0.6.0", 10), ("0.7.0", 11), ("0.8.0", 12), ("0.9.0", 13), ("0.9.1", 14),
            ("0.9.2", 15), ("0.9.3", 16), ("0.9.4", 17), ("0.10.0", 18), ("0.10.1", 19),
            ("0.10.2", 20), ("0.10.3", 21), ("0.10.4", 22), ("0.11.0", 23), ("0.12.0", 24), ("0.13.0", 25), ("0.14.0", 26), ("0.14.1", 27), ("0.15.0", 28), ("0.15.1", 29)
        ]:
            with self.subTest(version=version, code=code):
                doc = fixture_report()
                doc.update(appVersion=version, appVersionCode=code)
                self.assertEqual(validate_report(doc), expected)

    def test_crossed_unknown_and_malformed_app_versions_are_rejected(self):
        for version, code in [
            ("0.6.0", 11),
            ("0.7.0", 10),
            ("0.10.0", 14),
            ("0.9.0", 12),
            ("0.9.0", 14),
            ("0.9.1", 13),
            ("0.9.1", 15),
            ("0.9.2", 14),
            ("0.9.3", 15), ("0.9.2", 16),
            ("0.9.4", 16), ("0.9.3", 17),
            ("0.10.0", 17), ("0.9.4", 18), ("0.10.1", 18), ("0.10.0", 19), ("0.10.2", 19),
            ("0.10.1", 20), ("0.10.3", 20), ("0.10.2", 21), ("0.10.4", 21), ("0.10.3", 22), ("0.10.5", 22),
            ("0.11.0", 22), ("0.10.4", 23), ("0.12.0", 23), ("0.11.0", 24), ("0.13.0", 24), ("0.12.0", 25), ("0.14.0", 25), ("0.13.0", 26), ("0.14.1", 26), ("0.14.0", 27), ("0.15.0", 27), ("0.14.1", 28),
            ("0.8.0", 13),
            ("0.8.0", 10),
            ("0.8.0", 11),
            ("0.7.0", 12),
            ("0.6.0", 10.0),
            (None, 10),
            ("0.6.0", True),
        ]:
            with self.subTest(version=version, code=code):
                doc = fixture_report()
                doc.update(appVersion=version, appVersionCode=code)
                self.assertRaises(ReportError, validate_report, doc)
        missing = fixture_report()
        missing.pop("appVersion")
        self.assertRaises(ReportError, validate_report, missing)

    def test_current_app_version_does_not_relax_the_existing_protocol(self):
        for change in [
            lambda doc: doc.update(schema=3),
            lambda doc: doc.update(protocol="capacity-phone-v2"),
            lambda doc: doc.update(status="running"),
            lambda doc: doc["settings"].update(confidence=0.2),
            lambda doc: doc["models"][0].update(classes=4585),
        ]:
            doc = fixture_report()
            doc.update(appVersion="0.11.0", appVersionCode=23)
            change(doc)
            self.assertRaises(ReportError, validate_report, doc)

    def test_catalog_pins_actual_models_and_fixture_tensors_not_just_well_formed_hashes(self):
        doc = fixture_report()
        catalog = {}
        for row in doc["models"]:
            catalog[row["id"] + ".sha256"] = row["sha256"]
            catalog[row["id"] + ".size"] = str(row["bytes"])
        for row in doc["fixtures"]:
            catalog[row["id"] + ".sha256"] = row["sha256"]
            catalog[row["id"] + ".tensor.sha256"] = row["referenceTensorSha256"]
        validate_catalog(doc, catalog)
        doc["models"][0]["sha256"] = "0" * 64
        self.assertRaises(ReportError, validate_catalog, doc, catalog)

    def test_same_geometry_can_expose_name_difference_without_calling_it_ground_truth(self):
        raw = bytes([128]) + bytes(3199)
        mask = dict(
            codec="zlib-bitpack-msb",
            width=160,
            height=160,
            data=base64.b64encode(zlib.compress(raw)).decode(),
            sha256=hashlib.sha256(raw).hexdigest(),
            activePixels=1,
            empty=False,
        )
        actual = dict(classId=1, nameEnglish="cat", score=0.8, box=[0, 0, 10, 10], mask=mask)
        reference = dict(
            classId=2,
            nameEnglish="dog",
            score=0.9,
            box=[0, 0, 10, 10],
            maskBase64=base64.b64encode(raw).decode(),
            maskSha256=hashlib.sha256(raw).hexdigest(),
        )
        comparison = compare_objects([actual], [reference])
        self.assertEqual(comparison["pairs"][0]["maskIoU"], 1)
        self.assertFalse(comparison["pairs"][0]["sameClass"])
        self.assertEqual(comparison["unmatchedApp"], 0)
        self.assertEqual(compare_objects([actual], [])["unmatchedApp"], 1)

    def test_actual_paired_timings_counts_and_reference_tensor_status_are_validated(self):
        summary = validate_report(fixture_report())
        self.assertEqual(summary["nativeCalls"], 168)
        self.assertEqual(summary["blocks"][0]["totalMedianNs"], 15)
        self.assertEqual(summary["tensorMismatches"], [])

    def test_missing_duplicate_inconsistent_stats_status_and_nan_are_rejected(self):
        doc = fixture_report()
        cases = []
        for change in [
            lambda x: x.update(status="cancelled"),
            lambda x: x["blocks"].pop(),
            lambda x: x["quality"].append(x["quality"][0]),
            lambda x: x["blocks"][0]["run"].update(medianNs=9),
            lambda x: x["blocks"][0]["samples"][0].update(totalNs=20),
            lambda x: x["blocks"][0]["samples"][0].update(runNs=float("nan")),
            lambda x: x["quality"][0].update(tensorSha256="e" * 64),
            lambda x: x["models"][0].update(classes=4585),
        ]:
            altered = copy.deepcopy(doc)
            change(altered)
            cases.append(altered)
        for altered in cases:
            with self.subTest(case=cases.index(altered)):
                self.assertRaises(ReportError, validate_report, altered)

    def test_bounded_mask_decode_checks_hash_active_pixels_padding_and_zip_bombs(self):
        raw = bytes([128]) + bytes(3199)
        mask = dict(
            codec="zlib-bitpack-msb",
            width=160,
            height=160,
            data=base64.b64encode(zlib.compress(raw)).decode(),
            sha256=hashlib.sha256(raw).hexdigest(),
            activePixels=1,
            empty=False,
        )
        self.assertEqual(unpack_mask(mask), raw)
        for altered in [
            {**mask, "sha256": "0" * 64},
            {**mask, "activePixels": 0},
            {**mask, "empty": True},
            {**mask, "data": "!"},
            {**mask, "data": base64.b64encode(zlib.compress(bytes(1000000))).decode()},
            {**mask, "data": base64.b64encode(zlib.compress(raw) + b"extra").decode()},
        ]:
            self.assertRaises(ReportError, unpack_mask, altered)


if __name__ == "__main__":
    unittest.main()
