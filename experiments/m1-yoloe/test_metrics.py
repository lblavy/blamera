import unittest
import numpy as np
from metrics import match_instances, mask_iou, box_iou


class MatchingTest(unittest.TestCase):
    def test_duplicate_and_wrong_class_cannot_both_match_one_ground_truth(self):
        mask = np.ones((3, 3), dtype=bool)
        p = [
            {"class": 0, "score": s, "box": [0, 0, 3, 3], "mask": mask}
            for s in [0.9, 0.8]
        ]
        p.append({"class": 1, "score": 0.95, "box": [0, 0, 3, 3], "mask": mask})
        r = match_instances(p, [{"class": 0, "box": [0, 0, 3, 3], "mask": mask}])
        self.assertEqual((r["tp"], r["fp"], r["fn"]), (1, 2, 0))
        self.assertEqual(r["matched_mask_iou"], [1.0])

    def test_missed_objects_remain_false_negatives(self):
        mask = np.ones((3, 3), dtype=bool)
        r = match_instances([], [{"class": 0, "box": [0, 0, 3, 3], "mask": mask}])
        self.assertEqual((r["tp"], r["fp"], r["fn"]), (0, 0, 1))
        self.assertAlmostEqual(box_iou([0, 0, 2, 2], [1, 1, 3, 3]), 1 / 7)

    def test_semantic_ignore_pixels_and_absent_union_are_not_fake_success(self):
        a = np.array([[1, 0], [0, 0]], bool)
        b = np.array([[1, 1], [0, 0]], bool)
        self.assertEqual(mask_iou(a, b, np.array([[1, 0], [1, 1]], bool)), 1.0)
        self.assertIsNone(mask_iou(np.zeros((2, 2), bool), np.zeros((2, 2), bool)))


if __name__ == "__main__":
    unittest.main()
