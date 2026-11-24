"""Stockage transactionnel des petites galeries, sans connexion partagée."""

import sqlite3
import unicodedata
from collections.abc import Iterator
from contextlib import closing, contextmanager
from pathlib import Path
from uuid import uuid4

from .face_schema import migrate_faces
from .gallery import GalleryError, NormalizedImage, Person, Photo


def person_name(value: str) -> tuple[str, str]:
    name = value.strip()
    if not name or len(name) > 80 or any(unicodedata.category(c).startswith("C") for c in name):
        raise GalleryError("Le nom doit contenir de 1 à 80 caractères, sans contrôle invisible.")
    return name, unicodedata.normalize("NFKC", name).casefold()


class GalleryStore:
    def __init__(self, directory: Path):
        self.path = directory / "gallery.sqlite3"
        try:
            directory.mkdir(parents=True, exist_ok=True)
        except OSError:
            raise GalleryError("Le dossier de galerie est indisponible.") from None
        with self._connection() as connection:
            version = connection.execute("PRAGMA user_version").fetchone()[0]
            tables = connection.execute(
                "SELECT name FROM sqlite_master WHERE type='table'"
            ).fetchall()
            if version == 0 and not tables:
                self._initialize(connection)
                version = 1
            if version == 1:
                migrate_faces(connection)
            elif version != 2:
                raise GalleryError("Version de galerie incompatible ; aucune donnée remplacée.")
            connection.execute("SELECT id, name, name_key FROM people LIMIT 0")
            connection.execute(
                "SELECT id, person_id, image, digest, width, height, revision FROM photos LIMIT 0"
            )
            connection.execute(
                "SELECT photo_id, profile, source, owner, revision, candidates, selected, generation FROM face_detections LIMIT 0"
            )
            connection.execute(
                "SELECT photo_id, profile, dimension, candidate, crop, vector FROM face_references LIMIT 0"
            )

    @contextmanager
    def _connection(self) -> Iterator[sqlite3.Connection]:
        try:
            with closing(sqlite3.connect(self.path, timeout=5)) as connection, connection:
                connection.row_factory = sqlite3.Row
                connection.execute("PRAGMA foreign_keys=ON")
                yield connection
        except sqlite3.Error:
            raise GalleryError(
                "Stockage de galerie indisponible ; opération non enregistrée."
            ) from None

    @staticmethod
    def _initialize(connection: sqlite3.Connection) -> None:
        # sqlite3 starts implicit transactions for DML, not these schema statements.
        connection.execute("BEGIN")
        connection.execute(
            "CREATE TABLE people (id TEXT PRIMARY KEY, name TEXT NOT NULL, "
            "name_key TEXT NOT NULL UNIQUE)"
        )
        connection.execute(
            "CREATE TABLE photos (id TEXT PRIMARY KEY, person_id TEXT NOT NULL "
            "REFERENCES people(id) ON DELETE CASCADE, image BLOB NOT NULL, "
            "digest TEXT NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, "
            "UNIQUE(person_id, digest))"
        )
        connection.execute("PRAGMA user_version=1")

    @staticmethod
    def _person(connection: sqlite3.Connection, identifier: str) -> None:
        if connection.execute("SELECT id FROM people WHERE id=?", (identifier,)).fetchone() is None:
            raise GalleryError("Cette personne n'existe plus. Actualisez la sélection.")

    @staticmethod
    def _unique_name(connection: sqlite3.Connection, key: str, identifier: str = "") -> None:
        existing = connection.execute(
            "SELECT id FROM people WHERE name_key=? AND id<>?", (key, identifier)
        ).fetchone()
        if existing is not None:
            raise GalleryError("Une personne porte déjà ce nom.")

    def people(self) -> list[Person]:
        with self._connection() as connection:
            rows = connection.execute(
                "SELECT p.id, p.name, COUNT(f.id) AS count FROM people p "
                "LEFT JOIN photos f ON f.person_id=p.id GROUP BY p.id ORDER BY p.name_key"
            )
            return [Person(row["id"], row["name"], row["count"]) for row in rows]

    def create(self, name: str) -> str:
        cleaned, key = person_name(name)
        identifier = uuid4().hex
        with self._connection() as connection:
            self._unique_name(connection, key)
            connection.execute("INSERT INTO people VALUES (?, ?, ?)", (identifier, cleaned, key))
        return identifier

    def rename(self, person_id: str, name: str) -> None:
        cleaned, key = person_name(name)
        with self._connection() as connection:
            self._person(connection, person_id)
            self._unique_name(connection, key, person_id)
            connection.execute(
                "UPDATE people SET name=?, name_key=? WHERE id=?", (cleaned, key, person_id)
            )

    def delete_person(self, person_id: str) -> None:
        with self._connection() as connection:
            self._person(connection, person_id)
            connection.execute("DELETE FROM people WHERE id=?", (person_id,))

    def photos(self, person_id: str) -> list[Photo]:
        with self._connection() as connection:
            self._person(connection, person_id)
            rows = connection.execute(
                "SELECT * FROM photos WHERE person_id=? ORDER BY rowid", (person_id,)
            )
            return [
                Photo(
                    row["id"], person_id, row["image"], row["width"], row["height"], row["revision"]
                )
                for row in rows
            ]

    def add_photo(self, person_id: str, photo: NormalizedImage) -> bool:
        with self._connection() as connection:
            self._person(connection, person_id)
            cursor = connection.execute(
                "INSERT OR IGNORE INTO photos(id, person_id, image, digest, width, height) VALUES (?, ?, ?, ?, ?, ?)",
                (uuid4().hex, person_id, photo.image, photo.digest, photo.width, photo.height),
            )
            return cursor.rowcount == 1

    def move_photo(self, photo_id: str, destination: str) -> None:
        with self._connection() as connection:
            self._person(connection, destination)
            photo = connection.execute(
                "SELECT digest FROM photos WHERE id=?", (photo_id,)
            ).fetchone()
            if photo is None:
                raise GalleryError("Cette photo n'existe plus.")
            duplicate = connection.execute(
                "SELECT id FROM photos WHERE person_id=? AND digest=?",
                (destination, photo["digest"]),
            ).fetchone()
            if duplicate is not None:
                raise GalleryError(
                    "Cette photo existe déjà chez cette personne ; original conservé."
                )
            connection.execute(
                "UPDATE photos SET person_id=?, revision=revision+1 WHERE id=?",
                (destination, photo_id),
            )
            connection.execute("DELETE FROM face_detections WHERE photo_id=?", (photo_id,))

    def delete_photo(self, photo_id: str) -> None:
        with self._connection() as connection:
            cursor = connection.execute("DELETE FROM photos WHERE id=?", (photo_id,))
            if cursor.rowcount != 1:
                raise GalleryError("Cette photo n'existe plus.")
