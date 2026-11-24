import unittest
import numpy as np
from study_postprocess import select_candidates, build_profiles, FrameMasks


class PostprocessTests(unittest.TestCase):
    def tensors(self):
        return np.zeros((1, 38, 8400), np.float32), np.ones(
            (1, 32, 160, 160), np.float32
        )

    def projection(self):
        return {
            "w": 320,
            "h": 160,
            "scale": 2.0,
            "left": 0,
            "top": 160,
            "sw": 640,
            "sh": 320,
        }

    def add(self, raw, i, cls, score, box=(100, 240, 80, 80)):
        raw[0, :4, i] = box
        raw[0, 4 + cls, i] = score
        raw[0, 6, i] = 1

    def test_full_argmax_then_nms_preserves_unmapped_classes_and_projection(self):
        raw, proto = self.tensors()
        self.add(raw, 0, 0, 0.8)
        raw[0, 5, 0] = 0.9
        selected, stats = select_candidates(raw, proto, self.projection(), 2)
        self.assertEqual(selected[0]["class"], 1)
        np.testing.assert_allclose(selected[0]["box"], [30, 20, 70, 60])
        self.add(raw, 1, 0, 0.7)
        self.assertEqual(
            len(select_candidates(raw, proto, self.projection(), 2, agnostic=False)[0]),
            2,
        )
        self.assertEqual(
            len(select_candidates(raw, proto, self.projection(), 2, agnostic=True)[0]),
            1,
        )

    def test_pre_cap300_and_all18_declared_variants_are_bounded(self):
        raw, proto = self.tensors()
        for i in range(310):
            self.add(
                raw,
                i,
                0,
                0.9 - i * 0.0001,
                (10 + (i % 50) * 12, 170 + (i // 50) * 12, 4, 4),
            )
        selected, stats = select_candidates(raw, proto, self.projection(), 2, cap=100)
        self.assertEqual(stats["candidates_before_cap"], 310)
        self.assertEqual(stats["candidate_count"], 300)
        self.assertEqual(len(selected), 100)
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
        self.assertEqual(len(profiles), 18)
        self.assertEqual(len({p["key"] for p in profiles}), 18)
        self.assertEqual(sum(p["mask_metrics"] for p in profiles), 6)

    def test_bad_shapes_and_non_finite_values_fail_without_model(self):
        raw, proto = self.tensors()
        self.assertRaises(
            ValueError, select_candidates, raw[:, :, :2], proto, self.projection(), 2
        )
        raw[0, 0, 0] = np.nan
        self.assertRaises(
            ValueError, select_candidates, raw, proto, self.projection(), 2
        )

    def test_masks_are_cropped_in_source_coordinates_cached_and_empty_explicit(self):
        raw, proto = self.tensors()
        self.add(raw, 0, 0, 0.9)
        self.add(raw, 1, 1, 0.8, (200, 240, 40, 40))
        selected, _ = select_candidates(raw, proto, self.projection(), 2)
        cache = FrameMasks(raw, proto, self.projection(), 2, limit=1)
        one = cache.get(selected[0])
        self.assertEqual(np.count_nonzero(one), 1600)
        self.assertIs(one, cache.get(selected[0]))
        self.assertRaises(ValueError, cache.get, selected[1])
        empty = FrameMasks(raw, np.zeros_like(proto), self.projection(), 2).get(
            selected[0]
        )
        self.assertEqual(np.count_nonzero(empty), 0)


if __name__ == "__main__":
    unittest.main()
