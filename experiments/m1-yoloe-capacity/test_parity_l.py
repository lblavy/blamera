import copy, unittest
from parity_l import validate_diagnosis


class LParityTests(unittest.TestCase):
    def valid(self):
        profiles = [
            {
                "agnostic": mode,
                "confidence": score,
                "cap": cap,
                "native_count": 1 if score == 0.35 else 0,
                "onnx_count": 1 if score == 0.35 else 0,
                "matches": 1 if score == 0.35 else 0,
                "same_anchor_class_order": True,
                "mask_iou_min": 1.0 if score == 0.35 else None,
            }
            for mode in [True, False]
            for score in [0.2, 0.35, 0.5]
            for cap in [20, 100]
        ]
        return {
            "fixtures": [
                {
                    "id": identifier,
                    "channel_errors": {
                        name: {"max_abs": value}
                        for name, value in [
                            ("coordinates", 0.04),
                            ("scores", 0.001),
                            ("coefficients", 0.002),
                            ("prototypes", 0.001),
                        ]
                    },
                    "argmax": {"above_confidence": {"0.2": 0, "0.35": 0, "0.5": 0}},
                    "profiles": copy.deepcopy(profiles),
                }
                for identifier in ["nasa-astronaut", "synthetic-negative"]
            ]
        }

    def test_accepts_reviewed_subpixel_only_exception_and_mask_support_at_main_score(
        self,
    ):
        validate_diagnosis(self.valid())

    def test_rejects_coordinate_score_prototype_or_decision_drift_without_relaxing_bounds(
        self,
    ):
        for field, value in [
            ("coordinates", 0.05001),
            ("scores", 0.02001),
            ("coefficients", 0.02001),
            ("prototypes", 0.00501),
        ]:
            data = self.valid()
            data["fixtures"][0]["channel_errors"][field]["max_abs"] = value
            self.assertRaises(ValueError, validate_diagnosis, data)
        for field, value in [
            ("same_anchor_class_order", False),
            ("onnx_count", 2),
            ("mask_iou_min", 0.98),
        ]:
            data = self.valid()
            data["fixtures"][0]["profiles"][2][field] = value
            self.assertRaises(ValueError, validate_diagnosis, data)
        data = self.valid()
        data["fixtures"][0]["argmax"]["above_confidence"]["0.2"] = 1
        self.assertRaises(ValueError, validate_diagnosis, data)
        data = self.valid()
        data["fixtures"].pop()
        self.assertRaises(ValueError, validate_diagnosis, data)


if __name__ == "__main__":
    unittest.main()
