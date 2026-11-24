"""The published preparation recipe must match checkpoint provenance."""

import unittest

from translate_catalogue import parse_arguments


class RecipeTests(unittest.TestCase):
    def test_default_and_explicit_settings_match_the_published_recipe(self):
        for argv in ([], ["--batch-size", "32", "--threads", "4"]):
            args = parse_arguments(argv)
            self.assertEqual((32, 4), (args.batch_size, args.threads))

    def test_other_batch_or_thread_settings_are_rejected_before_inference(self):
        for batch_size, threads in ((16, 2), (16, 4), (32, 2), (64, 4), (32, 1)):
            with self.subTest(batch_size=batch_size, threads=threads):
                with self.assertRaisesRegex(ValueError, "batch-size=32 and threads=4"):
                    parse_arguments(["--batch-size", str(batch_size), "--threads", str(threads)])


if __name__ == "__main__":
    unittest.main()
