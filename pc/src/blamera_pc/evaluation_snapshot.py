"""Vue cohérente des deux bases ; aucune donnée biométrique dans les logs."""

import hashlib
import json
import sqlite3
import struct
from collections.abc import Iterator
from contextlib import contextmanager
from dataclasses import asdict, dataclass
from typing import cast

from .evaluation_absence import TargetAbsences
from .evaluation_data import Policy
from .evaluation_store import Case, EvaluationStore, session_name, source_key
from .face_data import FaceProfile, FaceState
from .face_store import FaceReferences
from .gallery import Photo
from .gallery_store import GalleryStore


@dataclass(frozen=True)
class Reference:
    photo: Photo
    vector: tuple[float, ...]
    session: str
    pending: bool


@dataclass(frozen=True)
class Probe:
    case: Case
    state: FaceState | None
    missing_target: bool = False


@dataclass(frozen=True)
class Snapshot:
    fingerprint: str
    profile: FaceProfile
    policy: Policy
    names: dict[str, str]
    references: tuple[Reference, ...]
    probes: tuple[Probe, ...]
    digests: set[str]
    manifest: dict[str, object]
    available_references: tuple[Reference, ...]
    reference_selection: tuple[str, ...] | None
    unavailable_reference_ids: tuple[str, ...]

    @property
    def calibration_manifest(self) -> dict[str, object]:
        # Test targets may be prepared after freezing. Their sources and annotations
        # are fixed, but their detections/choices must not invalidate calibration.
        cases = [
            entry if entry[3] == "calibration" else [*entry[:5], None]
            for entry in cast(list[list[object]], self.manifest["cases"])
        ]
        return {**self.manifest, "cases": cases}

    @property
    def calibration_fingerprint(self) -> str:
        return manifest_fingerprint(self.calibration_manifest)

    @property
    def reference_sessions(self) -> set[str]:
        return {session_name(ref.session)[1] for ref in self.references if ref.session}


def _state_key(state: FaceState | None) -> object:
    if state is None:
        return None
    reference = state.reference
    vector_hash = None
    if reference is not None:
        packed = struct.pack(f"<{len(reference.sample.vector)}f", *reference.sample.vector)
        vector_hash = [reference.candidate, hashlib.sha256(packed).hexdigest()]
    return [
        state.generation,
        state.selected,
        [candidate.values for candidate in state.candidates],
        vector_hash,
    ]


def _references(
    gallery: GalleryStore,
    evaluation: EvaluationStore,
    gc: sqlite3.Connection,
    ec: sqlite3.Connection,
    profile: FaceProfile,
) -> tuple[dict[str, str], list[Reference], list[object], set[str]]:
    names = {row["id"]: row["name"] for row in gc.execute("SELECT id,name FROM people ORDER BY id")}
    refs: list[Reference] = []
    metadata: list[object] = []
    digests: set[str] = set()
    faces = FaceReferences(gallery)
    for row in gc.execute("SELECT * FROM photos ORDER BY id"):
        photo = Photo(
            row["id"], row["person_id"], row["image"], row["width"], row["height"], row["revision"]
        )
        digests.add(hashlib.sha256(photo.image).hexdigest())
        state = faces._read(gc, photo, profile)
        session = evaluation.read_session(ec, photo)
        metadata.append([photo.id, source_key(photo), _state_key(state), session])
        if state is not None and state.reference is not None:
            refs.append(
                Reference(
                    photo,
                    state.reference.sample.vector,
                    session,
                    state.selected != state.reference.candidate,
                )
            )
    return names, refs, metadata, digests


def manifest_fingerprint(manifest: dict[str, object]) -> str:
    return hashlib.sha256(
        json.dumps(manifest, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()
    ).hexdigest()


def _probe(
    evaluation: EvaluationStore, ec: sqlite3.Connection, case: Case, profile: FaceProfile
) -> Probe:
    state = FaceReferences(evaluation)._read(ec, case.photo, profile)
    return Probe(case, state, TargetAbsences.read(ec, case, profile, state))


def _probe_key(probe: Probe) -> object:
    state = _state_key(probe.state)
    # Preserve the exact 0.6.0 manifest when no human absence was recorded.
    return [state, {"target_absent": True}] if probe.missing_target else state


def _capture(
    gallery: GalleryStore,
    evaluation: EvaluationStore,
    gc: sqlite3.Connection,
    ec: sqlite3.Connection,
    profile: FaceProfile,
) -> Snapshot:
    names, refs, ref_metadata, digests = _references(gallery, evaluation, gc, ec, profile)
    selection = evaluation.read_reference_selection(ec)
    available = tuple(refs)
    unavailable = (
        () if selection is None else tuple(sorted(set(selection) - {ref.photo.id for ref in refs}))
    )
    if selection is not None:
        refs = [ref for ref in refs if ref.photo.id in selection]
    policy = evaluation.read_policy(ec)
    probes = tuple(_probe(evaluation, ec, case, profile) for case in evaluation.read_cases(ec))
    metadata = [
        [
            probe.case.photo.id,
            source_key(probe.case.photo),
            probe.case.expected,
            probe.case.split,
            probe.case.session,
            _probe_key(probe),
        ]
        for probe in probes
    ]
    manifest: dict[str, object] = {
        "rule": "mean-unit-cosine-v1",
        "profile": asdict(profile),
        "policy": asdict(policy),
        "people": names,
        "references": ref_metadata,
        "reference_selection": selection,
        "cases": metadata,
    }
    fingerprint = manifest_fingerprint(manifest)
    return Snapshot(
        fingerprint,
        profile,
        policy,
        names,
        tuple(refs),
        probes,
        digests,
        manifest,
        available,
        selection,
        unavailable,
    )


@contextmanager
def locked_snapshot(
    gallery: GalleryStore, evaluation: EvaluationStore, profile: FaceProfile, *, write: bool = False
) -> Iterator[tuple[Snapshot, sqlite3.Connection]]:
    # Always take locks in this order. Report calculation is pure and short;
    # no model call or user interaction happens while both stores are locked.
    with gallery._connection() as gc, evaluation._connection() as ec:
        gc.execute("BEGIN IMMEDIATE" if write else "BEGIN")
        ec.execute("BEGIN IMMEDIATE" if write else "BEGIN")
        yield _capture(gallery, evaluation, gc, ec, profile), ec


def snapshot(gallery: GalleryStore, evaluation: EvaluationStore, profile: FaceProfile) -> Snapshot:
    with locked_snapshot(gallery, evaluation, profile) as (view, _):
        return view
