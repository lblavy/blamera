"""Migration additive, transactionnelle et commune à la galerie privée."""

import sqlite3


def migrate_faces(connection: sqlite3.Connection) -> None:
    if not connection.in_transaction:
        connection.execute("BEGIN")
    connection.execute("ALTER TABLE photos ADD COLUMN revision INTEGER NOT NULL DEFAULT 0")
    connection.execute(
        "CREATE TABLE face_detections (photo_id TEXT PRIMARY KEY REFERENCES photos(id) ON DELETE CASCADE, "
        "profile TEXT NOT NULL, source TEXT NOT NULL, owner TEXT NOT NULL, revision INTEGER NOT NULL, "
        "candidates TEXT NOT NULL, selected INTEGER, generation TEXT NOT NULL)"
    )
    connection.execute(
        "CREATE TABLE face_references (photo_id TEXT PRIMARY KEY REFERENCES face_detections(photo_id) "
        "ON DELETE CASCADE, profile TEXT NOT NULL, dimension INTEGER NOT NULL, "
        "candidate INTEGER NOT NULL, crop BLOB NOT NULL, vector BLOB NOT NULL)"
    )
    connection.execute("PRAGMA user_version=2")
