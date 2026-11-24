"""Constat humain du sujet attendu absent des détections."""

import json
import sqlite3

from .evaluation_store import Case, EvaluationStore, source_key
from .face_data import FaceProfile, FaceState
from .face_store import FaceReferences
from .gallery import GalleryError


def annotation_key(case: Case) -> str:
    return json.dumps([case.expected, case.split, case.session], ensure_ascii=False)


class TargetAbsences:
    def __init__(self, evaluation: EvaluationStore):
        self.evaluation = evaluation

    def _current(
        self, connection: sqlite3.Connection, case: Case, profile: FaceProfile, generation: str
    ) -> FaceState:
        current = connection.execute(
            "SELECT expected,split,session FROM cases WHERE photo_id=?", (case.photo.id,)
        ).fetchone()
        if current is None or tuple(current) != (case.expected, case.split, case.session):
            raise GalleryError("L'annotation du sujet a changé ; rechargez ce cas.")
        faces = FaceReferences(self.evaluation)
        state = faces._required(connection, case.photo, profile)
        faces._generation(state, generation)
        return state

    def mark(self, case: Case, profile: FaceProfile, *, generation: str) -> None:
        faces = FaceReferences(self.evaluation)
        with faces._write(case.photo) as connection:
            state = self._current(connection, case, profile, generation)
            if not state.candidates:
                raise GalleryError("Aucun visage détecté : ce cas est déjà non exploitable.")
            # A human absence decision replaces a former target choice, never its identity.
            connection.execute("DELETE FROM face_references WHERE photo_id=?", (case.photo.id,))
            connection.execute(
                "UPDATE face_detections SET selected=NULL WHERE photo_id=?", (case.photo.id,)
            )
            connection.execute(
                "INSERT OR REPLACE INTO target_absences VALUES (?, ?, ?, ?, ?)",
                (
                    case.photo.id,
                    profile.key,
                    source_key(case.photo),
                    annotation_key(case),
                    generation,
                ),
            )

    def clear(self, case: Case, profile: FaceProfile, *, generation: str) -> None:
        with FaceReferences(self.evaluation)._write(case.photo) as connection:
            self._current(connection, case, profile, generation)
            connection.execute("DELETE FROM target_absences WHERE photo_id=?", (case.photo.id,))

    @staticmethod
    def read(
        connection: sqlite3.Connection, case: Case, profile: FaceProfile, state: FaceState | None
    ) -> bool:
        if (
            state is None
            or not state.candidates
            or state.selected is not None
            or state.reference is not None
        ):
            return False
        row = connection.execute(
            "SELECT profile, source, annotation, generation FROM target_absences WHERE photo_id=?",
            (case.photo.id,),
        ).fetchone()
        return row is not None and tuple(row) == (
            profile.key,
            source_key(case.photo),
            annotation_key(case),
            state.generation,
        )
