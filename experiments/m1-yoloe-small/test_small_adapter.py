import copy, unittest
import numpy as np
from small_adapter import (
    small_profiles,
    vocabulary_translation,
    canonical_raw,
    compare_references,
    native_mapping,
)


class SmallAdapterTests(unittest.TestCase):
    def test_twelve_distinct_small_profiles_preserve_all_declared_settings(self):
        config = {
            "profiles": [
                {"model": "baseline", "agnostic_nms": False},
                {"model": "yoloe", "agnostic_nms": True},
                {"model": "yoloe", "agnostic_nms": False},
            ],
            "confidence_sensitivity": [0.2, 0.35, 0.5],
            "max_detections": [20, 100],
        }
        rows = small_profiles(config)
        self.assertEqual(len(rows), 12)
        self.assertEqual(len({r["key"] for r in rows}), 12)
        self.assertTrue(
            all(r["key"].startswith("small-") and r["model"] == "yoloe" for r in rows)
        )
        self.assertEqual(sum(r["mask_metrics"] for r in rows), 4)

    def test_vocabulary_permutation_is_pure_index_translation_and_rejects_new_senses(
        self,
    ):
        reference = {0: "cat", 1: "hat", 2: "tree"}
        candidate = {0: "tree", 1: "cat", 2: "hat"}
        translation = vocabulary_translation(reference, candidate)
        self.assertEqual(translation, [1, 2, 0])
        raw = np.zeros((1, 39, 4), np.float32)
        raw[0, 4:7] = np.array([[0.7], [0.8], [0.9]])
        before = raw.copy()
        translated = canonical_raw(raw, translation)
        np.testing.assert_array_equal(
            translated[0, 4:7, 0], np.array([0.8, 0.9, 0.7], np.float32)
        )
        np.testing.assert_array_equal(raw, before)
        np.testing.assert_array_equal(translated[:, :4], raw[:, :4])
        np.testing.assert_array_equal(translated[:, 7:], raw[:, 7:])
        self.assertIs(canonical_raw(raw, [0, 1, 2]), raw)
        self.assertRaises(
            ValueError,
            vocabulary_translation,
            reference,
            {0: "cat", 1: "hat", 2: "pine"},
        )
        self.assertRaises(
            ValueError,
            vocabulary_translation,
            reference,
            {0: "cat", 1: "cat", 2: "tree"},
        )

    def test_native_alias_ids_follow_only_the_vocab_permutation(self):
        mapping = {
            "pf_aliases": [{"id": 0, "name": "cat", "possible_categories": [225]}],
            "ade_pf_aliases": [{"id": 2, "name": "tree"}],
            "groups": [],
        }
        before = copy.deepcopy(mapping)
        actual = native_mapping(mapping, [1, 2, 0])
        self.assertEqual(actual["pf_aliases"][0]["id"], 1)
        self.assertEqual(actual["ade_pf_aliases"][0]["id"], 0)
        self.assertEqual(actual["pf_aliases"][0]["possible_categories"], [225])
        self.assertEqual(mapping, before)

    def test_reference_pairs_are_same_gt_and_separate_names_geometry_and_common_new(
        self,
    ):
        def mask(ids, value):
            return {
                "matched_gt_ids": ids,
                "per_gt": {
                    "1": {"iou": value, "boundary_f1": value},
                    "2": {"iou": value, "boundary_f1": value},
                },
            }

        row = {
            "key": "lvis:1",
            "dataset": "lvis",
            "stratum": "targeted",
            "image_axes": ["targeted", "rich_images"],
            "gt_targets": {"1": "cat", "2": "hat"},
        }
        small = {
            **row,
            "profile": "small-agnostic-c0.35-k20",
            "masks": mask([1, 2], 0.8),
            "geometry_masks_new": mask([2], 0.9),
        }
        baseline = {
            **row,
            "profile": "baseline-aware-c0.35-k20",
            "masks": mask([1], 0.6),
            "geometry_masks_new": mask([2], 0.7),
        }
        nano = {
            **row,
            "profile": "yoloe-agnostic-c0.35-k20",
            "masks": mask([2], 0.4),
            "geometry_masks_new": mask([2], 0.5),
        }
        refs = [baseline, nano]
        before = copy.deepcopy(refs)
        mapping = {
            "groups": [
                {"key": "cat", "scope": "common"},
                {"key": "hat", "scope": "new"},
            ]
        }
        pairs = compare_references([small], refs, mapping)
        self.assertEqual(len(pairs), 6)
        bykey = {(r["reference_model"], r["matching"], r["scope"]): r for r in pairs}
        new = bykey["nano", "typed", "new"]
        self.assertEqual(new["gt_ids"], [2])
        self.assertEqual(new["first"]["iou"], 0.4)
        self.assertEqual(new["second"]["iou"], 0.8)
        self.assertEqual(bykey["baseline", "typed", "new"]["count"], 0)
        self.assertEqual(
            bykey["baseline", "new_class_agnostic", "new"]["first"]["iou"], 0.7
        )
        self.assertEqual(bykey["nano", "typed", "common"]["count"], 0)
        self.assertEqual(refs, before)


if __name__ == "__main__":
    unittest.main()
