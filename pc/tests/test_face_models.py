"""Provisionnement public vérifié, réseau simulé et dossiers temporaires."""

import hashlib
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from io import BytesIO, StringIO
from pathlib import Path
from unittest.mock import patch

from src.blamera_pc.face_data import FaceProfile
from src.blamera_pc.face_models import (
    FaceConfiguration,
    ModelFile,
    load_configuration,
    main,
    provision,
    verify_models,
)
from src.blamera_pc.gallery import GalleryError


class ModelTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.content = b"SYNTHETIC model"
        self.file = ModelFile(
            "detector",
            "test.onnx",
            len(self.content),
            hashlib.sha256(self.content).hexdigest(),
            "https://example.invalid/synthetic",
        )
        self.config = FaceConfiguration(FaceProfile("SYNTHETIC"), (self.file,), 640, 0.8, 0.3)

    def test_pinned_manifest_loads_exact_profile_and_changes_are_incompatible(self):
        actual = load_configuration()
        self.assertEqual(actual.profile.dimension, 512)
        self.assertEqual(
            {file.role for file in actual.files},
            {
                "detector",
                "aligner",
                "encoder",
                "detector_license",
                "aligner_license",
                "encoder_terms",
                "encoder_code_license",
            },
        )
        self.assertEqual(len(actual.profile.key), 64)
        manifest = json.loads(
            (Path(__file__).resolve().parents[2] / "config/face-models.json").read_text()
        )
        manifest["score"] = 0.9
        path = self.root / "manifest.json"
        path.write_text(json.dumps(manifest))
        self.assertNotEqual(actual.profile.key, load_configuration(path).profile.key)
        manifest["files"][0]["name"] = "../escape.onnx"
        path.write_text(json.dumps(manifest))
        with self.assertRaises(GalleryError):
            load_configuration(path)

    def test_existing_valid_model_needs_no_download(self):
        (self.root / self.file.name).write_bytes(self.content)
        verify_models(self.config, self.root)
        with patch("urllib.request.urlopen", side_effect=AssertionError("No network")):
            provision(self.config, self.root)

    def test_missing_or_changed_model_is_refused(self):
        with self.assertRaises(GalleryError):
            verify_models(self.config, self.root)
        (self.root / self.file.name).write_bytes(b"wrong")
        with self.assertRaises(GalleryError):
            verify_models(self.config, self.root)

    def test_bad_download_never_replaces_existing_file(self):
        destination = self.root / self.file.name
        destination.write_bytes(b"OLD SYNTHETIC")
        with patch("urllib.request.urlopen", return_value=BytesIO(b"wrong")):
            with self.assertRaises(GalleryError):
                provision(self.config, self.root)
        self.assertEqual(destination.read_bytes(), b"OLD SYNTHETIC")
        with patch("urllib.request.urlopen", return_value=BytesIO(self.content)):
            provision(self.config, self.root)
        self.assertEqual(destination.read_bytes(), self.content)
        self.assertEqual(list(self.root.glob("*.part")), [])

    def test_corrupt_manifest_and_wrong_dimension_are_errors(self):
        path = self.root / "bad.json"
        path.write_text("invalid")
        with self.assertRaises(GalleryError):
            load_configuration(path)
        with self.assertRaises(GalleryError):
            load_configuration(self.root / "missing.json")

    def test_manifest_rejects_incompatible_contracts_before_loading_weights(self):
        original = json.loads(
            (Path(__file__).resolve().parents[2] / "config/face-models.json").read_text()
        )
        updates = [
            {"schema": 2},
            {"dimension": 128},
            {"preprocessing": "ArcFace"},
            {"files": None},
            {"files": []},
            {"files": [None]},
            {"detector_size": True},
            {"score": 0},
            {"nms": "bad"},
        ]
        path = self.root / "bad.json"
        for update in updates:
            with self.subTest(update=update):
                path.write_text(json.dumps({**original, **update}))
                with self.assertRaises(GalleryError):
                    load_configuration(path)
        for update in [
            {"size": True},
            {"sha256": "bad"},
            {"name": None},
            {"url": "https://example.invalid/not-pinned"},
        ]:
            with self.subTest(file=update):
                data = {
                    **original,
                    "files": [{**original["files"][0], **update}, *original["files"][1:]],
                }
                path.write_text(json.dumps(data))
                with self.assertRaises(GalleryError):
                    load_configuration(path)

    def test_excess_download_and_io_failure_preserve_existing_model(self):
        destination = self.root / self.file.name
        destination.write_bytes(b"OLD")
        with patch("urllib.request.urlopen", return_value=BytesIO(self.content + b"extra")):
            with self.assertRaises(GalleryError):
                provision(self.config, self.root)
        self.assertEqual(destination.read_bytes(), b"OLD")
        with patch("tempfile.NamedTemporaryFile", side_effect=OSError("Synthetic disk error")):
            with self.assertRaises(GalleryError):
                provision(self.config, self.root)
        self.assertEqual(destination.read_bytes(), b"OLD")

    def test_command_requires_explicit_flag_and_reports_success_or_safe_error(self):
        with patch.object(sys, "argv", ["models"]), patch("sys.stderr", new=StringIO()):
            with self.assertRaises(SystemExit):
                main()
        with (
            patch.object(sys, "argv", ["models", "--download"]),
            patch("src.blamera_pc.face_models.provision") as download,
            redirect_stdout(StringIO()),
        ):
            self.assertEqual(main(), 0)
            download.assert_called_once()
        with (
            patch.object(sys, "argv", ["models", "--download"]),
            patch(
                "src.blamera_pc.face_models.provision", side_effect=GalleryError("Synthetic error")
            ),
            redirect_stdout(StringIO()),
        ):
            self.assertEqual(main(), 1)
