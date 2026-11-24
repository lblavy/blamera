"""Constat d'absence du sujet, distinct d'une identité inconnue."""

import unittest

import test_evaluation_final_ui as fixtures
from src.blamera_pc.evaluation_final import current_final_report, current_freeze
from src.blamera_pc.face_store import FaceReferences
from test_evaluation_service import PROFILE
from test_faces import CANDIDATE, save_detections


class TargetAbsenceUiTests(unittest.TestCase):
    setUp = fixtures.FinalEvaluationUiTests.setUp
    app = fixtures.FinalEvaluationUiTests.app
    button = staticmethod(fixtures.FinalEvaluationUiTests.button)
    evaluation_app = fixtures.FinalEvaluationUiTests.evaluation_app
    fixture = fixtures.FinalEvaluationUiTests.fixture

    def test_explicit_absence_is_persisted_reversible_and_has_a_reason_in_final_report(self):
        gallery, store = self.fixture()
        before = gallery.path.read_bytes()
        case = store.cases()[1]
        save_detections(FaceReferences(store), case.photo, PROFILE, (CANDIDATE, CANDIDATE))
        self.button_app = self.evaluation_app()
        self.button(self.button_app, "Évaluer la calibration").click().run()
        self.button(self.button_app, "Figer les paramètres pour le test final").click().run()
        frozen = current_freeze(gallery, store, PROFILE)
        next(x for x in self.button_app.radio if x.label == "Afficher le lot").set_value(
            "test"
        ).run()
        self.button(self.button_app, "Sujet attendu non détecté").click().run()
        self.assertFalse(self.button_app.exception)
        self.assertTrue(
            any("Sujet attendu non détecté" in x.value for x in self.button_app.caption)
        )
        self.assertFalse(
            any(x.key == f"prepare_faces_{case.photo.id}" for x in self.button_app.button)
        )
        app = self.evaluation_app()
        next(x for x in app.radio if x.label == "Afficher le lot").set_value("test").run()
        self.button(app, "Annuler le constat d'absence").click().run()
        self.button(app, "Évaluer le test final").click().run()
        self.assertTrue(app.error)
        self.button(app, "Sujet attendu non détecté").click().run()
        self.assertEqual(current_freeze(gallery, store, PROFILE), frozen)
        self.button(app, "Évaluer le test final").click().run()
        self.assertFalse(app.exception)
        report = current_final_report(gallery, store, PROFILE)
        self.assertEqual(report["rows"][0]["reason"], "Sujet attendu non détecté")
        self.assertEqual(store.cases()[1].expected, case.expected)
        self.assertEqual(gallery.path.read_bytes(), before)
