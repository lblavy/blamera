"""Politique explicite de références, indépendante de la galerie principale."""

import unicodedata

from .gallery import GalleryError


def reference_selection(value: object) -> tuple[str, ...] | None:
    if value is None:
        return None
    if not isinstance(value, list) or any(not _identifier(item) for item in value):
        raise GalleryError("Sélection de références invalide ; enregistrez une nouvelle sélection.")
    if len(set(value)) != len(value):
        raise GalleryError("Une référence ne peut pas être sélectionnée plusieurs fois.")
    return tuple(sorted(value))


def _identifier(value: object) -> bool:
    return (
        isinstance(value, str)
        and 0 < len(value) <= 128
        and value == value.strip()
        and not any(unicodedata.category(char).startswith("C") for char in value)
    )
