"""Le test final se prépare sous un gel et reste distinct de Calibration."""

import unittest
from types import SimpleNamespace
from unittest.mock import patch

import test_evaluation_ui as fixtures
from src.blamera_pc.evaluation_final import current_final_report, current_freeze
from src.blamera_pc.evaluation_service import run_evaluation
from src.blamera_pc.evaluation_store import EvaluationStore
from src.blamera_pc.gallery_store import GalleryStore
from src.blamera_pc.image_import import normalize_image
from test_evaluation_service import PROFILE, confirmed
from test_gallery import image_bytes


class FinalEvaluationUiTests(unittest.TestCase):
    setUp = fixtures.EvaluationUiTests.setUp
    app = fixtures.EvaluationUiTests.app
    button = staticmethod(fixtures.EvaluationUiTests.button)
    evaluation_app = fixtures.EvaluationUiTests.evaluation_app

    def fixture(self):
        gallery = GalleryStore(self.directory)
        person = gallery.create("Personne synthétique")
        gallery.add_photo(person, normalize_image(image_bytes("red")))
        photo = gallery.photos(person)[0]
        confirmed(gallery, photo)
        store = EvaluationStore(self.directory)
        store.assign_sessions([photo], "Référence")
        for color, split in (("blue", "calibration"), ("pink", "test")):
            store.add_case(normalize_image(image_bytes(color)), person, split, split, set(), set())
        confirmed(store, store.cases()[0].photo)
        run_evaluation(gallery, store, PROFILE)
        patch(
            "blamera_pc.evaluation_ui.load_configuration",
            return_value=SimpleNamespace(profile=PROFILE),
        ).start()
        return gallery, store

    def test_freeze_unlocks_target_preparation_and_final_report_survives_reload(self):
        gallery, store = self.fixture()
        before = gallery.path.read_bytes()
        app = self.evaluation_app()
        self.assertTrue(self.button(app, "Évaluer le test final").disabled)
        next(x for x in app.radio if x.label == "Afficher le lot").set_value("test").run()
        case = store.cases()[1]
        self.assertFalse(any(x.key == f"prepare_faces_{case.photo.id}" for x in app.button))
        self.button(app, "Figer les paramètres pour le test final").click().run()
        self.assertFalse(app.exception)
        self.assertIsNotNone(current_freeze(gallery, store, PROFILE))
        self.assertTrue(any(x.key == f"prepare_faces_{case.photo.id}" for x in app.button))
        self.button(app, "Évaluer le test final").click().run()
        self.assertTrue(app.error)
        confirmed(store, case.photo)
        app.run()
        self.button(app, "Évaluer le test final").click().run()
        self.assertFalse(app.exception)
        self.assertIn("Bilan du Test final", [x.value for x in app.subheader])
        self.assertIn("Bilan de Calibration", [x.value for x in app.subheader])
        self.assertIsNotNone(current_final_report(gallery, store, PROFILE))
        app = self.evaluation_app()
        self.assertIn("Bilan du Test final", [x.value for x in app.subheader])
        self.assertEqual(gallery.path.read_bytes(), before)

    def test_stale_freeze_hides_current_final_report_and_cannot_reuse_exposed_images(self):
        _, store = self.fixture()
        confirmed(store, store.cases()[1].photo)
        app = self.evaluation_app()
        self.button(app, "Figer les paramètres pour le test final").click().run()
        self.button(app, "Évaluer le test final").click().run()
        next(x for x in app.number_input if x.label == "Seuil d'acceptation").set_value(0.5)
        self.button(app, "Appliquer les paramètres").click().run()
        self.assertNotIn("Bilan du Test final", [x.value for x in app.subheader])
        self.assertTrue(self.button(app, "Évaluer le test final").disabled)
        self.assertTrue(any("historique" in x.value for x in app.caption))
        self.button(app, "Évaluer la calibration").click().run()
        self.button(app, "Figer les paramètres pour le test final").click().run()
        self.assertTrue(any("déjà été évaluées" in x.value for x in app.error))
