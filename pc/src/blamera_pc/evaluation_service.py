"""Instantanés et rapports locaux, sans utiliser le test final pour le réglage."""

import hashlib
import json
from collections import Counter
from dataclasses import asdict
from datetime import datetime, timezone
from typing import cast

from .evaluation_data import decide, prototypes
from .evaluation_snapshot import Probe, Snapshot, locked_snapshot, manifest_fingerprint
from .evaluation_store import EvaluationStore, session_name
from .face_data import FaceProfile
from .gallery import GalleryError
from .gallery_store import GalleryStore


def run_evaluation(
    gallery: GalleryStore, evaluation: EvaluationStore, profile: FaceProfile
) -> dict[str, object]:
    with locked_snapshot(gallery, evaluation, profile, write=True) as (view, connection):
        validate_evaluation(view)
        payload = json.dumps(build_report(view), allow_nan=False, sort_keys=True)
        connection.execute(
            "INSERT OR REPLACE INTO reports VALUES (1, ?, ?)",
            (view.calibration_fingerprint, payload),
        )
        return cast(dict[str, object], json.loads(payload))


def current_report(
    gallery: GalleryStore, evaluation: EvaluationStore, profile: FaceProfile
) -> dict[str, object] | None:
    with locked_snapshot(gallery, evaluation, profile) as (view, connection):
        report = evaluation.read_report(connection, view.calibration_fingerprint)
        if report is not None:
            validate_report(report, view.calibration_fingerprint)
        return report


def _valid_counts(value: object) -> bool:
    keys = {
        "total",
        "usable",
        "unusable",
        "correct_names",
        "false_names",
        "false_names_known",
        "false_names_unknown",
        "correct_unknown",
        "false_rejections",
        "uncertain",
    }
    if not isinstance(value, dict) or not keys <= value.keys():
        return False
    return all(
        isinstance(value[key], int) and not isinstance(value[key], bool) and value[key] >= 0
        for key in keys
    )


def _valid_rows(value: object) -> bool:
    keys = {
        "case_id",
        "image",
        "expected_id",
        "expected",
        "session",
        "status",
        "identity",
        "candidate",
        "similarity",
        "second_similarity",
        "outcome",
    }
    return isinstance(value, list) and all(
        isinstance(row, dict) and keys <= row.keys() for row in value
    )


def _valid_metadata(report: dict[str, object], fingerprint: str) -> bool:
    manifest = report.get("manifest")
    if not isinstance(manifest, dict):
        return False
    try:
        return manifest_fingerprint(manifest) == fingerprint and all(
            key in manifest and report.get(key) == manifest[key]
            for key in ("policy", "profile", "rule")
        )
    except (TypeError, ValueError):
        return False


def validate_report(report: dict[str, object], fingerprint: str) -> None:
    people = report.get("per_person")
    reserved = report.get("reserved_count")
    valid_people = isinstance(people, list) and all(
        isinstance(row, dict) and isinstance(row.get("identity"), str) and _valid_counts(row)
        for row in people
    )
    valid_reserved = isinstance(reserved, int) and not isinstance(reserved, bool) and reserved >= 0
    valid_header = report.get("schema") == 1 and report.get("fingerprint") == fingerprint
    if not all(
        (
            valid_people,
            valid_reserved,
            valid_header,
            _valid_metadata(report, fingerprint),
            _valid_counts(report.get("counts")),
            _valid_rows(report.get("rows")),
        )
    ):
        raise GalleryError("Rapport invalide ; relancez l'évaluation.")


def validate_evaluation(view: Snapshot) -> None:
    if view.unavailable_reference_ids:
        raise GalleryError(
            "Une référence retenue n'est plus disponible ou confirmée ; corrigez la sélection."
        )
    if not view.references:
        raise GalleryError("Préparez au moins une référence confirmée.")
    if any(not ref.session or ref.pending for ref in view.references):
        raise GalleryError("Confirmez les références et renseignez leurs séances.")
    calibration = [probe for probe in view.probes if probe.case.split == "calibration"]
    if not calibration:
        raise GalleryError("Ajoutez des photos au lot Calibration.")
    _validate_corpus(view)
    validate_prepared(calibration, "Calibration")


