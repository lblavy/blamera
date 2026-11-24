import hashlib, json, sys, tempfile, unittest
from pathlib import Path
import numpy as np
from freeze_study import verify_seal
from study_runner import StudyEvaluation
from study_postprocess import build_profiles
from study_summary import aggregate_records
from study_runtime import blocked_network
import test_study_evaluation as fixtures


class IntegrationTests(unittest.TestCase):
    def test_seal_rejects_changed_code_before_any_native_loading(self):
        with tempfile.TemporaryDirectory(
            dir="build/reports/validation/m1-yoloe-quality"
        ) as directory:
            root = Path(directory)
            code = root / "code.py"
            code.write_bytes(b"frozen")
            seal = root / "seal.json"
            seal.write_text(
                json.dumps(
                    {
                        "files": [
                            {
                                "path": "code.py",
                                "sha256": hashlib.sha256(b"frozen").hexdigest(),
                            }
                        ],
                        "versions": {},
                    }
                )
            )
            digest = hashlib.sha256(seal.read_bytes()).hexdigest()
            verify_seal(root, seal, digest)
            code.write_bytes(b"changed")
            self.assertRaises(ValueError, verify_seal, root, seal, digest)
            self.assertRaises(ValueError, verify_seal, root, seal, "wrong")

    def test_network_is_blocked_and_restored_without_loading_runtime(self):
        import socket

        original = socket.socket
        with blocked_network():
            self.assertRaisesRegex(RuntimeError, "Network forbidden", socket.socket)
        self.assertIs(socket.socket, original)
        self.assertNotIn("onnxruntime", sys.modules)
        self.assertNotIn("torch", sys.modules)

    def test_native_boundary_disables_telemetry_before_session_and_drops_owner(self):
        from unittest.mock import patch
        from types import SimpleNamespace
        from study_runtime import ort_session

        events = []
        names = {i: ("cat" if i == 0 else f"class-{i}") for i in range(80)}

        class Native:
            def __init__(self, *args, **kwargs):
                events.append("create")
                self.providers = kwargs["providers"]

            def get_modelmeta(self):
                return SimpleNamespace(custom_metadata_map={"names": repr(names)})

            def get_inputs(self):
                return [SimpleNamespace(name="images")]

            def run(self, *args):
                events.append("run")
                return [1, 2]

        fake = SimpleNamespace(
            SessionOptions=type("Options", (), {}),
            ExecutionMode=SimpleNamespace(ORT_SEQUENTIAL=0),
            GraphOptimizationLevel=SimpleNamespace(ORT_ENABLE_ALL=99),
            InferenceSession=Native,
            disable_telemetry_events=lambda: events.append("telemetry-off"),
        )
        mapping = {"groups": [], "baseline_aliases": [], "ade_baseline_aliases": []}
        with patch.dict(sys.modules, {"onnxruntime": fake}):
            with ort_session(
                Path("."),
                "baseline",
                {"models": {"baseline": {"path": "never-loaded"}}},
                mapping,
            ) as owned:
                self.assertEqual(owned.run(None), [1, 2])
                self.assertEqual(owned.native.providers, ["CPUExecutionProvider"])
            self.assertIsNone(owned.native)
        self.assertEqual(events, ["telemetry-off", "create", "run"])

    def test_image_axes_and_new_geometric_same_gt_scores_are_preserved(self):
        fixture = fixtures.EvaluationTests()
        fixture.setUp()
        mapping = fixture.mapping
        mapping["groups"].append({"key": "hat", "scope": "new", "lvis_ids": [100]})
        mapping["pf_aliases"] = [
            {
                "id": 0,
                "name": "hat",
                "target": "hat",
                "possible_categories": [100],
                "relation": "exact",
            }
        ]
        truth = [dict(fixture.truth[0], id=77, category_id=100, target="hat")]

        class Truth:
            metadata = {
                1: {
                    "positive_category_ids": [100],
                    "neg_category_ids": [225],
                    "not_exhaustive_category_ids": [],
                    "annotated_instances": 21,
                    "target_small_instances": 1,
                }
            }

            def instances(self, record):
                return truth

            def all_boxes(self, record):
                return truth

        evaluator = StudyEvaluation(
            Path("."), {"visual_ids": []}, mapping, Truth(), Path("unused")
        )
        raw = np.zeros((1, 38, 8400), np.float32)
        raw[0, :4, 0] = [320, 320, 640, 640]
        raw[0, 4, 0] = 0.9
        raw[0, 6, 0] = 1
        proto = np.ones((1, 32, 160, 160), np.float32)
        projection = {
            "w": 8,
            "h": 8,
            "scale": 80.0,
            "left": 0,
            "top": 0,
            "sw": 640,
            "sh": 640,
        }
        record = {
            "key": "lvis:1",
            "dataset": "lvis",
            "id": 1,
            "width": 8,
            "height": 8,
            "stratum": "targeted",
        }
        rows = []
        for model, agnostic in [("baseline", False), ("yoloe", True)]:
            profile = {
                "model": model,
                "agnostic_nms": agnostic,
                "confidence": 0.35,
                "cap": 20,
                "mask_metrics": True,
                "key": f"{model}-{'agnostic' if agnostic else 'aware'}-c0.35-k20",
            }
            rows.extend(
                evaluator(
                    record,
                    model,
                    {0: "hat" if model == "yoloe" else "cat", 1: "other"},
                    raw,
                    proto,
                    projection,
                    [profile],
                )
            )
        Truth.metadata[1].update(annotated_instances=19, target_small_instances=0)
        second_record = {**record, "key": "lvis:second"}
        profile = {
            "model": "baseline",
            "agnostic_nms": False,
            "confidence": 0.35,
            "cap": 20,
            "mask_metrics": True,
            "key": "baseline-aware-c0.35-k20",
        }
        rows.extend(
            evaluator(
                second_record,
                "baseline",
                {0: "cat", 1: "other"},
                raw,
                proto,
                projection,
                [profile],
            )
        )
        summary = aggregate_records(rows, mapping)
        baseline = summary["profiles"]["baseline-aware-c0.35-k20"]
        self.assertEqual(baseline["strata"].get("rich_images", {}).get("images"), 1)
        self.assertEqual(baseline["strata"]["targeted"]["images"], 2)
        self.assertEqual(
            baseline["strata"]["images_with_small_target"]["gt_instances"], 1
        )
        self.assertEqual(baseline["strata"]["rich_images"]["masks"]["all_gt"]["iou"], 0)
        self.assertEqual(
            baseline["strata"]["rich_images"]["geometry_masks_new"]["all_gt"]["iou"], 1
        )
        pairs = [
            p
            for p in summary["same_gt_pairs"]
            if p.get("matching") == "new_class_agnostic"
        ]
        self.assertEqual(len(pairs), 1)
        self.assertEqual(pairs[0]["gt_ids"], [77])
        self.assertEqual(pairs[0]["first"]["iou"], 1)
        axis_pairs = summary["paired_strata"]["rich_images"][
            "new_class_agnostic-agnostic-k20"
        ]
        self.assertEqual(axis_pairs["matched_gt"], 1)
        self.assertEqual(axis_pairs["first"]["boundary_f1"], 1)

    def test_real_scoring_pipeline_all_profiles_and_aggregates_with_synthetic_outputs(
        self,
    ):
        fixture = fixtures.EvaluationTests()
        fixture.setUp()
        mapping = fixture.mapping
        mapping["pf_aliases"] = mapping["baseline_aliases"]
        mapping["ade_pf_aliases"] = mapping["ade_baseline_aliases"]

        class Truth:
            metadata = {1: fixture.metadata}

            def instances(self, record):
                return fixture.truth

            def all_boxes(self, record):
                return fixture.truth

            def semantic(self, record):
                return np.full((8, 8), 26, np.uint8)

        evaluator = StudyEvaluation(
            Path("."), {"visual_ids": []}, mapping, Truth(), Path("unused")
        )
        config = {
            "profiles": [
                {"model": "baseline", "agnostic_nms": False},
                {"model": "yoloe", "agnostic_nms": True},
                {"model": "yoloe", "agnostic_nms": False},
            ],
            "confidence_sensitivity": [0.2, 0.35, 0.5],
            "max_detections": [20, 100],
        }
        profiles = build_profiles(config)
        raw = np.zeros((1, 38, 8400), np.float32)
        raw[0, :4, 0] = [320, 320, 640, 640]
        raw[0, 4, 0] = 0.9
        raw[0, 6, 0] = 1
        proto = np.ones((1, 32, 160, 160), np.float32)
        projection = {
            "w": 8,
            "h": 8,
            "scale": 80.0,
            "left": 0,
            "top": 0,
            "sw": 640,
            "sh": 640,
        }
        rows = []
        for model in ["baseline", "yoloe"]:
            chosen = [p for p in profiles if p["model"] == model]
            for dataset in ["lvis", "ade"]:
                record = {
                    "key": dataset + ":1",
                    "dataset": dataset,
                    "id": 1,
                    "width": 8,
                    "height": 8,
                    "stratum": "control" if dataset == "lvis" else "ade_semantic",
                }
                rows.extend(
                    evaluator(
                        record,
                        model,
                        {0: "cat", 1: "unmapped"},
                        raw,
                        proto,
                        projection,
                        chosen,
                    )
                )
        self.assertEqual(len(rows), 36)
        summary = aggregate_records(rows, mapping)
        self.assertEqual(len(summary["profiles"]), 18)
        main = summary["profiles"]["yoloe-agnostic-c0.35-k20"]
        self.assertEqual(main["lvis"]["categories"]["cat"]["tp"], 1)
        self.assertEqual(main["masks"]["all_gt"]["iou"], 1)
        self.assertEqual(main["semantic"]["building"]["iou"], 1)
        self.assertEqual(summary["same_gt_pairs"][0]["count"], 1)
        self.assertIsNone(summary["profiles"]["baseline-aware-c0.20-k20"]["semantic"])


if __name__ == "__main__":
    unittest.main()
