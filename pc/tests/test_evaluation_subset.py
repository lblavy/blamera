"""Une sélection d'évaluation ne modifie jamais la galerie de références."""

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from src.blamera_pc.evaluation_service import current_report, run_evaluation
from src.blamera_pc.evaluation_store import EvaluationStore
from src.blamera_pc.face_store import FaceReferences
from src.blamera_pc.gallery import GalleryError
from src.blamera_pc.gallery_store import GalleryStore
from src.blamera_pc.image_import import normalize_image
from test_evaluation_service import PROFILE, confirmed
from test_gallery import image_bytes


class EvaluationSubsetTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.gallery = GalleryStore(self.root)
        self.owner = self.gallery.create("Personne synthétique")
        for color in ("red", "green"):
            self.gallery.add_photo(self.owner, normalize_image(image_bytes(color)))
        self.first, self.second = self.gallery.photos(self.owner)
        confirmed(self.gallery, self.first, (1.0, 0.0))
        confirmed(self.gallery, self.second, (0.0, 1.0))
        self.evaluation = EvaluationStore(self.root)
        self.evaluation.assign_sessions([self.first], "Jour référence 1")
        self.evaluation.assign_sessions([self.second], "Jour référence 2")
        self.evaluation.add_case(
            normalize_image(image_bytes("blue")),
            self.owner,
            "calibration",
            "Autre jour",
            set(),
            set(),
        )
        confirmed(self.evaluation, self.evaluation.cases()[0].photo)

    def configure(self, values):
        with self.evaluation._connection() as connection:
            connection.execute(
                "INSERT OR REPLACE INTO settings VALUES ('reference_selection', ?)",
                (json.dumps(values),),
            )

    def test_selection_api_validates_and_persists_without_touching_gallery(self):
        self.assertIsNone(self.evaluation.reference_selection())
        before = self.gallery.path.read_bytes()
        self.evaluation.set_reference_selection([self.second.id, self.first.id])
        self.assertEqual(
            EvaluationStore(self.root).reference_selection(),
            tuple(sorted((self.first.id, self.second.id))),
        )
        for value in (False, [True], [""], ["x", "x"], [" leading"], ["x\n"], ["x" * 129]):
            with self.subTest(value=value), self.assertRaises(GalleryError):
                self.evaluation.set_reference_selection(value)
        self.evaluation.set_reference_selection([])
        self.assertEqual(EvaluationStore(self.root).reference_selection(), ())
        self.evaluation.set_reference_selection(None)
        self.assertIsNone(EvaluationStore(self.root).reference_selection())
        self.assertEqual(self.gallery.path.read_bytes(), before)

    def test_corrupted_selection_is_rejected_without_changing_saved_report(self):
        report = run_evaluation(self.gallery, self.evaluation, PROFILE)
        for payload in ("broken", "{}", "false", '["x","x"]', "[1]"):
            with self.evaluation._connection() as connection:
                connection.execute(
                    "INSERT OR REPLACE INTO settings VALUES ('reference_selection', ?)", (payload,)
                )
            with self.subTest(payload=payload), self.assertRaises(GalleryError):
                self.evaluation.reference_selection()
        self.evaluation.set_reference_selection(None)
        self.assertEqual(current_report(self.gallery, self.evaluation, PROFILE), report)

    def test_selected_reference_alone_builds_prototype_and_excluded_session_is_not_required(self):
        with self.evaluation._connection() as connection:
            connection.execute("DELETE FROM reference_sessions WHERE photo_id=?", (self.second.id,))
        self.configure([self.first.id])
        before = hashlib.sha256(self.gallery.path.read_bytes()).hexdigest()
        report = run_evaluation(self.gallery, self.evaluation, PROFILE)
        self.assertEqual(report["reference_counts"], {self.owner: 1})
        self.assertAlmostEqual(report["rows"][0]["similarity"], 1.0)
        self.assertEqual(hashlib.sha256(self.gallery.path.read_bytes()).hexdigest(), before)
        self.assertIsNotNone(FaceReferences(self.gallery).read(self.second, PROFILE).reference)

    def test_default_keeps_all_and_explicit_selection_invalidates_previous_report(self):
        report = run_evaluation(self.gallery, self.evaluation, PROFILE)
        self.assertEqual(report["reference_counts"], {self.owner: 2})
        self.configure([self.first.id])
        self.assertIsNone(current_report(self.gallery, self.evaluation, PROFILE))
        selected = run_evaluation(self.gallery, self.evaluation, PROFILE)
        self.assertNotEqual(selected["fingerprint"], report["fingerprint"])
        self.assertEqual(selected["manifest"]["reference_selection"], [self.first.id])
        self.assertEqual(
            current_report(self.gallery, EvaluationStore(self.root), PROFILE), selected
        )

    def test_empty_or_missing_selected_reference_never_falls_back_to_all(self):
        for values in ([], ["missing-photo"]):
            self.configure(values)
            with self.subTest(values=values), self.assertRaises(GalleryError):
                run_evaluation(self.gallery, self.evaluation, PROFILE)

    def test_selected_unconfirmed_or_incompatible_reference_is_an_error(self):
        self.configure([self.first.id])
        with self.gallery._connection() as connection:
            connection.execute("DELETE FROM face_detections WHERE photo_id=?", (self.first.id,))
        with self.assertRaises(GalleryError):
            run_evaluation(self.gallery, self.evaluation, PROFILE)
        confirmed(self.gallery, self.first)
        with self.gallery._connection() as connection:
            connection.execute(
                "UPDATE face_detections SET profile='other' WHERE photo_id=?", (self.first.id,)
            )
        with self.assertRaises(GalleryError):
            run_evaluation(self.gallery, self.evaluation, PROFILE)

    def test_reference_added_later_is_not_silently_selected(self):
        self.configure([self.first.id])
        self.gallery.add_photo(self.owner, normalize_image(image_bytes("yellow")))
        newer = self.gallery.photos(self.owner)[-1]
        confirmed(self.gallery, newer, (-1.0, 0.0))
        report = run_evaluation(self.gallery, self.evaluation, PROFILE)
        self.assertEqual(report["reference_counts"], {self.owner: 1})

    def test_duplicate_of_excluded_reference_is_still_rejected(self):
        self.configure([self.first.id])
        self.evaluation.add_case(
            normalize_image(image_bytes("green")),
            self.owner,
            "calibration",
            "Autre jour",
            set(),
            set(),
        )
        confirmed(self.evaluation, self.evaluation.cases()[-1].photo)
        with self.assertRaises(GalleryError):
            run_evaluation(self.gallery, self.evaluation, PROFILE)

    def test_expected_person_without_selected_reference_blocks_report(self):
        other = self.gallery.create("Autre personne synthétique")
        self.gallery.add_photo(other, normalize_image(image_bytes("yellow")))
        photo = self.gallery.photos(other)[0]
        confirmed(self.gallery, photo, (0.0, 1.0))
        self.evaluation.assign_sessions([photo], "Troisième référence")
        self.configure([self.first.id])
        self.evaluation.add_case(
            normalize_image(image_bytes("black")), other, "calibration", "Autre jour", set(), set()
        )
        confirmed(self.evaluation, self.evaluation.cases()[-1].photo, (0.0, 1.0))
        with self.assertRaises(GalleryError):
            run_evaluation(self.gallery, self.evaluation, PROFILE)
