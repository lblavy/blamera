"""Le choix de référence reste explicite et persiste sans inférence au rerun."""

import unittest
from unittest.mock import patch

import test_people_ui as fixtures
from src.blamera_pc.face_data import FaceProfile
from src.blamera_pc.face_models import load_configuration
from src.blamera_pc.face_store import FaceReferences
from src.blamera_pc.gallery import GalleryError
from src.blamera_pc.gallery_store import GalleryStore
from src.blamera_pc.image_import import normalize_image
from test_face_service import FakeEngine
from test_faces import CANDIDATE, SAMPLE, save_detections, save_reference
from test_gallery import image_bytes


class UiEngine(FakeEngine):
    profile = load_configuration().profile


class FaceUiTests(unittest.TestCase):
    setUp = fixtures.PeopleUiTests.setUp
    app = fixtures.PeopleUiTests.app
    button = staticmethod(fixtures.PeopleUiTests.button)

    def fixture(self):
        store = GalleryStore(self.directory)
        owner = store.create("SYNTHETIC person")
        store.add_photo(owner, normalize_image(image_bytes()))
        return store, store.photos(owner)[0]

    def test_resource_factory_is_lazy_cached_and_rejects_a_changed_profile(self):
        from src.blamera_pc.face_ui import _engine

        _engine.clear()
        self.addCleanup(_engine.clear)
        with patch(
            "src.blamera_pc.face_ui.OpenCvFaceEngine", return_value=UiEngine()
        ) as constructor:
            _engine(UiEngine.profile.key)
            _engine(UiEngine.profile.key)
            constructor.assert_called_once()
            with self.assertRaises(GalleryError):
                _engine("wrong-profile")

    def test_preparation_selection_and_confirmation_are_separate_and_persistent(self):
        store, photo = self.fixture()
        engine = UiEngine()
        with patch("blamera_pc.face_ui._engine", return_value=engine) as factory:
            app = self.app()
            factory.assert_not_called()
            self.button(app, "Préparer les visages").click().run()
            self.assertEqual(engine.detect_calls, 1)
            self.assertEqual(engine.encode_calls, 0)
            choose = next(radio for radio in app.radio if radio.label == "Visage à associer")
            self.assertIsNone(choose.value)
            self.assertTrue(self.button(app, "Enregistrer ce visage").disabled)
            app.run()
            self.assertEqual(engine.detect_calls, 1)
            choose = next(radio for radio in app.radio if radio.label == "Visage à associer")
            choose.set_value(1).run()
            self.assertEqual(engine.encode_calls, 0)
            self.button(app, "Enregistrer ce visage").click().run()
            self.assertEqual(engine.encode_calls, 1)
            reference = FaceReferences(store).read(photo, engine.profile).reference
            self.assertEqual(reference.candidate, 1)
            app = self.app()
            self.assertEqual(engine.detect_calls, 1)
            self.assertEqual(engine.encode_calls, 1)
            self.assertIn("Visage confirmé", [row.value for row in app.success])
            self.assertFalse(app.exception)

    def test_no_face_has_no_confirmation_and_missing_model_keeps_gallery_usable(self):
        store, photo = self.fixture()
        engine = UiEngine()
        with (
            patch.object(engine, "detect", return_value=[]),
            patch("blamera_pc.face_ui._engine", return_value=engine),
        ):
            app = self.app()
            self.button(app, "Préparer les visages").click().run()
            self.assertIn("Aucun visage détecté.", [row.value for row in app.caption])
            self.assertFalse(any(b.label == "Enregistrer ce visage" for b in app.button))
        from blamera_pc.gallery import GalleryError as UiGalleryError

        with patch(
            "blamera_pc.face_ui._engine", side_effect=UiGalleryError("Modèles indisponibles")
        ):
            self.button(app, "Préparer les visages").click().run()
            self.assertTrue(app.error)
            self.assertEqual(len(store.photos(photo.person_id)), 1)
            self.assertFalse(app.exception)

    def test_active_model_is_visible_once_and_old_profile_is_not_presented_as_confirmed(self):
        store, photo = self.fixture()
        references = FaceReferences(store)
        old_profile = FaceProfile("old-sface", 128)
        save_detections(references, photo, old_profile, (CANDIDATE,))
        save_reference(references, photo, old_profile, 0, SAMPLE)
        with patch("blamera_pc.face_ui._engine") as factory:
            app = self.app()
            self.assertEqual(
                [row.value for row in app.caption].count("Galerie · visages : ArcFace ResNet-50"), 1
            )
            self.assertNotIn("Visage confirme", [row.value for row in app.success])
            self.assertFalse(app.success)
            factory.assert_not_called()
            self.assertEqual(store.photos(photo.person_id)[0].image, photo.image)
            self.assertIsNotNone(references.read(photo, old_profile).reference)
