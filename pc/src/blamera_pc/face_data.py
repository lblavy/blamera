"""Contrats privés des visages de référence, sans décision d'identité."""

import math
from dataclasses import dataclass
from io import BytesIO
from numbers import Real
from typing import Protocol

from PIL import Image, UnidentifiedImageError

from .gallery import GalleryError, Photo


@dataclass(frozen=True)
class FaceProfile:
    key: str
    dimension: int = 128


@dataclass(frozen=True)
class FaceCandidate:
    values: tuple[float, ...]


@dataclass(frozen=True)
class FaceSample:
    crop: bytes
    vector: tuple[float, ...]


@dataclass(frozen=True)
class FaceReference:
    candidate: int
    sample: FaceSample


@dataclass(frozen=True)
class FaceState:
    candidates: tuple[FaceCandidate, ...]
    selected: int | None = None
    reference: FaceReference | None = None
    generation: str = ""


class FaceEngine(Protocol):
    profile: FaceProfile

    def detect(self, image: bytes) -> list[FaceCandidate]: ...

    def encode(self, image: bytes, candidate: FaceCandidate) -> FaceSample: ...


def normalize_vector(values: tuple[float, ...], dimension: int) -> tuple[float, ...]:
    if not 1 <= dimension <= 4096 or len(values) != dimension:
        raise GalleryError("Empreinte de visage incompatible.")
    if any(
        isinstance(value, bool) or not isinstance(value, Real) or not math.isfinite(value)
        for value in values
    ):
        raise GalleryError("Empreinte de visage invalide.")
    norm = math.hypot(*values)
    if not math.isfinite(norm) or norm <= 0:
        raise GalleryError("Empreinte de visage vide.")
    return tuple(float(value / norm) for value in values)


def validate_candidates(photo: Photo, candidates: tuple[FaceCandidate, ...]) -> None:
    if len(candidates) > 20:
        raise GalleryError("Trop de visages candidats. Choisissez un cadrage plus simple.")
    for candidate in candidates:
        _validate_candidate(photo, candidate.values)


def _validate_candidate(photo: Photo, values: tuple[float, ...]) -> None:
    if len(values) != 15 or any(
        isinstance(v, bool) or not isinstance(v, Real) or not math.isfinite(v) for v in values
    ):
        raise GalleryError("Détection de visage invalide.")
    x, y, width, height = values[:4]
    if (
        x < 0
        or y < 0
        or width <= 0
        or height <= 0
        or x + width > photo.width + 0.001
        or y + height > photo.height + 0.001
    ):
        raise GalleryError("Visage hors des limites de la photo.")
    points = tuple(zip(values[4:14:2], values[5:14:2]))
    if len(set(points)) < 3 or not 0 <= values[14] <= 1:
        raise GalleryError("Repères de visage invalides.")
    if any(not 0 <= px <= photo.width or not 0 <= py <= photo.height for px, py in points):
        raise GalleryError("Repères de visage hors de la photo.")


def validate_crop(content: bytes) -> None:
    if len(content) > 1024 * 1024:
        raise GalleryError("Aperçu de visage invalide.")
    try:
        with Image.open(BytesIO(content), formats=["PNG"]) as image:
            if image.size != (112, 112) or image.mode != "RGB":
                raise GalleryError("Aperçu de visage incompatible.")
            image.load()
    except (UnidentifiedImageError, OSError, ValueError):
        raise GalleryError("Aperçu de visage illisible.") from None
