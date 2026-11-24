import unittest
from contextlib import contextmanager
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import numpy as np
from small_adapter import small_profiles, compare_references, aggregate_comparisons
from run_small import small_session
from runner_core import run_frames
from study_runner import StudyEvaluation
from study_summary import aggregate_records
import test_study_evaluation as fixtures


class SmallIntegrationTests(unittest.TestCase):
    def test_wrapped_native_boundary_canonicalizes_without_touching_reference_mapping(
        self,
    ):
        events = []
        mapping = {"pf_aliases": [{"id": 0, "name": "cat"}], "ade_pf_aliases": []}
        proof = {
            "canonical_from_small_indices": [1, 0],
            "canonical_names": {"0": "cat", "1": "hat"},
            "onnx": {"path": "new-small"},
        }
        raw = np.zeros((1, 38, 4), np.float32)
        raw[:, 4] = 0.9
        raw[:, 5] = 0.8

        @contextmanager
        def boundary(root, model, config, aliases):
            self.assertEqual(model, "yoloe")
            self.assertEqual(config["models"]["yoloe"]["path"], "new-small")
            self.assertEqual(aliases["pf_aliases"][0]["id"], 1)
            events.append("open")
            try:
                yield SimpleNamespace(
                    run=lambda tensor: [raw, np.zeros((1, 32, 160, 160), np.float32)]
                )
            finally:
                events.append("closed")

        with patch("run_small.ort_session", boundary):
            with small_session(Path("."), proof, {}, mapping) as native:
                actual, _ = native.run(None)
                self.assertEqual(native.names, {0: "cat", 1: "hat"})
                self.assertAlmostEqual(actual[0, 4, 0], 0.8)
                self.assertAlmostEqual(raw[0, 4, 0], 0.9)
        self.assertEqual(events, ["open", "closed"])
        self.assertEqual(mapping["pf_aliases"][0]["id"], 0)

    def test_small_only_reuses_real_scoring_engine_for_all_profiles_one_forward_per_image(
        self,
    ):
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
            calls.append("open-small")

            def forward(tensor):
                calls.append("forward")
                return raw, np.ones((1, 32, 160, 160), np.float32)

            try:
                yield SimpleNamespace(names={0: "cat", 1: "other"}, run=forward)
            finally:
                calls.append("close-small")

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
        forwards = run_frames(
            inputs,
            small_profiles(config),
            factory,
            lambda r: np.zeros((8, 8, 3), np.uint8),
            evaluator,
            rows.append,
        )
        self.assertEqual(forwards, 2)
        self.assertEqual(len(rows), 24)
        self.assertEqual(calls, ["open-small", "forward", "forward", "close-small"])
        result = aggregate_records(rows, mapping)
        self.assertEqual(len(result["profiles"]), 12)
        self.assertEqual(
            result["profiles"]["small-agnostic-c0.35-k20"]["masks"]["all_gt"]["iou"], 1
        )
        self.assertEqual(
            result["profiles"]["small-agnostic-c0.35-k20"]["semantic"]["building"][
                "iou"
            ],
            1,
        )
        references = [
            {**r, "profile": r["profile"].replace("small-", "yoloe-", 1)} for r in rows
        ]
        references += [
            {**r, "profile": r["profile"].replace("small-aware-", "baseline-aware-", 1)}
            for r in rows
            if r["profile"].startswith("small-aware-")
        ]
        pairs = compare_references(rows, references, mapping)
        self.assertEqual(len(pairs), 24)
        groups = aggregate_comparisons(pairs)
        self.assertEqual(
            groups["nano"]["common"]["rich_images"]["typed-agnostic-k20"]["matched_gt"],
            1,
        )
        self.assertEqual(
            groups["baseline"]["new"]["all"]["typed-agnostic-k20"]["matched_gt"], 0
        )


if __name__ == "__main__":
    unittest.main()
