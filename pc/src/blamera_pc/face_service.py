"""Préparation explicite ; aucune reconnaissance d'identité dans cet incrément."""

from .face_data import FaceEngine, FaceSample, normalize_vector
from .face_store import FaceReferences
from .gallery import GalleryError, Photo


def prepare_faces(references: FaceReferences, photo: Photo, engine: FaceEngine) -> None:
    generation = references.generation(photo)
    candidates = tuple(engine.detect(photo.image))
    references.detections(photo, engine.profile, candidates, expected_generation=generation)


def confirm_face(references: FaceReferences, photo: Photo, index: int, engine: FaceEngine) -> None:
    state = references.read(photo, engine.profile)
    if state is None or isinstance(index, bool) or not 0 <= index < len(state.candidates):
        raise GalleryError("Choisissez un visage préparé avant de confirmer.")
    sample = engine.encode(photo.image, state.candidates[index])
    normalized = FaceSample(sample.crop, normalize_vector(sample.vector, engine.profile.dimension))
    references.confirm(photo, engine.profile, index, normalized, generation=state.generation)
