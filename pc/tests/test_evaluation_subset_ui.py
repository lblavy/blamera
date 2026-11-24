"""Sélection de références dans Évaluation, sans mutation de l'enrôlement."""

import unittest
from types import SimpleNamespace
from unittest.mock import patch

import test_evaluation_ui as fixtures
from src.blamera_pc.evaluation_store import EvaluationStore
from src.blamera_pc.face_store import FaceReferences
from src.blamera_pc.gallery_store import GalleryStore
from src.blamera_pc.image_import import normalize_image
from test_evaluation_service import PROFILE, confirmed
from test_gallery import image_bytes


class EvaluationSubsetUiTests(unittest.TestCase):
    setUp = fixtures.EvaluationUiTests.setUp
    app = fixtures.EvaluationUiTests.app
    button = staticmethod(fixtures.EvaluationUiTests.button)
    evaluation_app = fixtures.EvaluationUiTests.evaluation_app

    @staticmethod
    def selector(app):
        return next(item for item in app.multiselect if item.label == "Références retenues")

    def fixture(self):
        gallery = GalleryStore(self.directory)
        person = gallery.create("Personne synthétique")
        for color in ("red", "green"):
            gallery.add_photo(person, normalize_image(image_bytes(color)))
        first, second = gallery.photos(person)
        confirmed(gallery, first)
        confirmed(gallery, second, (0.0, 1.0))
        store = EvaluationStore(self.directory)
        store.assign_sessions([first], "Jour de référence")
        store.add_case(
            normalize_image(image_bytes("blue")), person, "calibration", "Autre jour", set(), set()
        )
        confirmed(store, store.cases()[0].photo)
        patch(
            "blamera_pc.evaluation_ui.load_configuration",
            return_value=SimpleNamespace(profile=PROFILE),
        ).start()
        return gallery, store, first, second

    def test_selecting_only_dated_reference_persists_and_keeps_gallery_untouched(self):
        gallery, store, first, second = self.fixture()
        before = gallery.path.read_bytes()
        app = self.evaluation_app()
        self.assertIn(
            "2 références retenues sur 2 disponibles", [item.value for item in app.caption]
        )
        self.selector(app).set_value([first.id])
        self.button(app, "Appliquer la sélection").click().run()
        self.assertEqual(store.reference_selection(), (first.id,))
        self.assertIn(
            "1 références retenues sur 2 disponibles", [item.value for item in app.caption]
        )
        self.button(app, "Évaluer la calibration").click().run()
        self.assertIn("Résultats par image", [item.value for item in app.subheader])
        app = self.evaluation_app()
        self.assertEqual(self.selector(app).value, [first.id])
        self.assertEqual(gallery.path.read_bytes(), before)
        self.assertIsNotNone(FaceReferences(gallery).read(second, PROFILE).reference)

    def test_empty_selection_is_visible_and_can_restore_all(self):
        _, store, first, _ = self.fixture()
        store.set_reference_selection([first.id])
        app = self.evaluation_app()
        self.selector(app).set_value([])
        self.button(app, "Appliquer la sélection").click().run()
        self.assertEqual(store.reference_selection(), ())
        self.assertIn(
            "0 références retenues sur 2 disponibles", [item.value for item in app.caption]
        )
        self.button(app, "Évaluer la calibration").click().run()
        self.assertTrue(app.error)
        self.button(app, "Utiliser toutes les références").click().run()
        self.assertIsNone(store.reference_selection())
        self.assertIn(
            "2 références retenues sur 2 disponibles", [item.value for item in app.caption]
        )

    def test_missing_selected_reference_is_visible_and_correctable(self):
        _, store, first, _ = self.fixture()
        store.set_reference_selection(["deleted-id"])
        app = self.evaluation_app()
        self.assertFalse(app.exception)
        self.assertTrue(app.warning)
        self.selector(app).set_value([first.id])
        self.button(app, "Appliquer la sélection").click().run()
        self.assertEqual(store.reference_selection(), (first.id,))
        self.assertFalse(app.warning)
