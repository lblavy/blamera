"""Invariants du writer partagé, indépendants des photos et du stockage."""

import copy
import json
import unittest
from unittest.mock import patch

from src.blamera_pc.evaluation_data import Policy
from src.blamera_pc.face_gallery_package import MAX_PACKAGE_BYTES, encode_package
from src.blamera_pc.face_models import load_configuration
from src.blamera_pc.gallery import GalleryError
from test_face_gallery_export import FIXTURE, NOW


class FaceGalleryPackageTests(unittest.TestCase):
    def setUp(self):
        self.fixture = json.loads(FIXTURE.read_text(encoding="utf-8"))
        self.configuration = load_configuration()

    def encode(self, data=None):
        data = data or self.fixture
        return encode_package(
            self.configuration,
            Policy(**data["policy"]),
            data["identities"],
            data["qualification"],
            NOW,
        )

    def test_public_fixture_semantics_match_pc_writer_and_unit_vectors_are_not_modified(self):
        payload = self.encode()
        data = json.loads(payload)
        self.assertEqual(data, {**self.fixture, "exported_utc": "2026-11-01T12:34:56Z"})
        self.assertFalse(payload.startswith(b"\xef\xbb\xbf"))
        self.assertLessEqual(len(payload), MAX_PACKAGE_BYTES)

    def test_vectors_require_exact_dimension_finite_real_values_and_unit_norm(self):
        for vector in (
            [0.0] * 512,
            [1.0] + [0.0] * 510,
            [float("nan")] + [0.0] * 511,
            [float("inf")] + [0.0] * 511,
            [True] + [0.0] * 511,
            [1.002] + [0.0] * 511,
        ):
            data = copy.deepcopy(self.fixture)
            data["identities"][0]["prototype"] = vector
            with self.subTest(vector=vector[:2]), self.assertRaises(GalleryError):
                self.encode(data)
        data = copy.deepcopy(self.fixture)
        data["identities"][0]["prototype"][0] = 1.0009
        self.assertEqual(json.loads(self.encode(data))["identities"][0]["prototype"][0], 1.0009)

    def test_identity_names_ids_counts_duplicates_and_unknown_fields_are_refused(self):
        for key, value in (
            ("id", "UPPER"),
            ("id", "f" * 33),
            ("name", ""),
            ("name", " leading"),
            ("name", "a\u200bb"),
            ("name", "a" * 81),
            ("reference_count", True),
            ("reference_count", 1.0),
            ("reference_count", 0),
            ("reference_count", 10001),
            ("extra", "unwanted"),
        ):
            data = copy.deepcopy(self.fixture)
            data["identities"][0][key] = value
            with self.subTest(key=key, value=value), self.assertRaises(GalleryError):
                self.encode(data)
        for field in ("id", "name"):
            data = copy.deepcopy(self.fixture)
            data["identities"][1][field] = data["identities"][0][field]
            with self.subTest(field=field), self.assertRaises(GalleryError):
                self.encode(data)
        for entries in ([], [self.fixture["identities"][0]] * 11):
            with self.subTest(count=len(entries)), self.assertRaises(GalleryError):
                self.encode({**self.fixture, "identities": entries})

    def test_qualification_requires_bounded_consistent_integer_counts_and_known_status(self):
        for key, value in (
            ("status", "validated"),
            ("scope", "all-people"),
            ("frozen_context", "G" * 64),
            ("test_report", "f" * 63),
            ("extra", True),
        ):
            data = copy.deepcopy(self.fixture)
            data["qualification"][key] = value
            with self.subTest(key=key), self.assertRaises(GalleryError):
                self.encode(data)
        for key, value in (
            ("total", True),
            ("total", 15.0),
            ("total", 100001),
            ("total", 0),
            ("usable", 12),
            ("unusable", 3),
            ("false_names_known", 1),
            ("correct_names", 9),
            ("uncertain", -1),
            ("extra", 1),
        ):
            data = copy.deepcopy(self.fixture)
            data["qualification"]["counts"][key] = value
            with self.subTest(key=key, value=value), self.assertRaises(GalleryError):
                self.encode(data)
        data = copy.deepcopy(self.fixture)
        data["qualification"]["counts"] = None
        with self.assertRaises(GalleryError):
            self.encode(data)

    def test_size_is_utf8_bytes_and_oversize_valid_content_is_refused(self):
        data = copy.deepcopy(self.fixture)
        data["identities"] = [
            {
                "id": f"{index + 1:032x}",
                "name": "😀" * 79 + str(index),
                "reference_count": 1,
                "prototype": [1.0] + [-1.2345678901234567e-100] * 511,
            }
            for index in range(10)
        ]
        with self.assertRaisesRegex(GalleryError, "120 Kio"):
            self.encode(data)
        payload = self.encode()
        with patch("src.blamera_pc.face_gallery_package.MAX_PACKAGE_BYTES", len(payload)):
            self.assertEqual(self.encode(), payload)
        with patch("src.blamera_pc.face_gallery_package.MAX_PACKAGE_BYTES", len(payload) - 1):
            with self.assertRaises(GalleryError):
                self.encode()
