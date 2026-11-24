"""Import de lots, résultat individuel et poursuite après un fichier refusé."""

from collections.abc import Sequence
from dataclasses import dataclass

from .gallery import GalleryError
from .gallery_store import GalleryStore
from .image_import import MAX_BATCH, normalize_image


@dataclass(frozen=True)
class PhotoUpload:
    name: str
    content: bytes


@dataclass(frozen=True)
class ImportOutcome:
    name: str
    message: str
    added: bool


def import_batch(
    store: GalleryStore, person_id: str, files: Sequence[PhotoUpload]
) -> list[ImportOutcome]:
    if len(files) > MAX_BATCH:
        return [ImportOutcome("Lot", "Maximum 20 photos par import ; aucune photo ajoutée.", False)]
    outcomes = []
    for upload in files:
        name = upload.name.replace("\\", "/").rsplit("/", 1)[-1][:120]
        try:
            added = store.add_photo(person_id, normalize_image(upload.content))
            message = "Ajoutée" if added else "Déjà présente : ignorée"
            outcomes.append(ImportOutcome(name, message, added))
        except GalleryError as error:
            outcomes.append(ImportOutcome(name, str(error), False))
    return outcomes
