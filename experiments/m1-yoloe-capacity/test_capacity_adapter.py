import copy, tempfile, unittest
from pathlib import Path
from capacity_adapter import (
    profiles,
    destination,
    compare_references,
    aggregate_pairs,
    ORDER,
)


class CapacityTests(unittest.TestCase):
    def config(self):
        return {
            "profiles": [
                {"model": "baseline", "agnostic_nms": False},
                {"model": "yoloe", "agnostic_nms": True},
                {"model": "yoloe", "agnostic_nms": False},
            ],
            "confidence_sensitivity": [0.2, 0.35, 0.5],
            "max_detections": [20, 100],
        }

    def test_size_order_and_twelve_distinct_profiles_without_reference_models(self):
        self.assertEqual(ORDER, ("x", "m", "l"))
        for size in ORDER:
            rows = profiles(self.config(), size)
            self.assertEqual(len(rows), 12)
            self.assertEqual(len({r["key"] for r in rows}), 12)
            self.assertTrue(
                all(
                    r["key"].startswith(size + "-") and r["model"] == "yoloe"
                    for r in rows
                )
            )
            self.assertEqual(sum(r["mask_metrics"] for r in rows), 4)
        self.assertRaises(ValueError, profiles, self.config(), "s")

    def test_output_is_new_size_scoped_and_refuses_existing_results_or_bad_size(self):
        with tempfile.TemporaryDirectory(
            dir="build/reports/validation/m1-yoloe-capacity"
        ) as directory:
            root = Path(directory).resolve()
            path = destination(root, "x")
            self.assertEqual(
                path, root / "build/reports/validation/m1-yoloe-capacity/x/results-v1"
            )
            path.mkdir(parents=True)
            (path / "sentinel").write_text("keep")
            self.assertRaises(ValueError, destination, root, "x")
            self.assertEqual((path / "sentinel").read_text(), "keep")
            self.assertRaises(ValueError, destination, root, "../m1-yoloe-quality")

    def test_same_gt_pairs_retain_three_references_common_new_and_geometry(self):
        def mask(ids, value):
            return {
                "matched_gt_ids": ids,
                "per_gt": {
                    "1": {"iou": value, "boundary_f1": value},
                    "2": {"iou": value, "boundary_f1": value},
                },
            }

        common = {
            "key": "lvis:1",
            "dataset": "lvis",
            "stratum": "targeted",
            "image_axes": ["targeted", "rich_images"],
            "gt_targets": {"1": "cat", "2": "hat"},
        }
        candidate = {
            **common,
            "profile": "x-agnostic-c0.35-k20",
            "masks": mask([1, 2], 0.9),
            "geometry_masks_new": mask([2], 0.95),
        }
        refs = {
            name: [
                {
                    **common,
                    "profile": prefix + "-c0.35-k20",
                    "masks": mask(ids, value),
                    "geometry_masks_new": mask([2], value),
                }
            ]
            for name, prefix, ids, value in [
                ("baseline", "baseline-aware", [1], 0.5),
                ("nano", "yoloe-agnostic", [1, 2], 0.6),
                ("small", "small-agnostic", [2], 0.7),
            ]
        }
        before = copy.deepcopy(refs)
        mapping = {
            "groups": [
                {"key": "cat", "scope": "common"},
                {"key": "hat", "scope": "new"},
            ]
        }
        pairs = compare_references([candidate], refs, mapping)
        self.assertEqual(len(pairs), 9)
        lookup = {(p["reference_model"], p["scope"], p["matching"]): p for p in pairs}
        self.assertEqual(lookup["small", "new", "typed"]["first"]["iou"], 0.7)
        self.assertEqual(lookup["nano", "common", "typed"]["count"], 1)
        self.assertEqual(lookup["baseline", "new", "typed"]["count"], 0)
        self.assertEqual(lookup["baseline", "new", "new_class_agnostic"]["gt_ids"], [2])
        self.assertEqual(refs, before)
        groups = aggregate_pairs(pairs)
        self.assertEqual(
            groups["small"]["new"]["rich_images"]["typed-agnostic-k20"]["matched_gt"], 1
        )


if __name__ == "__main__":
    unittest.main()