def _validate_corpus(view: Snapshot) -> None:
    groups: dict[str, str] = {}
    identities = {ref.photo.person_id for ref in view.references}
    for probe in view.probes:
        key = session_name(probe.case.session)[1]
        if key in view.reference_sessions or groups.get(key, probe.case.split) != probe.case.split:
            raise GalleryError(
                "Séparez les séances des références, de Calibration et du Test final."
            )
        groups[key] = probe.case.split
        if hashlib.sha256(probe.case.photo.image).hexdigest() in view.digests:
            raise GalleryError("Une image du corpus existe aussi parmi les références.")
        if probe.case.expected is not None and probe.case.expected not in identities:
            raise GalleryError("Une identité attendue n'a plus de référence confirmée compatible.")


def validate_prepared(calibration: list[Probe], label: str) -> None:
    for probe in calibration:
        if probe.missing_target:
            continue
        state = probe.state
        if state is None or (
            state.candidates
            and (state.reference is None or state.selected != state.reference.candidate)
        ):
            raise GalleryError(
                f"Préparez et confirmez tous les visages de {label} avant l'évaluation."
            )


def _row(
    probe: Probe, view: Snapshot, identities: dict[str, tuple[float, ...]], index: int
) -> dict[str, object]:
    expected = probe.case.expected
    row: dict[str, object] = {
        "case_id": probe.case.photo.id,
        "image": index,
        "expected_id": expected,
        "expected": view.names.get(expected, "Personne inconnue")
        if expected
        else "Personne inconnue",
        "session": probe.case.session,
        "status": "Non exploitable",
        "identity": None,
        "candidate": None,
        "similarity": None,
        "second_similarity": None,
        "outcome": "unusable",
        "reason": "Sujet attendu non détecté" if probe.missing_target else "Aucun visage détecté",
    }
    if probe.missing_target or probe.state is None or probe.state.reference is None:
        return row
    row["reason"] = None
    decision = decide(probe.state.reference.sample.vector, identities, view.policy)
    row.update(
        status=decision.status,
        identity=view.names.get(decision.identity or ""),
        candidate=view.names[decision.candidate],
        similarity=decision.score,
        second_similarity=decision.second,
    )
    if decision.status == "Reconnu":
        row["outcome"] = "correct_names" if decision.identity == expected else "false_names"
    elif decision.status == "Inconnu":
        row["outcome"] = "correct_unknown" if expected is None else "false_rejections"
    else:
        row["outcome"] = "uncertain"
    return row


def _counts(rows: list[dict[str, object]]) -> dict[str, int]:
    totals = Counter(str(row["outcome"]) for row in rows)
    false_known = sum(
        row["outcome"] == "false_names" and row["expected_id"] is not None for row in rows
    )
    return {
        "total": len(rows),
        "usable": len(rows) - totals["unusable"],
        "unusable": totals["unusable"],
        "correct_names": totals["correct_names"],
        "false_names": totals["false_names"],
        "false_names_known": false_known,
        "false_names_unknown": totals["false_names"] - false_known,
        "correct_unknown": totals["correct_unknown"],
        "false_rejections": totals["false_rejections"],
        "uncertain": totals["uncertain"],
    }


def build_report(view: Snapshot, split: str = "calibration") -> dict[str, object]:
    samples: dict[str, list[tuple[float, ...]]] = {}
    for ref in view.references:
        samples.setdefault(ref.photo.person_id, []).append(ref.vector)
    identities = prototypes(samples, view.profile.dimension)
    calibration = [probe for probe in view.probes if probe.case.split == split]
    rows = [_row(probe, view, identities, index + 1) for index, probe in enumerate(calibration)]
    per_person = [
        {"identity": label, **_counts([row for row in rows if row["expected_id"] == identity])}
        for identity, label in [(key, name) for key, name in view.names.items() if key in samples]
        + [(None, "Personnes inconnues")]
    ]
    return {
        "schema": 1,
        "fingerprint": view.calibration_fingerprint if split == "calibration" else view.fingerprint,
        "split": split,
        "profile": asdict(view.profile),
        "policy": asdict(view.policy),
        "rule": "mean-unit-cosine-v1",
        "created_utc": datetime.now(timezone.utc).isoformat(),
        "manifest": view.calibration_manifest if split == "calibration" else view.manifest,
        "reference_counts": {identity: len(vectors) for identity, vectors in samples.items()},
        "counts": _counts(rows),
        "rows": rows,
        "per_person": per_person,
        "reserved_count": len(view.probes) - len(calibration) if split == "calibration" else 0,
    }
