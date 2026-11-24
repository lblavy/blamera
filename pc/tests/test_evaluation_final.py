"""Un test réservé ne peut pas devenir un réglage déguisé."""

import json
import unittest

import test_evaluation_service as fixtures
from src.blamera_pc.evaluation_data import Policy
from src.blamera_pc.evaluation_final import (
    current_final_report,
    current_freeze,
    freeze_final_test,
    run_final_test,
)
from src.blamera_pc.evaluation_service import current_report, run_evaluation
from src.blamera_pc.evaluation_store import EvaluationStore
from src.blamera_pc.face_data import FaceProfile
from src.blamera_pc.face_store import FaceReferences
from src.blamera_pc.gallery import GalleryError
from test_evaluation_service import PROFILE, confirmed
from test_faces import CANDIDATE, save_detections


class FinalEvaluationTests(unittest.TestCase):
    setUp = fixtures.EvaluationServiceTests.setUp
    probe = fixtures.EvaluationServiceTests.probe

    def prepare(self, vector=None):
        self.probe()
        photo = self.probe("pink", "self", vector, "test", "Test réservé")
        calibration = run_evaluation(self.gallery, self.evaluation, PROFILE)
        return photo, calibration

    def test_freeze_requires_current_calibration_and_a_reserved_lot(self):
        with self.assertRaises(GalleryError):
            freeze_final_test(self.gallery, self.evaluation, PROFILE)
        self.probe()
        run_evaluation(self.gallery, self.evaluation, PROFILE)
        with self.assertRaises(GalleryError):
            freeze_final_test(self.gallery, self.evaluation, PROFILE)
        self.probe("pink", "self", None, "test", "Test réservé")
        with self.assertRaises(GalleryError):
            freeze_final_test(self.gallery, self.evaluation, PROFILE)
        run_evaluation(self.gallery, self.evaluation, PROFILE)
        frozen = freeze_final_test(self.gallery, self.evaluation, PROFILE)
        self.assertEqual(frozen["policy"], {"reject": 0.3, "accept": 0.6, "margin": 0.05})
        self.assertEqual(current_freeze(self.gallery, EvaluationStore(self.root), PROFILE), frozen)

    def test_final_requires_freeze_then_human_confirmation_and_preserves_calibration(self):
        photo, calibration = self.prepare()
        before = self.gallery.path.read_bytes()
        with self.assertRaises(GalleryError):
            run_final_test(self.gallery, self.evaluation, PROFILE)
        frozen = freeze_final_test(self.gallery, self.evaluation, PROFILE)
        with self.assertRaises(GalleryError):
            run_final_test(self.gallery, self.evaluation, PROFILE)
        save_detections(FaceReferences(self.evaluation), photo, PROFILE, (CANDIDATE,))
        with self.assertRaises(GalleryError):
            run_final_test(self.gallery, self.evaluation, PROFILE)
        confirmed(self.evaluation, photo)
        self.assertEqual(current_freeze(self.gallery, self.evaluation, PROFILE), frozen)
        self.assertEqual(current_report(self.gallery, self.evaluation, PROFILE), calibration)
        final = run_final_test(self.gallery, self.evaluation, PROFILE)
        self.assertEqual(final["split"], "test")
        self.assertEqual(final["counts"]["correct_names"], 1)
        self.assertEqual(final["counts"]["total"], 1)
        self.assertEqual(
            current_final_report(self.gallery, EvaluationStore(self.root), PROFILE), final
        )
        self.assertEqual(run_final_test(self.gallery, self.evaluation, PROFILE), final)
        self.assertEqual(current_report(self.gallery, self.evaluation, PROFILE), calibration)
        self.assertEqual(self.gallery.path.read_bytes(), before)

    def test_no_face_remains_in_denominator_and_all_outcomes_are_separate(self):
        empty, _ = self.prepare()
        for color, expected, vector in (
            ("green", None, (0.0, 1.0)),
            ("yellow", "self", (0.4, 0.916515)),
            ("black", None, (1.0, 0.0)),
        ):
            self.probe(color, expected, vector, "test", "Test réservé")
        run_evaluation(self.gallery, self.evaluation, PROFILE)
        freeze_final_test(self.gallery, self.evaluation, PROFILE)
        save_detections(FaceReferences(self.evaluation), empty, PROFILE, ())
        report = run_final_test(self.gallery, self.evaluation, PROFILE)
        self.assertEqual(report["counts"]["total"], 4)
        self.assertEqual(report["counts"]["unusable"], 1)
        self.assertEqual(report["counts"]["correct_unknown"], 1)
        self.assertEqual(report["counts"]["uncertain"], 1)
        self.assertEqual(report["counts"]["false_names_unknown"], 1)
        self.assertEqual(len(report["rows"]), 4)

    def test_context_changes_invalidate_freeze_without_auto_refreezing(self):
        photo, _ = self.prepare((1.0, 0.0))
        freeze_final_test(self.gallery, self.evaluation, PROFILE)
        self.evaluation.set_policy(Policy(0.3, 0.5, 0.05))
        self.assertIsNone(current_freeze(self.gallery, self.evaluation, PROFILE))
        with self.assertRaises(GalleryError):
            run_final_test(self.gallery, self.evaluation, PROFILE)
        run_evaluation(self.gallery, self.evaluation, PROFILE)
        freeze_final_test(self.gallery, self.evaluation, PROFILE)
        self.evaluation.update_case(photo.id, None, "test", "Test réservé", set())
        self.assertIsNone(current_freeze(self.gallery, self.evaluation, PROFILE))
        with self.assertRaises(GalleryError):
            run_final_test(self.gallery, self.evaluation, PROFILE)

    def test_exposed_images_cannot_be_reused_with_new_policy_or_new_preparation(self):
        photo, _ = self.prepare((1.0, 0.0))
        freeze_final_test(self.gallery, self.evaluation, PROFILE)
        run_final_test(self.gallery, self.evaluation, PROFILE)
        confirmed(self.evaluation, photo, (0.0, 1.0))
        self.assertIsNone(current_final_report(self.gallery, self.evaluation, PROFILE))
        with self.assertRaises(GalleryError):
            run_final_test(self.gallery, self.evaluation, PROFILE)
        self.evaluation.set_policy(Policy(0.3, 0.5, 0.05))
        self.assertIsNone(current_final_report(self.gallery, self.evaluation, PROFILE))
        run_evaluation(self.gallery, self.evaluation, PROFILE)
        with self.assertRaises(GalleryError):
            freeze_final_test(self.gallery, self.evaluation, PROFILE)

    def test_reference_selection_profile_and_calibration_preparation_invalidate_freeze(self):
        self.prepare((1.0, 0.0))
        freeze_final_test(self.gallery, self.evaluation, PROFILE)
        self.assertIsNone(current_freeze(self.gallery, self.evaluation, FaceProfile("other", 2)))
        self.evaluation.set_reference_selection([self.reference.id])
        self.assertIsNone(current_freeze(self.gallery, self.evaluation, PROFILE))
        run_evaluation(self.gallery, self.evaluation, PROFILE)
        freeze_final_test(self.gallery, self.evaluation, PROFILE)
        confirmed(self.evaluation, self.evaluation.cases()[0].photo, (0.0, 1.0))
        self.assertIsNone(current_freeze(self.gallery, self.evaluation, PROFILE))
        run_evaluation(self.gallery, self.evaluation, PROFILE)
        freeze_final_test(self.gallery, self.evaluation, PROFILE)
        confirmed(self.gallery, self.reference, (0.0, 1.0))
        self.assertIsNone(current_freeze(self.gallery, self.evaluation, PROFILE))

    def test_corrupt_freeze_and_final_archives_fail_explicitly_without_replacement(self):
        self.prepare((1.0, 0.0))
        frozen = freeze_final_test(self.gallery, self.evaluation, PROFILE)
        for value in ("broken", "[]", json.dumps({**frozen, "manifest": {}})):
            with self.evaluation._connection() as connection:
                connection.execute(
                    "UPDATE settings SET payload=? WHERE key='final_freeze'", (value,)
                )
            with self.subTest(value=value), self.assertRaises(GalleryError):
                current_freeze(self.gallery, self.evaluation, PROFILE)
        with self.evaluation._connection() as connection:
            connection.execute(
                "UPDATE settings SET payload=? WHERE key='final_freeze'", (json.dumps(frozen),)
            )
        final = run_final_test(self.gallery, self.evaluation, PROFILE)
        for value in ({**final, "split": "calibration"}, {**final, "counts": None}):
            with self.evaluation._connection() as connection:
                connection.execute("UPDATE final_runs SET payload=?", (json.dumps(value),))
            with self.subTest(value=value), self.assertRaises(GalleryError):
                current_final_report(self.gallery, self.evaluation, PROFILE)
        with self.evaluation._connection() as connection:
            connection.execute(
                "UPDATE final_runs SET payload=?", (json.dumps({**final, "test_sources": None}),)
            )
        with self.assertRaises(GalleryError):
            freeze_final_test(self.gallery, self.evaluation, PROFILE)

    def test_freeze_rejects_incomplete_calibration_policy_instead_of_crashing(self):
        _, calibration = self.prepare()
        del calibration["policy"]
        self.evaluation.save_report(calibration["fingerprint"], calibration)
        with self.assertRaises(GalleryError):
            freeze_final_test(self.gallery, self.evaluation, PROFILE)
