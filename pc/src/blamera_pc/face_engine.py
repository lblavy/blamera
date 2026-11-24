"""Adaptateur CPU OpenCV, repères en coordonnées de la photo source."""

import threading
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path

import cv2
import numpy as np
from numpy.typing import NDArray
from PIL import Image

from .face_data import FaceCandidate, FaceSample, normalize_vector, validate_candidates
from .face_models import MODEL_DIRECTORY, FaceConfiguration, verify_models
from .gallery import GalleryError, Photo


@dataclass(frozen=True)
class DetectorFrame:
    image: NDArray[np.uint8]
    scale_x: float
    scale_y: float
    offset_x: int
    offset_y: int


def _bgr(content: bytes) -> NDArray[np.uint8]:
    with Image.open(BytesIO(content), formats=["PNG"]) as source, source.convert("RGB") as rgb:
        return np.asarray(rgb, dtype=np.uint8)[:, :, ::-1].copy()


def _frame(image: NDArray[np.uint8], size: int) -> DetectorFrame:
    height, width = image.shape[:2]
    scale = min(size / width, size / height)
    resized_width, resized_height = max(1, round(width * scale)), max(1, round(height * scale))
    resized = cv2.resize(image, (resized_width, resized_height))
    canvas = np.zeros((size, size, 3), dtype=np.uint8)
    x, y = (size - resized_width) // 2, (size - resized_height) // 2
    canvas[y : y + resized_height, x : x + resized_width] = resized
    return DetectorFrame(canvas, resized_width / width, resized_height / height, x, y)


def _remap(
    values: tuple[float, ...], frame: DetectorFrame, width: int, height: int
) -> FaceCandidate:
    result = list(values)
    left = (values[0] - frame.offset_x) / frame.scale_x
    top = (values[1] - frame.offset_y) / frame.scale_y
    right = min(float(width), left + values[2] / frame.scale_x)
    bottom = min(float(height), top + values[3] / frame.scale_y)
    result[:4] = [max(0.0, left), max(0.0, top), right - max(0.0, left), bottom - max(0.0, top)]
    for point in range(4, 14, 2):
        result[point] = (values[point] - frame.offset_x) / frame.scale_x
        result[point + 1] = (values[point + 1] - frame.offset_y) / frame.scale_y
    return FaceCandidate(tuple(result))


class OpenCvFaceEngine:
    def __init__(self, configuration: FaceConfiguration, directory: Path = MODEL_DIRECTORY):
        self.profile = configuration.profile
        if self.profile.dimension != 512:
            raise GalleryError("Profil ArcFace ResNet-50 incompatible.")
        verify_models(configuration, directory)
        self.configuration = configuration
        self._lock = threading.Lock()
        paths = {file.role: str(directory / file.name) for file in configuration.files}
        try:
            self._detector = cv2.FaceDetectorYN.create(
                paths["detector"],
                "",
                (configuration.detector_size, configuration.detector_size),
                configuration.score,
                configuration.nms,
                5000,
                cv2.dnn.DNN_BACKEND_OPENCV,
                cv2.dnn.DNN_TARGET_CPU,
            )
            # alignCrop is independent of SFace's network; retain the tested native
            # alignment without duplicating the much larger ResNet-50 network.
            self._aligner = cv2.FaceRecognizerSF.create(
                paths["aligner"], "", cv2.dnn.DNN_BACKEND_OPENCV, cv2.dnn.DNN_TARGET_CPU
            )
            self._encoder = cv2.dnn.readNetFromONNX(paths["encoder"])
            self._encoder.setPreferableBackend(cv2.dnn.DNN_BACKEND_OPENCV)
            self._encoder.setPreferableTarget(cv2.dnn.DNN_TARGET_CPU)
        except cv2.error:
            raise GalleryError("Chargement des modèles de visage impossible.") from None

    def detect(self, image: bytes) -> list[FaceCandidate]:
        try:
            source = _bgr(image)
            frame = _frame(source, self.configuration.detector_size)
            with self._lock:
                self._detector.setInputSize(
                    (self.configuration.detector_size, self.configuration.detector_size)
                )
                _, rows = self._detector.detect(frame.image)
            if rows is None:
                return []
            return self._candidates(
                np.asarray(rows, dtype=np.float32), frame, image, source.shape[1], source.shape[0]
            )
        except (cv2.error, OSError, ValueError, MemoryError):
            raise GalleryError("Détection des visages impossible. Vous pouvez réessayer.") from None

    @staticmethod
    def _candidates(
        rows: NDArray[np.float32], frame: DetectorFrame, image: bytes, width: int, height: int
    ) -> list[FaceCandidate]:
        if rows.ndim != 2 or rows.shape[1] != 15:
            raise GalleryError("Sortie du détecteur de visage invalide.")
        candidates = []
        photo = Photo("", "", image, width, height)
        for row in rows:
            candidate = _remap(tuple(float(value) for value in row), frame, width, height)
            try:
                validate_candidates(photo, (candidate,))
            except GalleryError:
                continue
            candidates.append(candidate)
        return sorted(candidates, key=lambda value: value.values[14], reverse=True)[:20]

    def encode(self, image: bytes, candidate: FaceCandidate) -> FaceSample:
        try:
            source = _bgr(image)
            validate_candidates(
                Photo("", "", image, source.shape[1], source.shape[0]), (candidate,)
            )
            with self._lock:
                aligned = self._aligner.alignCrop(
                    source, np.asarray(candidate.values, dtype=np.float32)
                )
                if aligned.shape != (112, 112, 3) or aligned.dtype != np.uint8:
                    raise GalleryError("Alignement du visage incompatible.")
                blob = cv2.dnn.blobFromImage(
                    aligned, 1 / 127.5, (112, 112), (127.5, 127.5, 127.5), swapRB=True
                )
                self._encoder.setInput(blob)
                feature = self._encoder.forward()
                if feature.shape != (1, self.profile.dimension):
                    raise GalleryError("Sortie ArcFace ResNet-50 incompatible.")
                vector = normalize_vector(
                    tuple(float(value) for value in feature.reshape(-1)), self.profile.dimension
                )
            output = BytesIO()
            with Image.fromarray(cv2.cvtColor(aligned, cv2.COLOR_BGR2RGB)) as preview:
                preview.save(output, "PNG")
            return FaceSample(output.getvalue(), vector)
        except (cv2.error, OSError, ValueError, MemoryError):
            raise GalleryError("Préparation du visage impossible. Vous pouvez réessayer.") from None
