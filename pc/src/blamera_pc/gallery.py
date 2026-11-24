"""Types et erreurs de la galerie locale."""

from dataclasses import dataclass


class GalleryError(Exception):
    """Erreur attendue, présentable sans détails privés."""


@dataclass(frozen=True)
class Person:
    id: str
    name: str
    count: int = 0


@dataclass(frozen=True)
class Photo:
    id: str
    person_id: str
    image: bytes
    width: int
    height: int
    revision: int = 0


@dataclass(frozen=True)
class NormalizedImage:
    image: bytes
    digest: str
    width: int
    height: int
