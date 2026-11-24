"""Behavioral checks use synthetic predictions, never model downloads."""

import unittest

import numpy as np
from asset_core import compare_detections, packed_mask, raw_rows, validate_compact


class ContractTests(unittest.TestCase):
    def setUp(self):
        self.rows = np.zeros((1, 20, 38), np.float32)
        self.proto = np.ones((1, 32, 160, 160), np.float32)

    def test_raw_nms_preserves_classes_in_aware_mode_only(self):
        raw = np.zeros((1, 4 + 2 + 32, 8400), np.float32)
        raw[0, :4, :2] = np.array([[10, 10], [10, 10], [8, 8], [8, 8]])
        raw[0, 4, 0], raw[0, 5, 1] = 0.9, 0.8
        self.assertEqual(np.count_nonzero(raw_rows(raw, 2, False)[0, :, 4]), 2)
        self.assertEqual(np.count_nonzero(raw_rows(raw, 2, True)[0, :, 4]), 1)

    def test_raw_negative_has_twenty_padded_rows(self):
        result = raw_rows(np.zeros((1, 38, 8400), np.float32), 2, True)
        self.assertEqual(result.shape, (1, 20, 38))
        self.assertEqual(np.count_nonzero(result), 0)

    def test_contract_rejects_bad_shape_nonfinite_and_class(self):
        for change in ("shape", "finite", "class"):
            with self.subTest(change=change), self.assertRaises(ValueError):
                rows = self.rows.copy()
                if change == "shape":
                    rows = rows[:, :19]
                elif change == "finite":
                    rows[0, 0, 4] = np.nan
                else:
                    rows[0, 0, 4:6] = [0.8, 80]
                validate_compact(rows, self.proto, 80)

    def test_mask_is_cropped_to_box_and_letterbox_msb_first(self):
        row = np.zeros(38, np.float32)
        row[:6] = [0, 0, 8, 8, 0.8, 0]
        row[6] = 1
        projection = {"left": 4, "top": 0, "sw": 636, "sh": 640}
        packed = packed_mask(row, self.proto, projection)
        self.assertEqual(len(packed), 3200)
        self.assertEqual(packed[0], 0b01000000)
        self.assertEqual(packed[20], 0b01000000)
        self.assertEqual(sum(packed), 128)

    def test_parity_rejects_missing_wrong_class_box_score_and_mask(self):
        expected = [
            {
                "classId": 2,
                "score": 0.8,
                "box": [0, 0, 10, 10],
                "mask": bytes([255]) * 3200,
            }
        ]
        for changed in (
            [],
            [{**expected[0], "classId": 3}],
            [{**expected[0], "box": [0, 0, 12, 10]}],
            [{**expected[0], "score": 0.9}],
            [{**expected[0], "mask": bytes(3200)}],
        ):
            with self.subTest(changed=bool(changed)), self.assertRaises(ValueError):
                compare_detections(changed, expected)

    def test_parity_accepts_small_numeric_roundoff(self):
        expected = [
            {"classId": 2, "score": 0.8, "box": [0, 0, 10, 10], "mask": bytes(3200)}
        ]
        actual = [{**expected[0], "score": 0.800001, "box": [0.001, 0, 10, 10]}]
        self.assertEqual(compare_detections(actual, expected)["matched"], 1)


if __name__ == "__main__":
    unittest.main()
