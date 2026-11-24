"""Évaluation via AppTest, sans modèle réseau ni données personnelles."""

import unittest
from types import SimpleNamespace
from unittest.mock import patch

import test_people_ui as fixtures
from src.blamera_pc.evaluation_store import EvaluationStore
from src.blamera_pc.gallery_store import GalleryStore
from src.blamera_pc.image_import import normalize_image
from test_evaluation_service import PROFILE, confirmed
from test_gallery import image_bytes


class EvaluationUiTests(unittest.TestCase):
    setUp = fixtures.PeopleUiTests.setUp
    app = fixtures.PeopleUiTests.app
    button = staticmethod(fixtures.PeopleUiTests.button)

    def evaluation_app(self):
        app = self.app()
        next(radio for radio in app.radio if radio.label == "Personnes").set_value(
            "Évaluation"
        ).run()
        self.assertFalse(app.exception)
        return app

    def test_navigation_empty_state_and_no_native_calculation(self):
        with patch("blamera_pc.face_ui._engine") as engine:
            app = self.evaluation_app()
            self.assertIn("Évaluation des visages", [item.value for item in app.subheader])
            self.assertTrue(any("Test final" in item.value for item in app.caption))
            engine.assert_not_called()

    def test_import_unknown_reserved_and_correction_persist_without_enrollment(self):
        store = GalleryStore(self.directory)
        owner = store.create("Alice")
        upload = SimpleNamespace(name="synthetic.png", getvalue=lambda: image_bytes("blue"))
        with patch("streamlit.file_uploader", return_value=[upload]):
            app = self.evaluation_app()
            app.selectbox(key="eval_expected").set_value("__unknown__")
            app.selectbox(key="eval_split").set_value("test")
            app.text_input(key="eval_session").set_value("Séance test")
            self.button(app, "Importer dans le corpus").click().run()
        cases = EvaluationStore(self.directory).cases()
        self.assertEqual(len(cases), 1)
        self.assertIsNone(cases[0].expected)
        self.assertEqual(cases[0].split, "test")
        self.assertEqual(store.photos(owner), [])
        app.run()
        self.assertEqual(len(EvaluationStore(self.directory).cases()), 1)

    def test_evaluation_shows_counts_and_reloads_saved_report(self):
        store = GalleryStore(self.directory)
        owner = store.create("Alice")
        store.add_photo(owner, normalize_image(image_bytes("red")))
        reference = store.photos(owner)[0]
        confirmed(store, reference)
        evaluation = EvaluationStore(self.directory)
        evaluation.assign_sessions([reference], "Référence")
        evaluation.add_case(
            normalize_image(image_bytes("blue")), owner, "calibration", "Calibration", set(), set()
        )
        confirmed(evaluation, evaluation.cases()[0].photo)
        with patch(
            "blamera_pc.evaluation_ui.load_configuration",
            return_value=SimpleNamespace(profile=PROFILE),
        ):
            app = self.evaluation_app()
            self.button(app, "Évaluer la calibration").click().run()
            self.assertFalse(app.exception)
            self.assertIn("Résultats par image", [item.value for item in app.subheader])
            app = self.evaluation_app()
            self.assertIn("Résultats par image", [item.value for item in app.subheader])

    def test_evaluation_error_keeps_corpus_management_visible(self):
        app = self.evaluation_app()
        self.button(app, "Évaluer la calibration").click().run()
        self.assertTrue(app.error)
        self.assertIn("Photos du corpus", [item.value for item in app.subheader])

    def test_case_metadata_edit_and_confirmed_delete(self):
        store = GalleryStore(self.directory)
        owner = store.create("Alice")
        evaluation = EvaluationStore(self.directory)
        evaluation.add_case(
            normalize_image(image_bytes()), owner, "calibration", "Avant", set(), set()
        )
        case = evaluation.cases()[0]
        app = self.evaluation_app()
        app.selectbox(key=f"eval_identity_{case.photo.id}").set_value("__unknown__")
        app.text_input(key=f"eval_session_{case.photo.id}").set_value("Après")
        self.button(app, "Enregistrer l'annotation").click().run()
        self.assertIsNone(evaluation.cases()[0].expected)
        self.assertEqual(evaluation.cases()[0].session, "Après")
        self.assertTrue(self.button(app, "Retirer du corpus").disabled)
        app.checkbox(key=f"eval_delete_{case.photo.id}").check().run()
        self.button(app, "Retirer du corpus").click().run()
        self.assertEqual(evaluation.cases(), [])

    def test_reference_session_batch_and_policy_application(self):
        store = GalleryStore(self.directory)
        owner = store.create("Alice")
        store.add_photo(owner, normalize_image(image_bytes()))
        photo = store.photos(owner)[0]
        confirmed(store, photo)
        with patch(
            "blamera_pc.evaluation_ui.load_configuration",
            return_value=SimpleNamespace(profile=PROFILE),
        ):
            app = self.evaluation_app()
            app.text_input(key="ref_session_name").set_value("Ancienne séance")
            self.button(app, "Associer la séance").click().run()
            self.assertEqual(
                EvaluationStore(self.directory).reference_session(photo), "Ancienne séance"
            )
            next(
                item for item in app.number_input if item.label == "Seuil d'acceptation"
            ).set_value(0.75)
            self.button(app, "Appliquer les paramètres").click().run()
            self.assertEqual(EvaluationStore(self.directory).policy().accept, 0.75)

    def test_bad_file_and_oversized_batch_do_not_add_cases(self):
        bad = SimpleNamespace(name="bad.png", getvalue=lambda: b"SYNTHETIC not an image")
        for files in ([bad], [bad] * 21):
            with patch("streamlit.file_uploader", return_value=files):
                app = self.evaluation_app()
                app.text_input(key="eval_session").set_value("Nouvelle séance")
                self.button(app, "Importer dans le corpus").click().run()
                self.assertFalse(app.exception)
                self.assertEqual(EvaluationStore(self.directory).cases(), [])

    def test_deleted_expected_identity_remains_visible_as_deleted(self):
        evaluation = EvaluationStore(self.directory)
        evaluation.add_case(
            normalize_image(image_bytes()), "removed", "calibration", "Séance", set(), set()
        )
        app = self.evaluation_app()
        self.assertTrue(any("Identité retirée" in item.value for item in app.caption))
