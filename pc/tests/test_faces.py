"""Références biométriques uniquement sur images et vecteurs synthétiques."""

import sqlite3
import struct
import tempfile
import unittest
from contextlib import closing
from pathlib import Path
from unittest.mock import patch

from src.blamera_pc.face_data import (
    FaceCandidate,
    FaceProfile,
    FaceSample,
    normalize_vector,
    validate_candidates,
    validate_crop,
)
from src.blamera_pc.face_store import FaceReferences
from src.blamera_pc.gallery import GalleryError
from src.blamera_pc.gallery_store import GalleryStore
from src.blamera_pc.image_import import normalize_image
from test_gallery import image_bytes

PROFILE = FaceProfile("SYNTHETIC-profile-sha")
CANDIDATE = FaceCandidate(
    (1.0, 1.0, 8.0, 6.0, 3.0, 2.0, 7.0, 2.0, 5.0, 4.0, 3.0, 6.0, 7.0, 6.0, 0.9)
)
SAMPLE = FaceSample(image_bytes(size=(112, 112)), tuple([1.0] + [0.0] * 127))


def save_detections(store, photo, profile, candidates):
    store.detections(photo, profile, candidates, expected_generation=store.generation(photo))


def save_reference(store, photo, profile, index, sample):
    state = store.read(photo, profile)
    store.confirm(photo, profile, index, sample, generation=state.generation if state else "")


def save_selection(store, photo, profile, index):
    state = store.read(photo, profile)
    store.select(photo, profile, index, generation=state.generation if state else "")


class FaceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.gallery = GalleryStore(self.root)
        self.alice = self.gallery.create("Alice")
        self.bob = self.gallery.create("Bob")
        self.gallery.add_photo(self.alice, normalize_image(image_bytes()))
        self.photo = self.gallery.photos(self.alice)[0]
        self.faces = FaceReferences(self.gallery)

    def test_zero_and_multiple_candidates_need_explicit_selection_then_confirmation(self):
        save_detections(self.faces, self.photo, PROFILE, ())
        self.assertEqual(self.faces.read(self.photo, PROFILE).candidates, ())
        save_detections(self.faces, self.photo, PROFILE, (CANDIDATE, CANDIDATE))
        state = self.faces.read(self.photo, PROFILE)
        self.assertIsNone(state.selected)
        self.assertIsNone(state.reference)
        save_selection(self.faces, self.photo, PROFILE, 1)
        self.assertIsNone(self.faces.read(self.photo, PROFILE).reference)
        save_reference(self.faces, self.photo, PROFILE, 1, SAMPLE)
        reopened = FaceReferences(GalleryStore(self.root)).read(self.photo, PROFILE)
        self.assertEqual(reopened.selected, 1)
        self.assertEqual(reopened.reference.candidate, 1)
        self.assertEqual(reopened.reference.sample.vector, SAMPLE.vector)

    def test_moving_invalidates_and_late_reply_is_refused_even_after_move_back(self):
        save_detections(self.faces, self.photo, PROFILE, (CANDIDATE,))
        save_reference(self.faces, self.photo, PROFILE, 0, SAMPLE)
        self.gallery.move_photo(self.photo.id, self.bob)
        moved = self.gallery.photos(self.bob)[0]
        self.assertIsNone(self.faces.read(moved, PROFILE))
        with self.assertRaises(GalleryError):
            save_detections(self.faces, self.photo, PROFILE, (CANDIDATE,))
        self.gallery.move_photo(self.photo.id, self.alice)
        with self.assertRaises(GalleryError):
            save_reference(self.faces, self.photo, PROFILE, 0, SAMPLE)

    def test_rename_preserves_reference_and_deletion_cascades(self):
        save_detections(self.faces, self.photo, PROFILE, (CANDIDATE,))
        save_reference(self.faces, self.photo, PROFILE, 0, SAMPLE)
        self.gallery.rename(self.alice, "Alicia")
        self.assertIsNotNone(self.faces.read(self.photo, PROFILE).reference)
        self.gallery.delete_person(self.alice)
        with closing(sqlite3.connect(self.gallery.path)) as connection:
            self.assertEqual(
                connection.execute("SELECT COUNT(*) FROM face_references").fetchone()[0], 0
            )
        with self.assertRaises(GalleryError):
            save_detections(self.faces, self.photo, PROFILE, (CANDIDATE,))

    def test_profile_mismatch_and_invalid_vectors_never_replace_a_valid_reference(self):
        save_detections(self.faces, self.photo, PROFILE, (CANDIDATE,))
        save_reference(self.faces, self.photo, PROFILE, 0, SAMPLE)
        self.assertIsNone(self.faces.read(self.photo, FaceProfile("other-model-sha")))
        for vector in [
            (),
            (0.0,) * 128,
            (float("nan"),) * 128,
            (float("inf"),) * 128,
            (1.0,) * 127,
        ]:
            with self.subTest(size=len(vector)), self.assertRaises(GalleryError):
                save_reference(self.faces, self.photo, PROFILE, 0, FaceSample(SAMPLE.crop, vector))
        self.assertIsNotNone(self.faces.read(self.photo, PROFILE).reference)

    def test_bad_candidates_or_choice_and_corrupt_read_are_rejected(self):
        with self.assertRaises(GalleryError):
            save_detections(self.faces, self.photo, PROFILE, (FaceCandidate((1.0,) * 15),))
        save_detections(self.faces, self.photo, PROFILE, (CANDIDATE,))
        for index in [-1, 1]:
            with self.assertRaises(GalleryError):
                save_selection(self.faces, self.photo, PROFILE, index)
        save_reference(self.faces, self.photo, PROFILE, 0, SAMPLE)
        with closing(sqlite3.connect(self.gallery.path)) as connection, connection:
            connection.execute("UPDATE face_references SET vector=?", (b"broken",))
        with self.assertRaises(GalleryError):
            self.faces.read(self.photo, PROFILE)

    def test_vector_normalization_is_l2_and_dimension_checked(self):
        self.assertAlmostEqual(sum(v * v for v in normalize_vector((3.0, 4.0), 2)), 1.0)

    def test_candidate_and_crop_contracts_reject_invalid_geometry_and_content(self):
        with self.assertRaises(GalleryError):
            self.faces.confirm(self.photo, PROFILE, 0, SAMPLE, generation="")
        variants = [
            FaceCandidate((1.0,) * 14),
            FaceCandidate((float("nan"),) * 15),
            FaceCandidate((-1.0, *CANDIDATE.values[1:])),
            FaceCandidate((*CANDIDATE.values[:4], 99.0, *CANDIDATE.values[5:])),
        ]
        for candidate in variants:
            with self.subTest(candidate=candidate), self.assertRaises(GalleryError):
                validate_candidates(self.photo, (candidate,))
        with self.assertRaises(GalleryError):
            validate_candidates(self.photo, (CANDIDATE,) * 21)
        for content in [b"broken", b"x" * (1024 * 1024 + 1), image_bytes(size=(10, 10))]:
            with self.subTest(size=len(content)), self.assertRaises(GalleryError):
                validate_crop(content)

    def test_corrupt_candidate_and_reference_metadata_cannot_be_reused(self):
        mutations = [
            ("face_detections", "candidates", "null"),
            ("face_detections", "candidates", "["),
            ("face_detections", "candidates", "x" * 20001),
            ("face_detections", "generation", "broken"),
            ("face_detections", "selected", 99),
            ("face_references", "profile", "other"),
            ("face_references", "dimension", 512),
            ("face_references", "crop", b"broken"),
            ("face_references", "vector", struct.pack("<128f", *([1.0] * 128))),
        ]
        for table, column, value in mutations:
            save_detections(self.faces, self.photo, PROFILE, (CANDIDATE,))
            save_reference(self.faces, self.photo, PROFILE, 0, SAMPLE)
            with closing(sqlite3.connect(self.gallery.path)) as connection, connection:
                connection.execute(f"UPDATE {table} SET {column}=?", (value,))
            with self.subTest(table=table, column=column), self.assertRaises(GalleryError):
                self.faces.read(self.photo, PROFILE)


