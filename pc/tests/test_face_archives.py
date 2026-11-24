"""Only the requested verified model is extracted from a pinned archive."""

import hashlib
import json
import tempfile
import unittest
import warnings
import zipfile
from dataclasses import replace
from io import BytesIO
from pathlib import Path
from unittest.mock import patch

from src.blamera_pc.face_data import FaceProfile
from src.blamera_pc.face_models import (
    MANIFEST,
    FaceConfiguration,
    ModelArchive,
    ModelFile,
    load_configuration,
    provision,
)
from src.blamera_pc.gallery import GalleryError


def bundle(entries):
    target = BytesIO()
    with warnings.catch_warnings(), zipfile.ZipFile(target, "w") as archive:
        warnings.simplefilter("ignore", UserWarning)
        for name, content in entries:
            archive.writestr(name, content)
    return target.getvalue()


class ArchiveTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.model = b"SYNTHETIC weights"
        self.archive = bundle([("weights.onnx", self.model), ("../outside.txt", b"ignored")])
        self.file = self.model_file(self.archive)
        self.configuration = FaceConfiguration(
            FaceProfile("synthetic-r50", 512), (self.file,), 640, 0.8, 0.3
        )

    def model_file(self, content):
        return ModelFile(
            "encoder",
            "weights.onnx",
            len(self.model),
            hashlib.sha256(self.model).hexdigest(),
            "https://example.invalid/archive.zip",
            ModelArchive(
                "archive.zip", len(content), hashlib.sha256(content).hexdigest(), "weights.onnx"
            ),
        )

    def test_download_checks_archive_and_member_without_extracting_other_paths(self):
        with patch("urllib.request.urlopen", return_value=BytesIO(self.archive)) as network:
            provision(self.configuration, self.root)
        network.assert_called_once()
        self.assertEqual((self.root / "weights.onnx").read_bytes(), self.model)
        self.assertEqual({p.name for p in self.root.iterdir()}, {"weights.onnx", "archive.zip"})
        (self.root / "weights.onnx").unlink()
        with patch("urllib.request.urlopen", side_effect=AssertionError("offline")):
            provision(self.configuration, self.root)
        self.assertEqual((self.root / "weights.onnx").read_bytes(), self.model)

    def test_corrupt_archive_download_preserves_previous_weights(self):
        (self.root / "weights.onnx").write_bytes(b"OLD")
        with patch("urllib.request.urlopen", return_value=BytesIO(b"bad")):
            with self.assertRaises(GalleryError):
                provision(self.configuration, self.root)
        self.assertEqual((self.root / "weights.onnx").read_bytes(), b"OLD")
        self.assertFalse((self.root / "archive.zip").exists())
        self.assertFalse(list(self.root.glob("*.part")))

    def test_bad_zip_missing_duplicate_wrong_size_and_wrong_content_are_not_activated(self):
        variants = [
            b"not a zip",
            bundle([("other.onnx", self.model)]),
            bundle([("weights.onnx", self.model), ("weights.onnx", self.model)]),
            bundle([("weights.onnx", b"short")]),
            bundle([("weights.onnx", b"x" * len(self.model))]),
        ]
        for content in variants:
            with self.subTest(archive_size=len(content)):
                (self.root / "archive.zip").write_bytes(content)
                (self.root / "weights.onnx").write_bytes(b"OLD")
                configuration = replace(self.configuration, files=(self.model_file(content),))
                with patch("urllib.request.urlopen", side_effect=AssertionError("offline")):
                    with self.assertRaises(GalleryError):
                        provision(configuration, self.root)
                self.assertEqual((self.root / "weights.onnx").read_bytes(), b"OLD")
                self.assertFalse(list(self.root.glob("*.part")))

    def test_manifest_refuses_bad_archive_metadata_and_archive_on_non_encoder(self):
        original = json.loads(MANIFEST.read_text(encoding="utf-8"))
        encoder_index = next(
            i for i, file in enumerate(original["files"]) if file["role"] == "encoder"
        )
        original_archive = original["files"][encoder_index]["archive"]
        variants = [
            False,
            {},
            {**original_archive, "name": "../escape.zip"},
            {**original_archive, "member": "../escape.onnx"},
            {**original_archive, "size": True},
            {**original_archive, "size": 0},
            {**original_archive, "size": 600 * 1024 * 1024},
            {**original_archive, "sha256": "wrong"},
            {**original_archive, "member": None},
        ]
        path = self.root / "bad.json"
        for archive in variants:
            data = json.loads(json.dumps(original))
            data["files"][encoder_index]["archive"] = archive
            path.write_text(json.dumps(data), encoding="utf-8")
            with self.subTest(archive=archive), self.assertRaises(GalleryError):
                load_configuration(path)
        original["files"][0]["archive"] = original_archive
        path.write_text(json.dumps(original), encoding="utf-8")
        with self.assertRaises(GalleryError):
            load_configuration(path)
