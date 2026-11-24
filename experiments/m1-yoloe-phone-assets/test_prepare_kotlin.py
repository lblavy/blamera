"""Tests for identity and provenance gates of the input-only variant."""

import copy
import unittest

from prepare_kotlin import kotlin_catalog, validate_inputs


class KotlinInputsTest(unittest.TestCase):
    def setUp(self):
        self.originals = [
            {
                "id": str(i),
                "width": 512,
                "height": 512,
                "pixelsSha256": "a" * 64,
                "tensorSha256": "b" * 64,
            }
            for i in range(14)
        ]
        self.manifest = {
            "inputs": [
                {
                    "id": str(i),
                    "width": 512,
                    "height": 512,
                    "pixelSha256": "a" * 64,
                    "pcPixelSha256": "a" * 64,
                    "tensorSha256": "c" * 64 if i == 0 else "b" * 64,
                    "pcTensorSha256": "b" * 64,
                }
                for i in range(14)
            ]
        }

    def test_changed_set_is_derived_from_pinned_tensor_hashes(self):
        self.assertEqual(validate_inputs(self.manifest, self.originals), ["0"])

    def test_identity_gate_rejects_missing_duplicate_pixels_size_or_parent_drift(self):
        for problem in (
            "missing",
            "duplicate",
            "pixels",
            "dimensions",
            "parent",
            "hash",
        ):
            changed = copy.deepcopy(self.manifest)
            if problem == "missing":
                changed["inputs"].pop()
            elif problem == "duplicate":
                changed["inputs"][1]["id"] = "0"
            elif problem == "pixels":
                changed["inputs"][0]["pixelSha256"] = "f" * 64
            elif problem == "dimensions":
                changed["inputs"][0]["width"] = 640
            elif problem == "parent":
                changed["inputs"][0]["pcTensorSha256"] = "f" * 64
            else:
                changed["inputs"][0]["tensorSha256"] = "invalid"
            with self.subTest(problem=problem), self.assertRaises(ValueError):
                validate_inputs(changed, self.originals)

    def test_catalog_changes_tensor_pins_only_and_rejects_missing_pins(self):
        source = "# Original\n0.sha256=unchanged\n0.tensor.sha256=" + "b" * 64 + "\n"
        result = kotlin_catalog(source, self.manifest["inputs"][:1])
        self.assertEqual(result, source.replace("b" * 64, "c" * 64))
        with self.assertRaises(ValueError):
            kotlin_catalog(source, self.manifest["inputs"][:2])


if __name__ == "__main__":
    unittest.main()
