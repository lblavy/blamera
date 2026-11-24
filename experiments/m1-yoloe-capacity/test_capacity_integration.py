import unittest
from pathlib import Path
from contextlib import contextmanager
from types import SimpleNamespace
import sys
import numpy as np
from capacity_adapter import (
    profiles,
    compare_references,
    aggregate_pairs,
    session_proof,
)
from run_capacity import StudyEvaluation
from runner_core import run_frames
from study_summary import aggregate_records
import test_study_evaluation as fixtures


class CapacityIntegrationTests(unittest.TestCase):
    def test_one_candidate_forward_per_image_all_profiles_and_three_references(self):
        fixture = fixtures.EvaluationTests()
        fixture.setUp()
        mapping = fixture.mapping
        mapping["pf_aliases"] = mapping["baseline_aliases"]
        mapping["ade_pf_aliases"] = mapping["ade_baseline_aliases"]

        class Truth:
            metadata = {
                1: {
                    **fixture.metadata,
                    "annotated_instances": 20,
                    "target_small_instances": 1,
                }
            }

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
        raw = np.zeros((1, 38, 8400), np.float32)
        raw[0, :4, 0] = [320, 320, 640, 640]
        raw[0, 4, 0] = 0.9
        raw[0, 6, 0] = 1
        calls = []

        @contextmanager
        def factory(model):
            self.assertEqual(model, "yoloe")
            calls.append("open-candidate")

            def forward(tensor):
                calls.append("forward")
                return raw, np.ones((1, 32, 160, 160), np.float32)

            try:
                yield SimpleNamespace(names={0: "cat", 1: "other"}, run=forward)
            finally:
                calls.append("close-candidate")

        inputs = [
            {
                "key": dataset + ":1",
                "dataset": dataset,
                "id": 1,
                "width": 8,
                "height": 8,
                "stratum": "targeted" if dataset == "lvis" else "ade_semantic",
            }
            for dataset in ["lvis", "ade"]
        ]
        rows = []
        count = run_frames(
            inputs,
            profiles(config, "x"),
            factory,
            lambda r: np.zeros((8, 8, 3), np.uint8),
            evaluator,
            rows.append,
        )
        self.assertEqual(count, 2)
        self.assertEqual(len(rows), 24)
        self.assertEqual(
            calls, ["open-candidate", "forward", "forward", "close-candidate"]
        )
        summary = aggregate_records(rows, mapping)
        self.assertEqual(len(summary["profiles"]), 12)
        self.assertEqual(
            summary["profiles"]["x-agnostic-c0.35-k20"]["semantic"]["building"]["iou"],
            1,
        )
        references = {
            "nano": [
                {**r, "profile": r["profile"].replace("x-", "yoloe-", 1)} for r in rows
            ],
            "small": [
                {**r, "profile": r["profile"].replace("x-", "small-", 1)} for r in rows
            ],
            "baseline": [
                {**r, "profile": r["profile"].replace("x-aware-", "baseline-aware-", 1)}
                for r in rows
                if r["profile"].startswith("x-aware-")
            ],
        }
        pairs = compare_references(rows, references, mapping)
        self.assertEqual(len(pairs), 36)
        self.assertEqual(
            aggregate_pairs(pairs)["small"]["common"]["rich_images"][
                "typed-agnostic-k20"
            ]["matched_gt"],
            1,
        )
        self.assertRaises(
            ValueError, compare_references, rows, {**references, "small": []}, mapping
        )
        self.assertNotIn("onnxruntime", sys.modules)
        self.assertNotIn("torch", sys.modules)

    def test_legacy_session_field_bridge_retains_explicit_candidate_identity(self):
        candidate = {
            "model": "YOLOE-26x-seg-pf",
            "canonical_from_candidate_indices": [1, 0],
            "canonical_names": {"0": "cat", "1": "hat"},
            "onnx": {"path": "new-candidate"},
        }
        bridged = session_proof(candidate)
        self.assertEqual(bridged["canonical_from_small_indices"], [1, 0])
        self.assertEqual(bridged["model"], "YOLOE-26x-seg-pf")
        self.assertNotIn("canonical_from_small_indices", candidate)


if __name__ == "__main__":
    unittest.main()
