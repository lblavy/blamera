"""Corpus de comparaison séparé des références, sous la racine privée locale."""

import hashlib
import json
import sqlite3
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import cast
from uuid import uuid4

from .evaluation_data import Policy
from .evaluation_selection import reference_selection
from .gallery import GalleryError, NormalizedImage, Photo
from .gallery_store import GalleryStore


@dataclass(frozen=True)
class Case:
    photo: Photo
    expected: str | None
    split: str
    session: str


def source_key(photo: Photo) -> str:
    return hashlib.sha256(photo.image).hexdigest() + f":{photo.person_id}:{photo.revision}"


def session_name(value: str) -> tuple[str, str]:
    if any(unicodedata.category(char).startswith("C") for char in value):
        raise GalleryError("Nom de séance invalide.")
    cleaned = " ".join(value.split())
    if not cleaned or len(cleaned) > 80:
        raise GalleryError("Indiquez une séance de 1 à 80 caractères.")
    return cleaned, unicodedata.normalize("NFKC", cleaned).casefold()


class EvaluationStore(GalleryStore):
    def set_reference_selection(self, photo_ids: list[str] | None) -> None:
        selection = reference_selection(photo_ids)
        with self._connection() as connection:
            connection.execute(
                "INSERT OR REPLACE INTO settings VALUES ('reference_selection', ?)",
                (json.dumps(selection),),
            )

    def reference_selection(self) -> tuple[str, ...] | None:
        with self._connection() as connection:
            return self.read_reference_selection(connection)

    @staticmethod
    def read_reference_selection(connection: sqlite3.Connection) -> tuple[str, ...] | None:
        row = connection.execute(
            "SELECT payload FROM settings WHERE key='reference_selection'"
        ).fetchone()
        if row is None:
            return None
        try:
            return reference_selection(json.loads(row[0]))
        except (TypeError, ValueError):
            raise GalleryError("Sélection de références illisible ; réenregistrez-la.") from None

    def policy(self) -> Policy:
        with self._connection() as connection:
            return self.read_policy(connection)

    def set_policy(self, policy: Policy) -> None:
        policy.validate()
        with self._connection() as connection:
            connection.execute(
                "INSERT OR REPLACE INTO settings VALUES ('policy', ?)",
                (json.dumps([policy.reject, policy.accept, policy.margin]),),
            )

    @staticmethod
    def read_policy(connection: sqlite3.Connection) -> Policy:
        row = connection.execute("SELECT payload FROM settings WHERE key='policy'").fetchone()
        if row is None:
            return Policy()
        try:
            values = json.loads(row[0])
            if not isinstance(values, list) or len(values) != 3:
                raise ValueError
            policy = Policy(*values)
            policy.validate()
            return policy
        except (ValueError, TypeError):
            raise GalleryError(
                "Seuils enregistrés illisibles ; réenregistrez les paramètres."
            ) from None

    def __init__(self, directory: Path):
        super().__init__(directory / "evaluation")
        with self._connection() as connection:
            connection.execute("BEGIN IMMEDIATE")
            connection.execute("CREATE TABLE IF NOT EXISTS eval_meta (version INTEGER NOT NULL)")
            row = connection.execute("SELECT version FROM eval_meta").fetchone()
            if row is not None and row[0] != 1:
                raise GalleryError("Version du corpus d'évaluation incompatible.")
            if row is None:
                connection.execute("INSERT INTO eval_meta VALUES (1)")
            connection.execute(
                "INSERT OR IGNORE INTO people VALUES ('evaluation', 'Évaluation', 'évaluation')"
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS cases (photo_id TEXT PRIMARY KEY REFERENCES photos(id) "
                "ON DELETE CASCADE, expected TEXT, split TEXT NOT NULL, session TEXT NOT NULL, session_key TEXT NOT NULL)"
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS reference_sessions (photo_id TEXT PRIMARY KEY, source TEXT NOT NULL, "
                "session TEXT NOT NULL, session_key TEXT NOT NULL)"
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS reports (id INTEGER PRIMARY KEY, fingerprint TEXT NOT NULL, payload TEXT NOT NULL)"
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, payload TEXT NOT NULL)"
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS final_runs (context TEXT PRIMARY KEY, payload TEXT NOT NULL)"
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS target_absences (photo_id TEXT PRIMARY KEY REFERENCES photos(id) "
                "ON DELETE CASCADE, profile TEXT NOT NULL, source TEXT NOT NULL, "
                "annotation TEXT NOT NULL, generation TEXT NOT NULL)"
            )

    def add_case(
        self,
        image: NormalizedImage,
        expected: str | None,
        split: str,
        session: str,
        forbidden_digests: set[str],
        reference_sessions: set[str],
    ) -> None:
        cleaned, key = self._metadata(split, session, reference_sessions)
        if image.digest in forbidden_digests:
            raise GalleryError("Cette image est déjà une référence ; choisissez une autre photo.")
        with self._connection() as connection:
            connection.execute("BEGIN IMMEDIATE")
            self._check_split(connection, key, split)
            if connection.execute(
                "SELECT 1 FROM photos WHERE digest=?", (image.digest,)
            ).fetchone():
                raise GalleryError("Cette image existe déjà dans le corpus d'évaluation.")
            identifier = uuid4().hex
            connection.execute(
                "INSERT INTO photos(id, person_id, image, digest, width, height) VALUES (?, 'evaluation', ?, ?, ?, ?)",
                (identifier, image.image, image.digest, image.width, image.height),
            )
            connection.execute(
                "INSERT INTO cases VALUES (?, ?, ?, ?, ?)",
                (identifier, expected, split, cleaned, key),
            )

    def cases(self) -> list[Case]:
        with self._connection() as connection:
            return self.read_cases(connection)

    @staticmethod
    def read_cases(connection: sqlite3.Connection) -> list[Case]:
        rows = connection.execute(
            "SELECT p.*, c.expected, c.split, c.session FROM photos p JOIN cases c ON c.photo_id=p.id ORDER BY p.rowid"
        )
        return [
            Case(
                Photo(
                    row["id"],
                    row["person_id"],
                    row["image"],
                    row["width"],
                    row["height"],
                    row["revision"],
                ),
                row["expected"],
                row["split"],
                row["session"],
            )
            for row in rows
        ]

    def update_case(
        self,
        case_id: str,
        expected: str | None,
        split: str,
        session: str,
        reference_sessions: set[str],
    ) -> None:
        cleaned, key = self._metadata(split, session, reference_sessions)
        with self._connection() as connection:
            connection.execute("BEGIN IMMEDIATE")
            self._check_split(connection, key, split, case_id)
            connection.execute(
                "DELETE FROM target_absences WHERE photo_id=? AND EXISTS "
                "(SELECT 1 FROM cases WHERE photo_id=? AND "
                "(expected IS NOT ? OR split<>? OR session<>?))",
                (case_id, case_id, expected, split, cleaned),
            )
            changed = connection.execute(
                "UPDATE cases SET expected=?, split=?, session=?, session_key=? WHERE photo_id=?",
                (expected, split, cleaned, key, case_id),
            )
            if changed.rowcount != 1:
                raise GalleryError("Ce cas d'évaluation n'existe plus.")

    def assign_sessions(self, photos: list[Photo], session: str) -> None:
        cleaned, key = session_name(session)
        if not photos:
            raise GalleryError("Choisissez au moins une photo de référence.")
        with self._connection() as connection:
            connection.execute("BEGIN IMMEDIATE")
            if connection.execute("SELECT 1 FROM cases WHERE session_key=?", (key,)).fetchone():
                raise GalleryError("Cette séance appartient déjà au corpus d'évaluation.")
            connection.executemany(
                "INSERT OR REPLACE INTO reference_sessions VALUES (?, ?, ?, ?)",
                [(photo.id, source_key(photo), cleaned, key) for photo in photos],
            )

    def reference_session(self, photo: Photo) -> str:
        with self._connection() as connection:
            return self.read_session(connection, photo)

    @staticmethod
    def read_session(connection: sqlite3.Connection, photo: Photo) -> str:
        row = connection.execute(
            "SELECT session FROM reference_sessions WHERE photo_id=? AND source=?",
            (photo.id, source_key(photo)),
        ).fetchone()
        return row[0] if row is not None else ""

    def save_report(self, fingerprint: str, report: dict[str, object]) -> None:
        payload = json.dumps(report, allow_nan=False, sort_keys=True, separators=(",", ":"))
        with self._connection() as connection:
            connection.execute(
                "INSERT OR REPLACE INTO reports VALUES (1, ?, ?)", (fingerprint, payload)
            )

    def report(self, fingerprint: str) -> dict[str, object] | None:
        with self._connection() as connection:
            return self.read_report(connection, fingerprint)

    @staticmethod
    def read_report(connection: sqlite3.Connection, fingerprint: str) -> dict[str, object] | None:
        row = connection.execute(
            "SELECT payload FROM reports WHERE id=1 AND fingerprint=?", (fingerprint,)
        ).fetchone()
        if row is None:
            return None
        try:
            raw = json.loads(row[0])
            if not isinstance(raw, dict) or raw.get("schema") not in (None, 1):
                raise ValueError
            return cast(dict[str, object], raw)
        except (ValueError, TypeError):
            raise GalleryError("Rapport illisible ; relancez l'évaluation.") from None

    @staticmethod
    def _metadata(split: str, session: str, reference_sessions: set[str]) -> tuple[str, str]:
        cleaned, key = session_name(session)
        if split not in {"calibration", "test"}:
            raise GalleryError("Lot d'évaluation invalide.")
        if key in reference_sessions:
            raise GalleryError("Cette séance appartient déjà aux références.")
        return cleaned, key

    @staticmethod
    def _check_split(
        connection: sqlite3.Connection, key: str, split: str, case_id: str = ""
    ) -> None:
        if connection.execute(
            "SELECT 1 FROM cases WHERE session_key=? AND split<>? AND photo_id<>?",
            (key, split, case_id),
        ).fetchone():
            raise GalleryError("Une même séance ne peut pas traverser Calibration et Test final.")
