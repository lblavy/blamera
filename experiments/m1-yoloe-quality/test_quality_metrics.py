import unittest
import numpy as np
from quality_metrics import (
    semantic_counts,
    aggregate_semantic,
    aggregate_categories,
    paired_mask_scores,
)


class QualityMetricTests(unittest.TestCase):
    def test_absent_class_false_positive_contributes_to_union_and_boundary_counts(self):
        truth = np.zeros((8, 8), bool)
        truth[2:4, 2:4] = True
        valid = np.ones_like(truth)
        positive = semantic_counts(truth, truth, valid, 0)
        absent = semantic_counts(truth, np.zeros_like(truth), valid, 0)
        result = aggregate_semantic([positive, absent])
        self.assertEqual(result["intersection"], 4)
        self.assertEqual(result["union"], 8)
        self.assertEqual(result["iou"], 0.5)
        self.assertAlmostEqual(result["boundary_f1"], 2 / 3)

    def test_void_values_never_add_edges_or_change_verified_scores(self):
        valid = np.ones((9, 9), bool)
        valid[:, 4:6] = False
        truth = np.zeros_like(valid)
        truth[2:7, 1:4] = True
        a = semantic_counts(truth, truth, valid, 1)
        b = semantic_counts(truth | ~valid, truth ^ ~valid, valid, 1)
        self.assertEqual(a, b)
        empty = semantic_counts(np.ones_like(valid), truth, np.zeros_like(valid), 1)
        self.assertIsNone(aggregate_semantic([empty])["iou"])
        self.assertIsNone(aggregate_semantic([empty])["boundary_f1"])
        none = semantic_counts(np.zeros_like(valid), np.zeros_like(valid), valid, 1)
        self.assertIsNone(aggregate_semantic([none])["boundary_f1"])

    def test_micro_macro_and_unknown_counts_are_distinct(self):
        result = aggregate_categories(
            [
                {
                    "category": "common",
                    "tp": 10,
                    "fp": 0,
                    "fn": 0,
                    "ignored": {"unverified": 2},
                },
                {
                    "category": "novel",
                    "tp": 0,
                    "fp": 0,
                    "fn": 1,
                    "ignored": {"outside_target_scope": 3},
                },
            ]
        )
        self.assertAlmostEqual(result["micro"]["recall"], 10 / 11)
        self.assertEqual(result["macro"]["recall"], 0.5)
        self.assertIsNone(result["categories"]["novel"]["precision"])
        self.assertEqual(result["micro"]["ignored"]["outside_target_scope"], 3)

    def test_paired_comparison_uses_the_same_matched_gt_ids(self):
        a = {
            "matched_gt_ids": [1, 2],
            "per_gt": {
                "1": {"iou": 1.0, "boundary_f1": 0.8},
                "2": {"iou": 0.2, "boundary_f1": 0.1},
            },
        }
        b = {
            "matched_gt_ids": [1, 3],
            "per_gt": {
                "1": {"iou": 0.5, "boundary_f1": 0.4},
                "3": {"iou": 1.0, "boundary_f1": 1.0},
            },
        }
        result = paired_mask_scores(a, b)
        self.assertEqual(result["gt_ids"], [1])
        self.assertEqual(result["first"]["iou"], 1.0)
        self.assertEqual(result["second"]["iou"], 0.5)
        self.assertIsNone(
            paired_mask_scores({"matched_gt_ids": [], "per_gt": {}}, b)["first"]["iou"]
        )


if __name__ == "__main__":
    unittest.main()