class MigrationTests(unittest.TestCase):
    def make_v1(self, directory):
        path = Path(directory) / "gallery.sqlite3"
        with closing(sqlite3.connect(path)) as connection, connection:
            connection.execute(
                "CREATE TABLE people(id TEXT PRIMARY KEY,name TEXT,name_key TEXT UNIQUE)"
            )
            connection.execute(
                "CREATE TABLE photos(id TEXT PRIMARY KEY,person_id TEXT REFERENCES people(id) ON DELETE CASCADE,image BLOB,digest TEXT,width INTEGER,height INTEGER,UNIQUE(person_id,digest))"
            )
            connection.execute("INSERT INTO people VALUES ('owner','SYNTHETIC','synthetic')")
            connection.execute(
                "INSERT INTO photos VALUES ('photo','owner',?,'hash',12,8)", (image_bytes(),)
            )
            connection.execute("PRAGMA user_version=1")
        return path

    def test_v1_data_survives_transactional_upgrade(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.make_v1(directory)
            store = GalleryStore(Path(directory))
            self.assertEqual(store.people()[0].id, "owner")
            self.assertEqual(store.photos("owner")[0].image, image_bytes())
            with closing(sqlite3.connect(path)) as connection:
                self.assertEqual(connection.execute("PRAGMA user_version").fetchone()[0], 2)

    def test_failed_upgrade_rolls_back_columns_and_version(self):
        original_connect = sqlite3.connect

        class BrokenMigration(sqlite3.Connection):
            def execute(self, sql, parameters=()):
                if sql.startswith("CREATE TABLE face_references"):
                    raise sqlite3.OperationalError("Synthetic failure")
                return super().execute(sql, parameters)

        with tempfile.TemporaryDirectory() as directory:
            path = self.make_v1(directory)
            with patch(
                "src.blamera_pc.gallery_store.sqlite3.connect",
                side_effect=lambda *a, **kw: original_connect(*a, **kw, factory=BrokenMigration),
            ):
                with self.assertRaises(GalleryError):
                    GalleryStore(Path(directory))
            with closing(sqlite3.connect(path)) as connection:
                self.assertEqual(connection.execute("PRAGMA user_version").fetchone()[0], 1)
                self.assertEqual(len(connection.execute("PRAGMA table_info(photos)").fetchall()), 6)
