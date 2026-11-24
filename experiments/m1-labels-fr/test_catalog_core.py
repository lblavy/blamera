import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from catalog_core import (
    checked_source,
    make_catalogue,
    read_checkpoint,
    validate_retained,
    verify_model_files,
)


class CatalogueTests(unittest.TestCase):
    def setUp(self):
        self.source = ["cat", "bat", "baseball bat"]
        self.raw = json.dumps(self.source).encode()
        self.sha = hashlib.sha256(self.raw).hexdigest()
        self.identity = {"englishSha256": self.sha, "revision": "pinned", "beams": 4}

    def test_source_preserves_exact_order_and_checks_bytes(self):
        self.assertEqual(self.source, checked_source(self.raw, self.sha, 3))
        with self.assertRaises(ValueError):
            checked_source(self.raw + b" ", self.sha, 3)

    def test_source_rejects_non_list_invalid_size_or_control_characters(self):
        for data in (
            {"cat": "chat"},
            ["cat"],
            ["cat", "", "bat"],
            ["cat\n", "bat", "dog"],
            ["cat\ufffd", "bat", "dog"],
        ):
            raw = json.dumps(data).encode()
            with self.assertRaises(ValueError):
                checked_source(raw, hashlib.sha256(raw).hexdigest(), 3)

    def test_checkpoint_resumes_only_an_exact_prefix_for_same_generation(self):
        raw = json.dumps({**self.identity, "translations": ["chat", "chauve-souris"]})
        self.assertEqual(
            ["chat", "chauve-souris"], read_checkpoint(raw, self.identity, self.source)
        )
        wrong = {**self.identity, "revision": "other", "translations": ["chat"]}
        with self.assertRaises(ValueError):
            read_checkpoint(json.dumps(wrong), self.identity, self.source)

    def test_checkpoint_rejects_truncated_invalid_or_oversized_progress(self):
        for translations in ([""], ["chat"] * 4, "chat", [None]):
            with self.assertRaises(ValueError):
                read_checkpoint(
                    json.dumps({**self.identity, "translations": translations}),
                    self.identity,
                    self.source,
                )

    def test_catalogue_preserves_indices_and_applies_explicit_semantic_overrides(self):
        catalogue = make_catalogue(
            self.source, ["chat", "batte", "batte de base-ball"], {"bat": "chauve-souris"}, self.sha
        )
        self.assertEqual(
            {
                "schema": 1,
                "locale": "fr",
                "englishSha256": self.sha,
                "labels": ["chat", "chauve-souris", "batte de base-ball"],
            },
            catalogue,
        )

    def test_catalogue_rejects_missing_translations_or_unknown_override_keys(self):
        for translated, overrides in (
            (["chat"], {}),
            (["chat", "batte", "batte"], {"dog": "chien"}),
            (["chat", "TODO", "batte"], {}),
        ):
            with self.assertRaises(ValueError):
                make_catalogue(self.source, translated, overrides, self.sha)

    def test_retained_names_require_exact_ids_values_and_reasons(self):
        source = ["bacon", "cat"]
        labels = ["bacon", "chat"]
        retained = [{"id": 0, "english": "bacon", "french": "bacon", "reason": "French loanword"}]
        self.assertEqual(1, validate_retained(source, labels, retained))
        for bad in (
            [],
            retained * 2,
            [{**retained[0], "id": 1}],
            [{**retained[0], "reason": ""}],
            [{**retained[0], "french": "pork"}],
        ):
            with self.assertRaises(ValueError):
                validate_retained(source, labels, bad)

    def test_model_hashes_are_checked_before_loading_even_if_sizes_match(self):
        with tempfile.TemporaryDirectory(
            dir=Path(__file__).resolve().parents[2] / ".tools/m1-labels-fr"
        ) as temporary:
            folder = Path(temporary)
            model = folder / "config.json"
            model.write_bytes(b"safe")
            files = [
                {"file": "config.json", "size": 4, "sha256": hashlib.sha256(b"safe").hexdigest()}
            ]
            self.assertEqual(1, verify_model_files(folder, files))
            model.write_bytes(b"evil")
            with self.assertRaises(ValueError):
                verify_model_files(folder, files)


if __name__ == "__main__":
    unittest.main()
