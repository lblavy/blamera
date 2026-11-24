import unittest
import numpy as np
from postprocess import prepare, decode


class PostprocessTest(unittest.TestCase):
    def test_agnostic_suppresses_overlapping_synonyms_but_class_aware_keeps_both(self):
        _, p = prepare(np.zeros((100, 200, 3), np.uint8))
        raw = np.zeros((1, 38, 8400), np.float32)
        for anchor in [0, 1]:
            raw[0, :4, anchor] = [320, 320, 200, 200]
            raw[0, 6, anchor] = 1
        raw[0, 4, 0] = 0.9
        raw[0, 5, 1] = 0.8
        proto = np.zeros((1, 32, 160, 160), np.float32)
        proto[0, 0] = 1
        normal, _ = decode(raw, proto, p, {0: "cat", 1: "feline"})
        agnostic, _ = decode(raw, proto, p, {0: "cat", 1: "feline"}, agnostic=True)
        self.assertEqual(len(normal), 2)
        self.assertEqual(len(agnostic), 1)
        self.assertEqual(agnostic[0]["class"], 0)
        np.testing.assert_allclose(agnostic[0]["box"], [68.75, 18.75, 131.25, 81.25])
        self.assertGreater(len(agnostic[0]["segments"]), 0)


if __name__ == "__main__":
    unittest.main()
