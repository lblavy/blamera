"""Ne jamais forcer un autre visage quand la cible attendue manque."""

import unittest
from dataclasses import replace

import test_evaluation_service as fixtures
from src.blamera_pc.evaluation_absence import TargetAbsences
from src.blamera_pc.evaluation_final import (
    current_final_report,
    current_freeze,
    freeze_final_test,
    run_final_test,
)
from src.blamera_pc.evaluation_service import current_report, run_evaluation
from src.blamera_pc.evaluation_snapshot import manifest_fingerprint, snapshot
from src.blamera_pc.evaluation_store import EvaluationStore
from src.blamera_pc.face_data import FaceProfile
from src.blamera_pc.face_store import FaceReferences
from src.blamera_pc.gallery import GalleryError
from src.blamera_pc.image_import import normalize_image
from test_evaluation_service import PROFILE, confirmed
from test_faces import CANDIDATE, save_detections
from test_gallery import image_bytes


class TargetAbsenceTests(unittest.TestCase):
    setUp = fixtures.EvaluationServiceTests.setUp
    probe = fixtures.EvaluationServiceTests.probe

    def fixture(self):
        self.probe()
        missing = self.probe("pink", "self", None, "test", "Test final")
        faces = FaceReferences(self.evaluation)
        save_detections(faces, missing, PROFILE, (CANDIDATE, CANDIDATE))
        calibration = run_evaluation(self.gallery, self.evaluation, PROFILE)
        frozen = freeze_final_test(self.gallery, self.evaluation, PROFILE)
        case = self.evaluation.cases()[1]
        return case, faces, calibration, frozen

    def mark(self, case, faces):
        TargetAbsences(self.evaluation).mark(case, PROFILE, generation=faces.generation(case.photo))

    def test_missing_expected_target_among_detected_faces_counts_unusable_without_forcing_name(
        self,
    ):
        case, faces, calibration, frozen = self.fixture()
        before = self.gallery.path.read_bytes()
        with self.assertRaises(GalleryError):
            run_final_test(self.gallery, self.evaluation, PROFILE)
        self.mark(case, faces)
        self.assertEqual(current_freeze(self.gallery, self.evaluation, PROFILE), frozen)
        self.assertEqual(current_report(self.gallery, self.evaluation, PROFILE), calibration)
        report = run_final_test(self.gallery, self.evaluation, PROFILE)
        self.assertEqual(report["counts"]["total"], 1)
        self.assertEqual(report["counts"]["unusable"], 1)
        row = report["rows"][0]
        self.assertEqual(row["reason"], "Sujet attendu non détecté")
        self.assertIsNone(row["identity"])
        self.assertIsNone(row["candidate"])
        self.assertIsNone(row["similarity"])
        self.assertEqual(self.gallery.path.read_bytes(), before)
        self.assertEqual(
            current_final_report(self.gallery, EvaluationStore(self.root), PROFILE), report
        )

    def test_clear_reblocks_and_repreparation_or_confirmation_invalidates_missing_marker(self):
        case, faces, _, frozen = self.fixture()
        self.mark(case, faces)
        TargetAbsences(self.evaluation).clear(
            case, PROFILE, generation=faces.generation(case.photo)
        )
        with self.assertRaises(GalleryError):
            run_final_test(self.gallery, self.evaluation, PROFILE)
        self.mark(case, faces)
        save_detections(faces, case.photo, PROFILE, (CANDIDATE,))
        with self.assertRaises(GalleryError):
            run_final_test(self.gallery, self.evaluation, PROFILE)
        self.mark(case, faces)
        confirmed(self.evaluation, case.photo)
        report = run_final_test(self.gallery, self.evaluation, PROFILE)
        self.assertEqual(report["counts"]["correct_names"], 1)
        self.assertEqual(current_freeze(self.gallery, self.evaluation, PROFILE), frozen)

    def test_mark_is_bound_to_current_annotation_source_profile_and_generation(self):
        case, faces, _, _ = self.fixture()
        target = TargetAbsences(self.evaluation)
        for profile, generation in (
            (PROFILE, "stale"),
            (FaceProfile("wrong", 2), faces.generation(case.photo)),
        ):
            with self.subTest(profile=profile), self.assertRaises(GalleryError):
                target.mark(case, profile, generation=generation)
        self.evaluation.update_case(case.photo.id, None, "test", "Test final", set())
        with self.assertRaises(GalleryError):
            self.mark(case, faces)
        current = self.evaluation.cases()[1]
        self.mark(current, faces)
        self.evaluation.update_case(case.photo.id, self.person, "test", "Test final", set())
        self.evaluation.update_case(case.photo.id, None, "test", "Test final", set())
        probe = snapshot(self.gallery, self.evaluation, PROFILE).probes[1]
        self.assertFalse(probe.missing_target)
        self.evaluation.delete_photo(case.photo.id)
        with self.assertRaises(GalleryError):
            self.mark(current, faces)

    def test_existing_freeze_manifest_stays_identical_without_annotations(self):
        _, _, calibration, frozen = self.fixture()
        view = snapshot(self.gallery, self.evaluation, PROFILE)
        legacy = {
            **view.manifest,
            "cases": [
                [*entry[:5], None] if entry[3] == "test" else entry
                for entry in view.manifest["cases"]
            ],
        }
        self.assertEqual(view.calibration_fingerprint, manifest_fingerprint(legacy))
        self.assertEqual(calibration["fingerprint"], frozen["fingerprint"])
        self.assertEqual(len(view.manifest["cases"][1]), 6)
        self.assertEqual(
            set(view.manifest),
            {"rule", "profile", "policy", "people", "references", "reference_selection", "cases"},
        )

    def test_calibration_absence_is_explicit_and_zero_face_has_distinct_reason(self):
        missing = self.probe(vector=None)
        faces = FaceReferences(self.evaluation)
        save_detections(faces, missing, PROFILE, (CANDIDATE,))
        self.mark(self.evaluation.cases()[0], faces)
        empty = self.probe("green", None, None)
        save_detections(faces, empty, PROFILE, ())
        report = run_evaluation(self.gallery, self.evaluation, PROFILE)
        self.assertEqual(report["counts"]["unusable"], 2)
        self.assertEqual(
            [r["reason"] for r in report["rows"]],
            ["Sujet attendu non détecté", "Aucun visage détecté"],
        )

    def test_changing_absence_after_exposure_cannot_rewrite_final_report(self):
        case, faces, _, _ = self.fixture()
        self.mark(case, faces)
        run_final_test(self.gallery, self.evaluation, PROFILE)
        TargetAbsences(self.evaluation).clear(
            case, PROFILE, generation=faces.generation(case.photo)
        )
        self.assertIsNone(current_final_report(self.gallery, self.evaluation, PROFILE))
        confirmed(self.evaluation, case.photo)
        with self.assertRaises(GalleryError):
            run_final_test(self.gallery, self.evaluation, PROFILE)

    def test_fifteen_test_cases_keep_thirteen_confirmations_and_two_distinct_failures(self):
        case, faces, _, _ = self.fixture()
        for index in range(13):
            self.probe(f"#{index + 1:06x}", "self", (1.0, 0.0), "test", "Test final")
        empty = self.probe("cyan", "self", None, "test", "Test final")
        save_detections(faces, empty, PROFILE, ())
        self.evaluation.set_policy(fixtures.Policy(0.3, 0.5, 0.05))
        calibration = run_evaluation(self.gallery, self.evaluation, PROFILE)
        frozen = freeze_final_test(self.gallery, self.evaluation, PROFILE)
        self.mark(case, faces)
        report = run_final_test(self.gallery, self.evaluation, PROFILE)
        self.assertEqual(report["counts"]["total"], 15)
        self.assertEqual(report["counts"]["usable"], 13)
        self.assertEqual(report["counts"]["unusable"], 2)
        self.assertEqual(report["counts"]["correct_names"], 13)
        self.assertEqual(report["rows"][0]["reason"], "Sujet attendu non détecté")
        self.assertEqual(report["rows"][-1]["reason"], "Aucun visage détecté")
        self.assertEqual(current_report(self.gallery, self.evaluation, PROFILE), calibration)
        self.assertEqual(current_freeze(self.gallery, self.evaluation, PROFILE), frozen)

    def test_unprepared_zero_face_or_changed_pixels_cannot_receive_a_missing_marker(self):
        case, faces, _, _ = self.fixture()
        target = TargetAbsences(self.evaluation)
        changed = replace(
            case, photo=replace(case.photo, image=normalize_image(image_bytes("cyan")).image)
        )
        with self.assertRaises(GalleryError):
            target.mark(changed, PROFILE, generation=faces.generation(case.photo))
        save_detections(faces, case.photo, PROFILE, ())
        with self.assertRaises(GalleryError):
            self.mark(case, faces)
        empty = self.probe("cyan", "self", None, "test", "Test final")
        with self.assertRaises(GalleryError):
            target.mark(self.evaluation.cases()[-1], PROFILE, generation="missing")
        self.assertIsNone(faces.read(empty, PROFILE))

    def test_marking_replaces_a_previous_face_choice_and_undo_does_not_restore_it(self):
        case, faces, _, _ = self.fixture()
        confirmed(self.evaluation, case.photo)
        self.mark(case, faces)
        state = faces.read(case.photo, PROFILE)
        self.assertIsNone(state.reference)
        self.assertIsNone(state.selected)
        TargetAbsences(self.evaluation).clear(case, PROFILE, generation=state.generation)
        with self.assertRaises(GalleryError):
            run_final_test(self.gallery, self.evaluation, PROFILE)

    def test_schema_upgrade_preserves_an_existing_freeze_and_main_gallery(self):
        _, _, calibration, frozen = self.fixture()
        before = self.gallery.path.read_bytes()
        with self.evaluation._connection() as connection:
            connection.execute("DROP TABLE target_absences")
        upgraded = EvaluationStore(self.root)
        self.assertEqual(current_report(self.gallery, upgraded, PROFILE), calibration)
        self.assertEqual(current_freeze(self.gallery, upgraded, PROFILE), frozen)
        self.assertEqual(self.gallery.path.read_bytes(), before)
