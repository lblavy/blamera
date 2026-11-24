"""Contrat OpenCV aux frontières simulées ; aucune inférence distante."""

import unittest
from dataclasses import replace
from unittest.mock import patch

import numpy as np
from src.blamera_pc.face_data import FaceProfile
from src.blamera_pc.face_engine import OpenCvFaceEngine
from src.blamera_pc.face_models import load_configuration
from src.blamera_pc.gallery import GalleryError
from test_gallery import image_bytes


class Detector:
    def setInputSize(self, value):
        self.size = value

    def detect(self, image):
        self.image = image
        return 1, np.array(
            [
                [
                    32.0,
                    224.0,
                    128.0,
                    128.0,
                    64.0,
                    256.0,
                    128.0,
                    256.0,
                    96.0,
                    288.0,
                    64.0,
                    320.0,
                    128.0,
                    320.0,
                    0.95,
                ]
            ],
            dtype=np.float32,
        )


class Recognizer:
    def alignCrop(self, image, row):
        self.image, self.row = image, row
        result = np.zeros((112, 112, 3), dtype=np.uint8)
        result[:, :, 2] = 255
        return result

    def feature(self, image):
        self.aligned = image
        return np.array([[3.0, 4.0] + [0.0] * 126], dtype=np.float32)


class ArcFaceNet:
    def setPreferableBackend(self, backend):
        self.backend = backend

    def setPreferableTarget(self, target):
        self.target = target

    def setInput(self, blob):
        self.blob = blob

    def forward(self):
        return np.array([[3.0, 4.0] + [0.0] * 510], dtype=np.float32)


