"""Gel reproductible et exposition unique du lot final, sans mutation de galerie."""

import hashlib
import json
import sqlite3
from datetime import datetime, timezone
from typing import cast

from .evaluation_service import (
    build_report,
    validate_evaluation,
    validate_prepared,
    validate_report,
)
from .evaluation_snapshot import Snapshot, locked_snapshot, manifest_fingerprint
from .evaluation_store import EvaluationStore
from .face_data import FaceProfile
from .gallery import GalleryError
from .gallery_store import GalleryStore


def _decode(payload: str) -> dict[str, object]:
    try:
        value = json.loads(payload)
        if not isinstance(value, dict) or value.get("schema") != 1:
            raise ValueError
        return cast(dict[str, object], value)
    except (TypeError, ValueError):
        raise GalleryError("Archive du test final illisible ; aucune donnée remplacée.") from None


def _freeze(connection: sqlite3.Connection, view: Snapshot) -> dict[str, object] | None:
    row = connection.execute("SELECT payload FROM settings WHERE key='final_freeze'").fetchone()
    if row is None:
        return None
    frozen = _decode(row[0])
    if frozen.get("fingerprint") != view.calibration_fingerprint:
        return None
    manifest = frozen.get("manifest")
    if (
        not isinstance(manifest, dict)
        or manifest_fingerprint(manifest) != view.calibration_fingerprint
        or any(frozen.get(key) != manifest.get(key) for key in ("policy", "profile", "rule"))
    ):
        raise GalleryError("Gel du test final invalide ; aucune donnée remplacée.")
    return frozen


def current_freeze(
    gallery: GalleryStore, evaluation: EvaluationStore, profile: FaceProfile
) -> dict[str, object] | None:
    with locked_snapshot(gallery, evaluation, profile) as (view, connection):
        return _freeze(connection, view)


def _test_sources(view: Snapshot) -> set[str]:
    return {
        hashlib.sha256(probe.case.photo.image).hexdigest()
        for probe in view.probes
        if probe.case.split == "test"
    }


def _reject_reuse(connection: sqlite3.Connection, view: Snapshot) -> None:
    sources = _test_sources(view)
    for row in connection.execute("SELECT context, payload FROM final_runs"):
        report = _decode(row["payload"])
        previous = report.get("test_sources")
        if not isinstance(previous, list) or not all(isinstance(item, str) for item in previous):
            raise GalleryError("Archive du test final invalide ; aucune donnée remplacée.")
        if row["context"] != view.calibration_fingerprint and sources.intersection(previous):
            raise GalleryError(
                "Ces photos ont déjà été évaluées. Utilisez un nouveau lot indépendant pour un nouveau gel."
            )


def freeze_final_test(
    gallery: GalleryStore, evaluation: EvaluationStore, profile: FaceProfile
) -> dict[str, object]:
    with locked_snapshot(gallery, evaluation, profile, write=True) as (view, connection):
        validate_evaluation(view)
        if not _test_sources(view):
            raise GalleryError("Ajoutez un lot de Test final avant de figer les paramètres.")
        calibration = evaluation.read_report(connection, view.calibration_fingerprint)
        if calibration is None:
            raise GalleryError("Évaluez la calibration avec les paramètres actuels avant le gel.")
        validate_report(calibration, view.calibration_fingerprint)
        _reject_reuse(connection, view)
        existing = _freeze(connection, view)
        if existing is not None:
            return existing
        frozen = {
            "schema": 1,
            "fingerprint": view.calibration_fingerprint,
            "manifest": view.calibration_manifest,
            "policy": calibration["policy"],
            "profile": calibration["profile"],
            "rule": calibration["rule"],
            "created_utc": datetime.now(timezone.utc).isoformat(),
        }
        connection.execute(
            "INSERT OR REPLACE INTO settings VALUES ('final_freeze', ?)",
            (json.dumps(frozen, sort_keys=True, allow_nan=False),),
        )
        return cast(dict[str, object], json.loads(json.dumps(frozen)))


def _final_report(connection: sqlite3.Connection, view: Snapshot) -> dict[str, object] | None:
    row = connection.execute(
        "SELECT payload FROM final_runs WHERE context=?", (view.calibration_fingerprint,)
    ).fetchone()
    if row is None:
        return None
    report = _decode(row[0])
    if report.get("fingerprint") != view.fingerprint:
        return None
    validate_report(report, view.fingerprint)
    if report.get("split") != "test":
        raise GalleryError("Archive du test final invalide ; aucune donnée remplacée.")
    return report


def current_final_report(
    gallery: GalleryStore, evaluation: EvaluationStore, profile: FaceProfile
) -> dict[str, object] | None:
    with locked_snapshot(gallery, evaluation, profile) as (view, connection):
        if _freeze(connection, view) is None:
            return None
        return _final_report(connection, view)


def run_final_test(
    gallery: GalleryStore, evaluation: EvaluationStore, profile: FaceProfile
) -> dict[str, object]:
    with locked_snapshot(gallery, evaluation, profile, write=True) as (view, connection):
        frozen = _freeze(connection, view)
        if frozen is None:
            raise GalleryError("Figez les paramètres actuels avant d'évaluer le Test final.")
        validate_evaluation(view)
        tests = [probe for probe in view.probes if probe.case.split == "test"]
        validate_prepared(tests, "Test final")
        existing = _final_report(connection, view)
        if existing is not None:
            return existing
        _reject_reuse(connection, view)
        exposed = connection.execute(
            "SELECT 1 FROM final_runs WHERE context=?", (view.calibration_fingerprint,)
        ).fetchone()
        if exposed:
            raise GalleryError(
                "Le test a déjà été ouvert avec d'autres préparations ; son résultat historique est conservé."
            )
        report = build_report(view, "test")
        report.update(freeze=frozen, test_sources=sorted(_test_sources(view)))
        payload = json.dumps(report, sort_keys=True, allow_nan=False)
        connection.execute(
            "INSERT INTO final_runs VALUES (?, ?)",
            (view.calibration_fingerprint, payload),
        )
        return _decode(payload)
