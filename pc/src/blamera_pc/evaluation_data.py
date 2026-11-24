"""Règles de comparaison exploratoires ; aucune inférence ni identité forcée."""

import math
from dataclasses import dataclass
from numbers import Real

from .face_data import normalize_vector
from .gallery import GalleryError


@dataclass(frozen=True)
class Policy:
    reject: float = 0.3
    accept: float = 0.6
    margin: float = 0.05

    def validate(self) -> None:
        values = (self.reject, self.accept, self.margin)
        if any(
            isinstance(value, bool) or not isinstance(value, Real) or not math.isfinite(value)
            for value in values
        ):
            raise GalleryError("Seuils de comparaison invalides.")
        if not -1 <= self.reject < self.accept <= 1 or not 0 <= self.margin <= 2:
            raise GalleryError("Respectez rejet < acceptation et une marge entre 0 et 2.")


@dataclass(frozen=True)
class Decision:
    status: str
    identity: str | None
    candidate: str
    score: float
    second: float | None


def prototypes(
    samples: dict[str, list[tuple[float, ...]]], dimension: int
) -> dict[str, tuple[float, ...]]:
    if not samples or any(not vectors for vectors in samples.values()):
        raise GalleryError("Aucune référence confirmée pour une personne.")
    result = {}
    for identity, vectors in samples.items():
        normalized = [normalize_vector(vector, dimension) for vector in vectors]
        mean = tuple(math.fsum(values) / len(normalized) for values in zip(*normalized))
        result[identity] = normalize_vector(mean, dimension)
    return result


def decide(
    vector: tuple[float, ...], identities: dict[str, tuple[float, ...]], policy: Policy
) -> Decision:
    policy.validate()
    if not identities:
        raise GalleryError("Préparez au moins une référence confirmée.")
    source = normalize_vector(vector, len(vector))
    scores = []
    for identity, prototype in identities.items():
        target = normalize_vector(prototype, len(source))
        score = min(1.0, max(-1.0, math.fsum(a * b for a, b in zip(source, target))))
        scores.append((identity, score))
    scores.sort(key=lambda item: (-item[1], item[0]))
    candidate, score = scores[0]
    second = scores[1][1] if len(scores) > 1 else None
    if score < policy.reject:
        status = "Inconnu"
    elif score < policy.accept or (second is not None and score - second < policy.margin):
        status = "Incertain"
    else:
        status = "Reconnu"
    return Decision(status, candidate if status == "Reconnu" else None, candidate, score, second)
