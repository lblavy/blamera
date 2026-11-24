import unittest
import numpy as np
from study_evaluation import score_lvis, score_ade


class Masks:
    def __init__(self, empty=False):
        self.empty = empty

    def get(self, p):
        return np.zeros((8, 8), bool) if self.empty else np.ones((8, 8), bool)


class EvaluationTests(unittest.TestCase):
    def setUp(self):
        self.mapping = {
            "groups": [{"key": "cat", "scope": "common", "lvis_ids": [225]}],
            "baseline_aliases": [
                {
                    "id": 0,
                    "name": "cat",
                    "target": "cat",
                    "possible_categories": [225],
                    "relation": "exact",
                }
            ],
            "pf_aliases": [],
            "ade_category_groups": {"building": [2, 26], "tree": [5], "plant": [18]},
            "ade_baseline_aliases": [{"id": 0, "name": "house", "target": "building"}],
            "ade_pf_aliases": [],
        }
        self.candidate = {"anchor": 0, "class": 0, "score": 0.8, "box": [0, 0, 8, 8]}
        self.truth = [
            {
                "id": 1,
                "category_id": 225,
                "target": "cat",
                "box": [0, 0, 8, 8],
                "mask": np.ones((8, 8), bool),
            }
        ]
        self.metadata = {
            "positive_category_ids": [225],
            "neg_category_ids": [],
            "not_exhaustive_category_ids": [],
        }

    def test_box_tp_survives_empty_mask_but_mask_scores_zero_and_fn_penalized(self):
        truth = self.truth + [dict(self.truth[0], id=2, box=[20, 20, 28, 28])]
        result = score_lvis(
            [self.candidate],
            truth,
            self.metadata,
            self.mapping,
            "baseline",
            Masks(True),
            1,
        )
        self.assertEqual(
            (result["categories"][0]["tp"], result["categories"][0]["fn"]), (1, 1)
        )
        self.assertEqual(result["empty_masks"], 1)
        self.assertEqual(result["masks"]["all_gt_mask_iou"], 0)
        self.assertEqual(result["masks"]["matched_gt_ids"], [1])
        self.assertEqual(result["masks"]["matched_only_iou"], 0)

    def test_unmapped_and_unverified_are_published_not_false_positive(self):
        metadata = {
            "positive_category_ids": [],
            "neg_category_ids": [],
            "not_exhaustive_category_ids": [],
        }
        predictions = [self.candidate, dict(self.candidate, anchor=1, **{"class": 1})]
        result = score_lvis(
            predictions, [], metadata, self.mapping, "baseline", None, 1
        )
        self.assertEqual(result["categories"][0]["fp"], 0)
        self.assertEqual(result["categories"][0]["ignored"], {"unverified": 1})
        self.assertEqual(result["unmapped_count"], 1)
        self.assertEqual(len(result["predictions"]), 2)
        self.assertIsNone(result["masks"])

    def test_control_localization_includes_annotated_objects_outside_target_families(
        self,
    ):
        metadata = {
            **self.metadata,
            "all_ground_truth": [
                *self.truth,
                {
                    "id": 8,
                    "target": "outside",
                    "category_id": 999,
                    "box": [20, 20, 28, 28],
                },
            ],
        }
        result = score_lvis(
            [self.candidate], self.truth, metadata, self.mapping, "baseline", None, 1
        )
        self.assertEqual(result["geometry"].get("all_annotated", {}).get("support"), 2)

    def test_wrong_label_perfect_mask_is_only_a_geometric_new_category_success(self):
        self.mapping["groups"].append({"key": "hat", "scope": "new", "lvis_ids": [100]})
        truth = [dict(self.truth[0], id=77, category_id=100, target="hat")]
        metadata = {
            "positive_category_ids": [100],
            "neg_category_ids": [225],
            "not_exhaustive_category_ids": [],
        }
        result = score_lvis(
            [self.candidate], truth, metadata, self.mapping, "baseline", Masks(), 1
        )
        self.assertEqual(sum(row["tp"] for row in result["categories"]), 0)
        self.assertEqual(result["geometry"]["new"]["tp"], 1)
        self.assertEqual(result["masks"]["all_gt_mask_iou"], 0)
        geometry = result.get("geometry_masks_new", {})
        self.assertEqual(geometry.get("all_gt_mask_iou"), 1)
        self.assertEqual(geometry["all_gt_boundary_f1"], 1)
        self.assertEqual(geometry["matched_gt_ids"], [77])
        missed = dict(truth[0], id=78, box=[0, 0, 4, 4])
        with_missed = score_lvis(
            [self.candidate],
            [*truth, missed],
            metadata,
            self.mapping,
            "baseline",
            Masks(),
            1,
        )
        self.assertEqual(with_missed["geometry_masks_new"]["all_gt_mask_iou"], 0.5)
        self.assertEqual(with_missed["geometry_masks_new"]["all_gt_boundary_f1"], 0.5)
        self.assertEqual(with_missed["geometry_masks_new"]["per_gt"]["78"]["iou"], 0)

    def test_ade_union_includes_house_and_penalizes_absent_class(self):
        gt = np.full((8, 8), 26, np.uint8)
        result = score_ade([self.candidate], gt, self.mapping, "baseline", Masks(), 1)
        self.assertEqual(result["semantic"]["building"]["intersection"], 64)
        absent = score_ade(
            [self.candidate],
            np.ones((8, 8), np.uint8),
            self.mapping,
            "baseline",
            Masks(),
            1,
        )
        self.assertEqual(absent["semantic"]["building"]["union"], 64)
        self.assertEqual(absent["semantic"]["building"]["intersection"], 0)


if __name__ == "__main__":
    unittest.main()
