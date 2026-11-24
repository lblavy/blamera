"""Export explicite de prototypes compatibles avec le test final courant."""

import hashlib
import json
from datetime import datetime, timezone

from .evaluation_data import prototypes
from .evaluation_final import _final_report, _freeze
from .evaluation_snapshot import Snapshot, locked_snapshot
from .evaluation_store import EvaluationStore
from .face_gallery_package import encode_package
from .face_models import FaceConfiguration, load_configuration
from .gallery import GalleryError
from .gallery_store import GalleryStore


def _identities(view: Snapshot) -> list[dict[str, object]]:
    samples: dict[str, list[tuple[float, ...]]] = {}
    for reference in view.references:
        samples.setdefault(reference.photo.person_id, []).append(reference.vector)
    means = prototypes(samples, view.profile.dimension)
    return [
        {
            "id": identifier,
            "name": view.names[identifier],
            "reference_count": len(samples[identifier]),
            "prototype": list(means[identifier]),
        }
        for identifier in sorted(means)
    ]


def build_export(
    gallery: GalleryStore,
    evaluation: EvaluationStore,
    configuration: FaceConfiguration | None = None,
    *,
    exported_at: datetime | None = None,
) -> bytes:
    configuration = configuration or load_configuration()
    with locked_snapshot(gallery, evaluation, configuration.profile) as (view, connection):
        frozen = _freeze(connection, view)
        report = _final_report(connection, view) if frozen is not None else None
        if report is None:
            raise GalleryError(
                "Un test final courant avec ses paramètres figés est nécessaire pour exporter."
            )
        canonical = json.dumps(report, sort_keys=True, separators=(",", ":"), allow_nan=False)
        qualification = {
            "status": "exploratory",
            "scope": "held-out-local-corpus",
            "frozen_context": view.calibration_fingerprint,
            "test_report": hashlib.sha256(canonical.encode()).hexdigest(),
            "counts": report["counts"],
        }
        return encode_package(
            configuration,
            view.policy,
            _identities(view),
            qualification,
            exported_at or datetime.now(timezone.utc),
        )