class EngineTests(unittest.TestCase):
    def build_engine(self):
        detector, recognizer = Detector(), Recognizer()
        patch("src.blamera_pc.face_engine.verify_models").start()
        detector_type = patch("cv2.FaceDetectorYN").start()
        recognizer_type = patch("cv2.FaceRecognizerSF").start()
        network = patch("cv2.dnn.readNetFromONNX").start()
        network.return_value = ArcFaceNet()
        self.addCleanup(patch.stopall)
        detector_type.create.return_value = detector
        recognizer_type.create.return_value = recognizer
        return OpenCvFaceEngine(load_configuration()), detector, recognizer

    def test_empty_malformed_or_outside_detection_outputs_do_not_create_candidates(self):
        engine, detector, _ = self.build_engine()
        image = image_bytes(size=(200, 100))
        with patch.object(detector, "detect", return_value=(0, None)):
            self.assertEqual(engine.detect(image), [])
        with patch.object(
            detector, "detect", return_value=(1, np.zeros((1, 14), dtype=np.float32))
        ):
            with self.assertRaises(GalleryError):
                engine.detect(image)
        with patch.object(
            detector, "detect", return_value=(1, np.zeros((1, 15), dtype=np.float32))
        ):
            self.assertEqual(engine.detect(image), [])
        with self.assertRaises(GalleryError):
            engine.detect(b"broken")

    def test_more_than_twenty_candidates_are_bounded_and_profile_dimension_is_checked(self):
        engine, detector, _ = self.build_engine()
        image = image_bytes(size=(200, 100))
        row = detector.detect(np.zeros((640, 640, 3), dtype=np.uint8))[1]
        with patch.object(detector, "detect", return_value=(1, np.repeat(row, 25, axis=0))):
            self.assertEqual(len(engine.detect(image)), 20)
        config = replace(load_configuration(), profile=FaceProfile("other", 128))
        with self.assertRaises(GalleryError):
            OpenCvFaceEngine(config)

    def test_invalid_alignment_is_refused_before_extracting_an_embedding(self):
        engine, _, recognizer = self.build_engine()
        image = image_bytes(size=(200, 100))
        candidate = engine.detect(image)[0]
        with (
            patch.object(
                recognizer, "alignCrop", return_value=np.zeros((10, 10, 3), dtype=np.uint8)
            ),
            patch.object(recognizer, "feature") as feature,
        ):
            with self.assertRaises(GalleryError):
                engine.encode(image, candidate)
            feature.assert_not_called()
        with self.assertRaises(GalleryError):
            engine.encode(b"broken", candidate)

    def test_non_square_letterbox_remaps_box_and_five_points_before_alignment(self):
        detector, recognizer = Detector(), Recognizer()
        with (
            patch("src.blamera_pc.face_engine.verify_models"),
            patch("cv2.FaceDetectorYN") as detector_type,
            patch("cv2.FaceRecognizerSF") as recognizer_type,
            patch("cv2.dnn.readNetFromONNX", return_value=ArcFaceNet()),
        ):
            detector_type.create.return_value = detector
            recognizer_type.create.return_value = recognizer
            engine = OpenCvFaceEngine(load_configuration())
            image = image_bytes(size=(200, 100))
            faces = engine.detect(image)
            self.assertEqual(len(faces), 1)
            np.testing.assert_allclose(
                faces[0].values[:14], (10, 20, 40, 40, 20, 30, 40, 30, 30, 40, 20, 50, 40, 50)
            )
            self.assertEqual(detector.image.shape, (640, 640, 3))
            self.assertEqual(tuple(detector.image[200, 100]), (0, 0, 255))
            self.assertEqual(tuple(detector.image[10, 10]), (0, 0, 0))
            sample = engine.encode(image, faces[0])
            self.assertEqual(recognizer.image.shape, (100, 200, 3))
            np.testing.assert_allclose(recognizer.row, faces[0].values)
            self.assertEqual(sample.vector[:2], (0.6, 0.8))
            self.assertEqual(len(sample.vector), 512)

    def test_missing_models_refused_before_native_load(self):
        from src.blamera_pc.gallery import GalleryError

        with (
            patch("src.blamera_pc.face_engine.verify_models", side_effect=GalleryError("Missing")),
            patch("cv2.FaceDetectorYN") as create,
        ):
            with self.assertRaises(GalleryError):
                OpenCvFaceEngine(load_configuration())
            create.assert_not_called()

    def test_arcface_uses_rgb_centered_blob_and_cpu_without_sface_feature(self):
        import cv2

        engine, _, recognizer = self.build_engine()
        image = image_bytes(size=(200, 100))
        with patch.object(recognizer, "feature", side_effect=AssertionError("SFace unused")):
            sample = engine.encode(image, engine.detect(image)[0])
        self.assertEqual(len(sample.vector), 512)
        self.assertEqual(engine._encoder.blob.shape, (1, 3, 112, 112))
        np.testing.assert_allclose(engine._encoder.blob[0, :, 0, 0], [1.0, -1.0, -1.0])
        self.assertEqual(engine._encoder.backend, cv2.dnn.DNN_BACKEND_OPENCV)
        self.assertEqual(engine._encoder.target, cv2.dnn.DNN_TARGET_CPU)

    def test_arcface_refuses_bad_output_shape_nonfinite_and_zero_vectors(self):
        engine, _, _ = self.build_engine()
        image = image_bytes(size=(200, 100))
        candidate = engine.detect(image)[0]
        for output in [
            np.ones((2, 256)),
            np.ones((512,)),
            np.ones((1, 128)),
            np.full((1, 512), np.nan),
            np.full((1, 512), np.inf),
            np.zeros((1, 512)),
        ]:
            with (
                self.subTest(shape=output.shape),
                patch.object(engine._encoder, "forward", return_value=output),
            ):
                with self.assertRaises(GalleryError):
                    engine.encode(image, candidate)

    def test_native_load_and_inference_errors_are_safe_and_recoverable(self):
        import cv2

        with (
            patch("src.blamera_pc.face_engine.verify_models"),
            patch("cv2.dnn.readNetFromONNX", side_effect=cv2.error("synthetic")),
        ):
            with self.assertRaisesRegex(GalleryError, "Chargement"):
                OpenCvFaceEngine(load_configuration())
        engine, _, _ = self.build_engine()
        image = image_bytes(size=(200, 100))
        candidate = engine.detect(image)[0]
        with patch.object(engine._encoder, "forward", side_effect=cv2.error("synthetic")):
            with self.assertRaisesRegex(GalleryError, "Préparation"):
                engine.encode(image, candidate)
        self.assertEqual(len(engine.encode(image, candidate).vector), 512)
