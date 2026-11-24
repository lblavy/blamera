"""Le choix humain précède tout embedding ; erreurs et réponse tardive isolées."""

import unittest
from unittest.mock import patch

import test_faces as fixtures
from src.blamera_pc.face_data import FaceSample
from src.blamera_pc.face_service import confirm_face, prepare_faces
from src.blamera_pc.gallery import GalleryError
from test_faces import CANDIDATE, PROFILE, SAMPLE, save_detections, save_reference


class FakeEngine:
    profile = PROFILE

    def __init__(self):
        self.detect_calls = 0
        self.encode_calls = 0

    def detect(self, image):
        self.detect_calls += 1
        return [CANDIDATE, CANDIDATE]

    def encode(self, image, candidate):
        self.encode_calls += 1
        return FaceSample(SAMPLE.crop, (3.0, 4.0) + (0.0,) * (self.profile.dimension - 2))


class ExplicitServiceTests(unittest.TestCase):
    setUp = fixtures.FaceTests.setUp

    def test_confirmation_without_detection_or_with_invalid_choice_never_encodes(self):
        engine = FakeEngine()
        with self.assertRaises(GalleryError):
            confirm_face(self.faces, self.photo, 0, engine)
        prepare_faces(self.faces, self.photo, engine)
        for index in [True, -1, 99]:
            with self.assertRaises(GalleryError):
                confirm_face(self.faces, self.photo, index, engine)
        self.assertEqual(engine.encode_calls, 0)

    def test_detection_is_not_embedding_and_confirmation_normalizes_selected_face(self):
        engine = FakeEngine()
        prepare_faces(self.faces, self.photo, engine)
        self.assertEqual(engine.encode_calls, 0)
        self.assertIsNone(self.faces.read(self.photo, PROFILE).selected)
        confirm_face(self.faces, self.photo, 1, engine)
        state = self.faces.read(self.photo, PROFILE)
        self.assertEqual(state.reference.candidate, 1)
        self.assertAlmostEqual(state.reference.sample.vector[0], 0.6, places=6)

    def test_encoder_failure_does_not_destroy_previous_reference(self):
        engine = FakeEngine()
        prepare_faces(self.faces, self.photo, engine)
        confirm_face(self.faces, self.photo, 0, engine)
        with patch.object(engine, "encode", side_effect=GalleryError("Synthetic error")):
            with self.assertRaises(GalleryError):
                confirm_face(self.faces, self.photo, 1, engine)
        self.assertEqual(self.faces.read(self.photo, PROFILE).reference.candidate, 0)

    def test_deleted_photo_is_not_resurrected_by_late_detection(self):
        engine = FakeEngine()
        original = engine.detect

        def late(image):
            self.gallery.delete_photo(self.photo.id)
            return original(image)

        with patch.object(engine, "detect", side_effect=late):
            with self.assertRaises(GalleryError):
                prepare_faces(self.faces, self.photo, engine)
        self.assertEqual(self.gallery.photos(self.alice), [])

    def test_new_detection_cannot_receive_a_late_embedding_for_old_candidates(self):
        engine = FakeEngine()
        prepare_faces(self.faces, self.photo, engine)

        def new_candidates(image, candidate):
            save_detections(self.faces, self.photo, PROFILE, (CANDIDATE,))
            return SAMPLE

        with patch.object(engine, "encode", side_effect=new_candidates):
            with self.assertRaises(GalleryError):
                confirm_face(self.faces, self.photo, 0, engine)
        self.assertIsNone(self.faces.read(self.photo, PROFILE).reference)

    def test_late_detection_does_not_erase_a_newer_confirmed_reference(self):
        engine = FakeEngine()

        def newer(image):
            save_detections(self.faces, self.photo, PROFILE, (CANDIDATE,))
            save_reference(self.faces, self.photo, PROFILE, 0, SAMPLE)
            return [CANDIDATE, CANDIDATE]

        with patch.object(engine, "detect", side_effect=newer):
            with self.assertRaises(GalleryError):
                prepare_faces(self.faces, self.photo, engine)
        self.assertIsNotNone(self.faces.read(self.photo, PROFILE).reference)
