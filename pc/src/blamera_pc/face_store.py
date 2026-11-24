"""Persistance des détections et références liées à leur photo/profil."""

import hashlib
import json
import math
import sqlite3
import struct
from collections.abc import Iterator
from contextlib import contextmanager
from uuid import uuid4

from .face_data import (
    FaceCandidate,
    FaceProfile,
    FaceReference,
    FaceSample,
    FaceState,
    normalize_vector,
    validate_candidates,
    validate_crop,
)
from .gallery import GalleryError, Photo
from .gallery_store import GalleryStore


class FaceReferences:
    def __init__(self, gallery: GalleryStore):
        self.gallery = gallery

    def read(self, photo: Photo, profile: FaceProfile) -> FaceState | None:
        with self.gallery._connection() as connection:
            return self._read(connection, photo, profile)

    def generation(self, photo: Photo) -> str | None:
        with self.gallery._connection() as connection:
            row = connection.execute(
                "SELECT generation FROM face_detections WHERE photo_id=?", (photo.id,)
            ).fetchone()
            return row["generation"] if row is not None else None

    def detections(
        self,
        photo: Photo,
        profile: FaceProfile,
        candidates: tuple[FaceCandidate, ...],
        *,
        expected_generation: str | None,
    ) -> None:
        validate_candidates(photo, candidates)
        serialized = json.dumps([candidate.values for candidate in candidates])
        with self._write(photo) as connection:
            row = connection.execute(
                "SELECT generation FROM face_detections WHERE photo_id=?", (photo.id,)
            ).fetchone()
            if (row["generation"] if row is not None else None) != expected_generation:
                raise GalleryError("Une préparation plus récente existe déjà pour cette photo.")
            connection.execute("DELETE FROM face_detections WHERE photo_id=?", (photo.id,))
            connection.execute(
                "INSERT INTO face_detections VALUES (?, ?, ?, ?, ?, ?, NULL, ?)",
                (
                    photo.id,
                    profile.key,
                    self._digest(photo),
                    photo.person_id,
                    photo.revision,
                    serialized,
                    uuid4().hex,
                ),
            )

    def select(self, photo: Photo, profile: FaceProfile, index: int, *, generation: str) -> None:
        with self._write(photo) as connection:
            state = self._required(connection, photo, profile)
            self._generation(state, generation)
            self._choice(index, state)
            connection.execute(
                "UPDATE face_detections SET selected=? WHERE photo_id=?", (index, photo.id)
            )

    def confirm(
        self, photo: Photo, profile: FaceProfile, index: int, sample: FaceSample, *, generation: str
    ) -> None:
        vector = normalize_vector(sample.vector, profile.dimension)
        validate_crop(sample.crop)
        packed = struct.pack(f"<{profile.dimension}f", *vector)
        with self._write(photo) as connection:
            state = self._required(connection, photo, profile)
            self._generation(state, generation)
            self._choice(index, state)
            connection.execute(
                "INSERT OR REPLACE INTO face_references VALUES (?, ?, ?, ?, ?, ?)",
                (photo.id, profile.key, profile.dimension, index, sample.crop, packed),
            )
            connection.execute(
                "UPDATE face_detections SET selected=? WHERE photo_id=?", (index, photo.id)
            )

    @staticmethod
    def _digest(photo: Photo) -> str:
        return hashlib.sha256(photo.image).hexdigest()

    @contextmanager
    def _write(self, photo: Photo) -> Iterator[sqlite3.Connection]:
        with self.gallery._connection() as connection:
            connection.execute("BEGIN IMMEDIATE")
            row = connection.execute(
                "SELECT person_id, image, revision FROM photos WHERE id=?", (photo.id,)
            ).fetchone()
            if (
                row is None
                or row["person_id"] != photo.person_id
                or row["revision"] != photo.revision
                or hashlib.sha256(row["image"]).hexdigest() != self._digest(photo)
            ):
                raise GalleryError("La photo a changé ; recommencez la préparation.")
            yield connection

    def _read(
        self, connection: sqlite3.Connection, photo: Photo, profile: FaceProfile
    ) -> FaceState | None:
        row = connection.execute(
            "SELECT * FROM face_detections WHERE photo_id=?", (photo.id,)
        ).fetchone()
        if (
            row is None
            or row["profile"] != profile.key
            or row["source"] != self._digest(photo)
            or row["owner"] != photo.person_id
            or row["revision"] != photo.revision
        ):
            return None
        try:
            state = self._decode(row, photo)
            reference = self._reference(connection, photo.id, profile, state)
            return FaceState(state.candidates, state.selected, reference, state.generation)
        except (ValueError, TypeError, KeyError, struct.error):
            raise GalleryError(
                "Référence de visage invalide ; préparez à nouveau cette photo."
            ) from None

    @staticmethod
    def _decode(row: sqlite3.Row, photo: Photo) -> FaceState:
        if len(row["candidates"]) > 20000:
            raise GalleryError("Détections de visage invalides.")
        raw = json.loads(row["candidates"])
        if not isinstance(raw, list):
            raise GalleryError("Détections de visage invalides.")
        candidates = tuple(FaceCandidate(tuple(values)) for values in raw)
        validate_candidates(photo, candidates)
        if not isinstance(row["generation"], str) or len(row["generation"]) != 32:
            raise GalleryError("Génération de référence invalide.")
        state = FaceState(candidates, row["selected"], generation=row["generation"])
        if state.selected is not None:
            FaceReferences._choice(state.selected, state)
        return state

    @staticmethod
    def _choice(index: int, state: FaceState) -> None:
        if (
            isinstance(index, bool)
            or not isinstance(index, int)
            or not 0 <= index < len(state.candidates)
        ):
            raise GalleryError("Choisissez un visage détecté.")

    @staticmethod
    def _generation(state: FaceState, expected: str) -> None:
        if state.generation != expected:
            raise GalleryError("Les visages ont été préparés à nouveau ; refaites votre choix.")

    @staticmethod
    def _reference(
        connection: sqlite3.Connection, photo_id: str, profile: FaceProfile, state: FaceState
    ) -> FaceReference | None:
        row = connection.execute(
            "SELECT * FROM face_references WHERE photo_id=?", (photo_id,)
        ).fetchone()
        if row is None:
            return None
        if row["profile"] != profile.key or row["dimension"] != profile.dimension:
            raise GalleryError("Profil de référence incompatible ; préparez à nouveau la photo.")
        FaceReferences._choice(row["candidate"], state)
        vector = tuple(struct.unpack(f"<{profile.dimension}f", row["vector"]))
        normalize_vector(vector, profile.dimension)
        if not math.isclose(math.hypot(*vector), 1.0, abs_tol=0.001):
            raise GalleryError("Norme de référence invalide.")
        validate_crop(row["crop"])
        return FaceReference(row["candidate"], FaceSample(row["crop"], vector))

    def _required(
        self, connection: sqlite3.Connection, photo: Photo, profile: FaceProfile
    ) -> FaceState:
        state = self._read(connection, photo, profile)
        if state is None:
            raise GalleryError("Préparez les visages de cette photo avant de confirmer.")
        return state
