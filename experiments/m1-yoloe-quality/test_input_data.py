import io
import unittest
from PIL import Image
from input_data import validate_image, duplicate_groups


class InputDataTests(unittest.TestCase):
    def jpeg(self, size=(12, 8)):
        output = io.BytesIO()
        Image.new("RGB", size, "red").save(output, format="JPEG")
        return output.getvalue()

    def test_valid_jpeg_records_original_hash_shape_and_perceptual_fingerprint(self):
        import hashlib

        data = self.jpeg()
        record = validate_image(data, {"width": 12, "height": 8})
        self.assertEqual(record["sha256"], hashlib.sha256(data).hexdigest())
        self.assertEqual((record["width"], record["height"]), (12, 8))
        self.assertEqual(len(record["dhash64"]), 16)

    def test_wrong_mime_bad_bytes_dimensions_and_excessive_length_fail_closed(self):
        for data, shape, mime in [
            (self.jpeg(), {"width": 13, "height": 8}, "image/jpeg"),
            (self.jpeg(), {"width": 12, "height": 8}, "text/html"),
            (b"<html>", {"width": 12, "height": 8}, "image/jpeg"),
            (b"x" * (20 * 1024 * 1024 + 1), {"width": 12, "height": 8}, "image/jpeg"),
        ]:
            with self.subTest(mime=mime, length=len(data)):
                self.assertRaises(ValueError, validate_image, data, shape, mime)

    def test_exact_and_near_duplicates_are_reported_without_replacing_ids(self):
        rows = [
            {"key": "a", "sha256": "same", "dhash64": "0000000000000000"},
            {"key": "b", "sha256": "same", "dhash64": "0000000000000000"},
            {"key": "c", "sha256": "other", "dhash64": "0000000000000001"},
        ]
        result = duplicate_groups(rows)
        self.assertEqual(result["exact"], [["a", "b"]])
        self.assertEqual(result["near"], [["a", "c", 1], ["b", "c", 1]])
        self.assertEqual([row["key"] for row in rows], ["a", "b", "c"])


if __name__ == "__main__":
    unittest.main()
