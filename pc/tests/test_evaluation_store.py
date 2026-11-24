"""Corpus privé isolé et séances : invariants sur bases synthétiques."""

import tempfile
import unittest
from dataclasses import replace
from pathlib import Path

from src.blamera_pc.evaluation_data import Policy
from src.blamera_pc.evaluation_store import EvaluationStore
from src.blamera_pc.gallery import GalleryError, Photo
from src.blamera_pc.gallery_store import GalleryStore
from src.blamera_pc.image_import import normalize_image
from test_gallery import image_bytes


class EvaluationStoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.main = GalleryStore(self.root)
        self.person = self.main.create("Personne synthétique")
        self.image = normalize_image(image_bytes())
        self.store = EvaluationStore(self.root)

    def add(self, **kwargs):
        values = dict(
            expected=self.person,
            split="calibration",
            session="Jour 2",
            forbidden_digests=set(),
            reference_sessions=set(),
        )
        self.store.add_case(self.image, **{**values, **kwargs})

    def test_import_is_separate_persistent_and_duplicate_across_splits_is_refused(self):
        self.add()
        self.assertEqual(self.main.photos(self.person), [])
        case = EvaluationStore(self.root).cases()[0]
        self.assertEqual(case.expected, self.person)
        self.assertEqual(case.session, "Jour 2")
        with self.assertRaises(GalleryError):
            self.add(expected=None, split="test", session="Jour 3")
        self.assertEqual(len(self.store.cases()), 1)

    def test_reference_duplicate_and_overlapping_or_empty_session_are_refused(self):
        for values in (
            dict(forbidden_digests={self.image.digest}),
            dict(reference_sessions={"jour 2"}),
            dict(session="  "),
            dict(session="\nBAD"),
            dict(split="bogus"),
        ):
            with self.subTest(values=values), self.assertRaises(GalleryError):
                self.add(**values)
        self.assertEqual(self.store.cases(), [])

    def test_edit_delete_and_sessions_preserve_atomic_cases(self):
        self.add()
        case = self.store.cases()[0]
        self.store.update_case(case.photo.id, None, "test", "Jour 3", set())
        edited = self.store.cases()[0]
        self.assertIsNone(edited.expected)
        self.assertEqual(edited.split, "test")
        with self.assertRaises(GalleryError):
            self.store.update_case(case.photo.id, self.person, "calibration", "REF", {"ref"})
        self.assertEqual(self.store.cases()[0], edited)
        self.store.delete_photo(case.photo.id)
        self.assertEqual(self.store.cases(), [])

    def test_reference_annotations_bound_to_source_owner_and_revision(self):
        photo = Photo("ref", self.person, self.image.image, self.image.width, self.image.height)
        self.store.assign_sessions([photo], "  ÉTÉ   2025 ")
        self.assertEqual(self.store.reference_session(photo), "ÉTÉ 2025")
        self.assertEqual(self.store.reference_session(replace(photo, revision=1)), "")
        self.assertEqual(self.store.reference_session(replace(photo, person_id="other")), "")
        self.assertEqual(self.store.reference_session(replace(photo, image=b"changed")), "")
        with self.assertRaises(GalleryError):
            self.add(session="été 2025", reference_sessions={"été 2025"})

    def test_same_session_cannot_cross_splits_or_be_assigned_to_reference(self):
        self.add()
        other = normalize_image(image_bytes(color="blue"))
        with self.assertRaises(GalleryError):
            self.store.add_case(other, None, "test", " JOUR  2 ", set(), set())
        photo = Photo("ref", self.person, self.image.image, self.image.width, self.image.height)
        with self.assertRaises(GalleryError):
            self.store.assign_sessions([photo], "jour 2")

    def test_report_requires_same_snapshot_and_survives_reopen(self):
        self.store.save_report("snapshot", {"rows": [], "policy": [0.3, 0.6, 0.05]})
        self.assertEqual(EvaluationStore(self.root).report("snapshot")["rows"], [])
        self.assertIsNone(self.store.report("other"))

    def test_policy_is_explicit_validated_and_persisted(self):
        self.assertEqual(self.store.policy(), Policy())
        self.store.set_policy(Policy(0.2, 0.7, 0.1))
        self.assertEqual(EvaluationStore(self.root).policy(), Policy(0.2, 0.7, 0.1))
        with self.assertRaises(GalleryError):
            self.store.set_policy(Policy(0.8, 0.4))
        self.assertEqual(self.store.policy().accept, 0.7)

    def test_failed_annotation_insert_rolls_back_photo_and_empty_sessions_fail(self):
        with self.store._connection() as connection:
            connection.execute(
                "CREATE TRIGGER fail_case BEFORE INSERT ON cases BEGIN SELECT RAISE(ABORT, 'SYNTHETIC'); END"
            )
        with self.assertRaises(GalleryError):
            self.add()
        self.assertEqual(self.store.photos("evaluation"), [])
        with self.assertRaises(GalleryError):
            self.store.assign_sessions([], "Valid")
        with self.assertRaises(GalleryError):
            self.store.update_case("missing", None, "calibration", "Valid", set())
        for session in ("x" * 81, "\x00test"):
            with self.assertRaises(GalleryError):
                self.add(session=session)

    def test_corrupted_policy_report_and_unknown_schema_are_explicit_errors(self):
        with self.store._connection() as connection:
            connection.execute("INSERT INTO settings VALUES ('policy', '{}')")
        with self.assertRaises(GalleryError):
            self.store.policy()
        for payload in ("broken", "[]", '{"schema":2}'):
            with self.store._connection() as connection:
                connection.execute(
                    "INSERT OR REPLACE INTO reports VALUES (1,'snapshot',?)", (payload,)
                )
            with self.assertRaises(GalleryError):
                self.store.report("snapshot")
        with self.store._connection() as connection:
            connection.execute("UPDATE eval_meta SET version=99")
        with self.assertRaises(GalleryError):
            EvaluationStore(self.root)
