import unittest
import numpy as np
from protocol_core import (
    validate_aliases,
    evaluate_predictions,
    mask_quality,
    select_metadata,
    rasterize_polygons,
    localization_quality,
)


class SemanticAndPartialTests(unittest.TestCase):
    def pred(self, category, possible, box=(0, 0, 10, 10), score=0.9):
        return {
            "target": category,
            "possible_categories": possible,
            "box": box,
            "score": score,
        }

    def gt(self, category=1, box=(0, 0, 10, 10)):
        return {"id": 1, "target": "cat", "category_id": category, "box": box}

    def test_subtypes_are_explicit_and_substring_false_friends_do_not_match(self):
        vocabulary = {1: "American shorthair", 2: "cat bed"}
        rows = [
            {
                "id": 1,
                "name": "American shorthair",
                "target": "cat",
                "relation": "subtype",
                "possible_categories": [1],
            }
        ]
        aliases = validate_aliases(rows, vocabulary)
        self.assertEqual(aliases[1]["target"], "cat")
        self.assertNotIn(2, aliases)
        self.assertRaises(
            ValueError,
            validate_aliases,
            rows + [dict(rows[0], target="dog")],
            vocabulary,
        )
        self.assertRaises(
            ValueError, validate_aliases, [dict(rows[0], name="cat")], vocabulary
        )

    def test_verified_negative_is_fp_unknown_is_ignored_and_partial_unmatched_ignored(
        self,
    ):
        prediction = self.pred("cat", [1])
        self.assertEqual(evaluate_predictions([prediction], [], [], [1], [])["fp"], 1)
        self.assertEqual(
            evaluate_predictions([prediction], [], [], [], [])["ignored"],
            ["unverified"],
        )
        distant = self.pred("cat", [1], (20, 20, 30, 30))
        result = evaluate_predictions([distant], [self.gt()], [1], [], [1])
        self.assertEqual(result["ignored"], ["non_exhaustive"])
        self.assertEqual(result["fn"], 1)

    def test_coarse_family_is_not_closed_by_one_positive_member(self):
        coarse = self.pred("cat", [1, 2], (20, 20, 30, 30))
        result = evaluate_predictions([coarse], [self.gt()], [1], [], [])
        self.assertEqual(result["ignored"], ["unverified"])
        self.assertEqual(result["fp"], 0)
        self.assertEqual(
            evaluate_predictions([coarse], [self.gt()], [1], [2], [])["fp"], 1
        )

    def test_one_to_one_semantic_matching_keeps_duplicate_fp_and_wrong_category_fn(
        self,
    ):
        first = self.pred("cat", [1])
        second = self.pred("cat", [1], score=0.8)
        result = evaluate_predictions([first, second], [self.gt()], [1], [], [])
        self.assertEqual((result["tp"], result["fp"], result["fn"]), (1, 1, 0))
        wrong = self.pred("dog", [2])
        result = evaluate_predictions([wrong], [self.gt()], [1], [2], [])
        self.assertEqual((result["tp"], result["fp"], result["fn"]), (0, 1, 1))

    def test_mask_scores_include_missed_objects_as_zero_and_keep_holes(self):
        mask = np.ones((9, 9), dtype=bool)
        mask[3:6, 3:6] = False
        gt = [{"id": 1, "mask": mask}, {"id": 2, "mask": mask}]
        pred = [{"mask": mask.copy()}]
        result = mask_quality(gt, pred, [(0, 0)])
        self.assertEqual(result["all_gt_mask_iou"], 0.5)
        self.assertEqual(result["all_gt_boundary_f1"], 0.5)
        filled = [{"mask": np.ones_like(mask)}]
        self.assertLess(mask_quality(gt, filled, [(0, 0)])["all_gt_boundary_f1"], 0.5)

    def test_selection_is_deterministic_and_control_independent_of_categories(self):
        pool = [
            {"id": i, "categories": {1 if i % 2 else 2}, "rich": False, "small": False}
            for i in range(12)
        ]
        one = select_metadata(pool, {"a": {1}, "b": {2}}, 17, 2, 4, 1)
        two = select_metadata(list(reversed(pool)), {"a": {1}, "b": {2}}, 17, 2, 4, 1)
        self.assertEqual(one, two)
        changed = select_metadata(pool, {"a": {1}}, 17, 2, 4, 1)
        self.assertEqual(one["control"], changed["control"])
        self.assertEqual(len(set(one["control"] + one["targeted"])), 6)
        self.assertRaises(
            ValueError, select_metadata, pool, {"missing": {9}}, 17, 2, 4, 1
        )

    def test_official_polygon_rasterization_uses_pixel_area_and_component_union(self):
        first = [0, 0, 2, 0, 2, 2, 0, 2]
        second = [1, 0, 3, 0, 3, 2, 1, 2]
        mask = rasterize_polygons([first], 4, 4)
        self.assertEqual(np.count_nonzero(mask), 4)
        self.assertEqual(np.count_nonzero(rasterize_polygons([first, second], 4, 4)), 6)
        self.assertRaises(ValueError, rasterize_polygons, [], 4, 4)

    def test_unknown_typed_label_can_localize_a_new_object_without_claiming_its_name(
        self,
    ):
        pred = [self.pred("person", [2])]
        gt = [self.gt()]
        self.assertEqual(evaluate_predictions(pred, gt, [1], [2], [])["tp"], 0)
        self.assertEqual(localization_quality(pred, gt)["recall"], 1.0)
        self.assertEqual(localization_quality([], gt)["recall"], 0.0)
        self.assertIsNone(localization_quality([], [])["recall"])

    def test_broad_feline_with_cougar_unverified_never_becomes_false_positive(self):
        import json
        from pathlib import Path

        aliases = json.loads(
            (Path(__file__).parent / "mapping-proposal.json").read_text(
                encoding="utf-8"
            )
        )["pf_aliases"]
        feline = next(row for row in aliases if row["name"] == "feline")
        pred = dict(feline, box=(0, 0, 10, 10), score=0.9)
        verified_absent = set(pred["possible_categories"]) - {316}
        result = evaluate_predictions([pred], [], [], verified_absent, [])
        self.assertEqual(result["fp"], 0)
        self.assertEqual(result["ignored"], ["outside_target_scope"])

    def test_true_outside_scope_lion_or_paper_towel_is_explicitly_ignored(self):
        import json
        from pathlib import Path

        aliases = json.loads(
            (Path(__file__).parent / "mapping-proposal.json").read_text(
                encoding="utf-8"
            )
        )["pf_aliases"]
        for name, outside in [("feline", 649), ("towel", 757)]:
            alias = next(row for row in aliases if row["name"] == name)
            pred = dict(alias, box=(0, 0, 10, 10), score=0.9)
            negative = set(pred["possible_categories"]) - {outside}
            result = evaluate_predictions([pred], [], [outside], negative, [])
            self.assertEqual((result["tp"], result["fp"]), (0, 0), name)
            self.assertEqual(result["ignored"], ["outside_target_scope"], name)
        matched = dict(pred, target="towel", possible_categories=[1108])
        truth = {"id": 1, "target": "towel", "category_id": 1108, "box": (0, 0, 10, 10)}
        self.assertEqual(
            evaluate_predictions([matched], [truth], [1108], [], [])["tp"], 1
        )

    def test_old_ade_exclusions_are_case_insensitive_and_nonempty(self):
        from protocol_core import previous_ade_samples

        rows = [
            {"id": "ADE_train_01", "sha256": "one"},
            {"id": "ade_train_02", "sha256": "two"},
            {"id": "coco_03", "sha256": "three"},
        ]
        self.assertEqual(previous_ade_samples(rows), rows[:2])

    def test_dilation_matches_spatial_neighbourhood_at_edges_and_radii(self):
        from protocol_core import dilate

        masks = [np.zeros((7, 11), dtype=bool), np.ones((7, 11), dtype=bool)]
        sparse = np.zeros((7, 11), dtype=bool)
        sparse[0, 0] = True
        sparse[-1, -1] = True
        sparse[3, 5] = True
        masks.append(sparse)
        for mask in masks:
            for radius in [0, 1, 6]:
                expected = np.zeros_like(mask)
                for y, x in zip(*np.nonzero(mask)):
                    expected[
                        max(0, y - radius) : y + radius + 1,
                        max(0, x - radius) : x + radius + 1,
                    ] = True
                np.testing.assert_array_equal(dilate(mask, radius), expected)

    def test_alias_leaving_gt_scope_requires_conservative_unmatched_policy(self):
        alias = {
            "id": 1,
            "name": "feline",
            "target": "cat",
            "relation": "coarse",
            "possible_categories": [1, 2],
        }
        self.assertRaises(
            ValueError, validate_aliases, [alias], {1: "feline"}, {"cat": {1}}
        )
        alias["unmatched_policy"] = "always_unknown"
        self.assertEqual(
            validate_aliases([alias], {1: "feline"}, {"cat": {1}})[1][
                "unmatched_policy"
            ],
            "always_unknown",
        )


if __name__ == "__main__":
    unittest.main()
