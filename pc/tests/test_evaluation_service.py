"""Rapports complets, test réservé et invalidation sur corpus synthétiques."""

import tempfile
import unittest
from pathlib import Path

from src.blamera_pc.evaluation_data import Policy
from src.blamera_pc.evaluation_service import current_report, run_evaluation
from src.blamera_pc.evaluation_store import EvaluationStore
from src.blamera_pc.face_data import FaceProfile, FaceSample
from src.blamera_pc.face_store import FaceReferences
from src.blamera_pc.gallery import GalleryError
from src.blamera_pc.gallery_store import GalleryStore
from src.blamera_pc.image_import import normalize_image
from test_faces import CANDIDATE, SAMPLE, save_detections, save_reference
from test_gallery import image_bytes

PROFILE = FaceProfile("synthetic-evaluation", 2)


def confirmed(gallery, photo, vector=(1.0, 0.0)):
    faces = FaceReferences(gallery)
    save_detections(faces, photo, PROFILE, (CANDIDATE,))
    save_reference(faces, photo, PROFILE, 0, FaceSample(SAMPLE.crop, vector))


class EvaluationServiceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.gallery = GalleryStore(self.root)
        self.person = self.gallery.create("Alice synthétique")
        self.gallery.add_photo(self.person, normalize_image(image_bytes("red")))
        self.reference = self.gallery.photos(self.person)[0]
        confirmed(self.gallery, self.reference)
        self.evaluation = EvaluationStore(self.root)
        self.evaluation.assign_sessions([self.reference], "Référence 2024")

    def probe(
        self,
        color="blue",
        expected="self",
        vector=(1.0, 0.0),
        split="calibration",
        session="Nouveau jour",
    ):
        expected = self.person if expected == "self" else expected
        self.evaluation.add_case(
            normalize_image(image_bytes(color)), expected, split, session, set(), set()
        )
        photo = self.evaluation.cases()[-1].photo
        if vector is not None:
            confirmed(self.evaluation, photo, vector)
        return photo

    def test_complete_report_counts_known_unknown_uncertain_and_unusable(self):
        self.probe()
        self.probe("green", None, (0.0, 1.0))
        self.probe("black", None, (1.0, 0.0))
        self.probe("white", "self", (-1.0, 0.0))
        self.probe("yellow", "self", (0.4, 0.916515))
        empty = self.probe("cyan", "self", None)
        save_detections(FaceReferences(self.evaluation), empty, PROFILE, ())
        self.probe("pink", "self", None, "test", "Test réservé")
        report = run_evaluation(self.gallery, self.evaluation, PROFILE)
        self.assertEqual(
            report["counts"],
            {
                "total": 6,
                "usable": 5,
                "unusable": 1,
                "correct_names": 1,
                "false_names": 1,
                "false_names_known": 0,
                "false_names_unknown": 1,
                "correct_unknown": 1,
                "false_rejections": 1,
                "uncertain": 1,
            },
        )
        self.assertEqual(len(report["rows"]), 6)
        self.assertEqual(report["reserved_count"], 1)
        self.assertEqual(current_report(self.gallery, EvaluationStore(self.root), PROFILE), report)

    def test_unprepared_or_unconfirmed_cases_block_complete_report(self):
        photo = self.probe(vector=None)
        with self.assertRaises(GalleryError):
            run_evaluation(self.gallery, self.evaluation, PROFILE)
        save_detections(FaceReferences(self.evaluation), photo, PROFILE, (CANDIDATE,))
        with self.assertRaises(GalleryError):
            run_evaluation(self.gallery, self.evaluation, PROFILE)

    def test_report_invalidated_by_policy_annotations_names_or_reference_changes(self):
        self.probe()
        run_evaluation(self.gallery, self.evaluation, PROFILE)
        self.evaluation.set_policy(Policy(0.2, 0.7, 0.1))
        self.assertIsNone(current_report(self.gallery, self.evaluation, PROFILE))
        run_evaluation(self.gallery, self.evaluation, PROFILE)
        self.gallery.rename(self.person, "Nouveau nom")
        self.assertIsNone(current_report(self.gallery, self.evaluation, PROFILE))
        run_evaluation(self.gallery, self.evaluation, PROFILE)
        self.evaluation.assign_sessions([self.reference], "Autre séance de référence")
        self.assertIsNone(current_report(self.gallery, self.evaluation, PROFILE))
        run_evaluation(self.gallery, self.evaluation, PROFILE)
        confirmed(self.gallery, self.reference, (0.0, 1.0))
        self.assertIsNone(current_report(self.gallery, self.evaluation, PROFILE))

    def test_missing_sessions_reference_identity_and_new_reference_duplicate_block(self):
        self.probe()
        with self.evaluation._connection() as connection:
            connection.execute("DELETE FROM reference_sessions")
        with self.assertRaises(GalleryError):
            run_evaluation(self.gallery, self.evaluation, PROFILE)
        self.evaluation.assign_sessions([self.reference], "Référence")
        self.gallery.add_photo(self.person, normalize_image(image_bytes("blue")))
        with self.assertRaises(GalleryError):
            run_evaluation(self.gallery, self.evaluation, PROFILE)

    def test_missing_known_identity_is_not_silently_treated_as_unknown(self):
        self.probe(expected="deleted-id")
        with self.assertRaises(GalleryError):
            run_evaluation(self.gallery, self.evaluation, PROFILE)

    def test_no_references_no_calibration_and_wrong_profile_do_not_produce_report(self):
        with self.assertRaises(GalleryError):
            run_evaluation(self.gallery, self.evaluation, PROFILE)
        self.probe()
        with self.assertRaises(GalleryError):
            run_evaluation(self.gallery, self.evaluation, FaceProfile("other", 2))
        self.gallery.delete_photo(self.reference.id)
        with self.assertRaises(GalleryError):
            run_evaluation(self.gallery, self.evaluation, PROFILE)

    def test_current_report_rejects_partial_corrupt_payload_instead_of_crashing_ui(self):
        self.probe()
        report = run_evaluation(self.gallery, self.evaluation, PROFILE)
        for payload in (
            {"schema": 1, "counts": {}, "rows": []},
            {**report, "per_person": None},
            {**report, "counts": None},
        ):
            self.evaluation.save_report(report["fingerprint"], payload)
            with self.subTest(payload=payload), self.assertRaises(GalleryError):
                current_report(self.gallery, self.evaluation, PROFILE)
