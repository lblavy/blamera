"""Écriture bornée du contrat commun de galerie ; aucune lecture de fichier privé."""

import json
import math
import re
import unicodedata
from dataclasses import asdict
from datetime import datetime, timezone
from typing import cast

from .evaluation_data import Policy
from .face_models import FaceConfiguration, load_configuration
from .gallery import GalleryError

MAX_PACKAGE_BYTES = 120 * 1024
PROFILE_KEY = "4a6472a476b37ec36626f41542d7fd41f13da63dfbeb09cd1ffbc480993aded1"
MODEL_ROLES = {"detector", "aligner", "encoder"}
COUNT_KEYS = {
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


def approved_pipeline(configuration: FaceConfiguration) -> dict[str, object]:
    if configuration != load_configuration() or configuration.profile.key != PROFILE_KEY:
        raise GalleryError("Profil facial non pris en charge par le format d'export.")
    models, notices = [], []
    for file in configuration.files:
        item: dict[str, object] = {
            "role": file.role,
            "name": file.name,
            "size": file.size,
            "sha256": file.sha256,
        }
        if file.role in MODEL_ROLES:
            models.append(item)
        else:
            notices.append({**item, "url": file.url})
    return {
        "profile_name": "opencv-yunet2023mar-arcface-r50-webface600k-v1",
        "preprocessing": "opencv-alignCrop-5points-RGB112-minus127.5-div127.5-L2-v1",
        "runtime": "opencv-dnn-4.13.0.92-cpu",
        "detector": {
            "size": configuration.detector_size,
            "score": configuration.score,
            "nms": configuration.nms,
        },
        "aggregation": "mean-unit-cosine-v1",
        "models": models,
        "notices": notices,
    }


def _integer(value: object, minimum: int, maximum: int) -> bool:
    return type(value) is int and minimum <= value <= maximum


def _name(value: object) -> bool:
    return (
        isinstance(value, str)
        and 1 <= len(value) <= 80
        and value.strip() == value
        and not any(unicodedata.category(char).startswith("C") for char in value)
    )


def _prototype(value: object) -> bool:
    if not isinstance(value, list) or len(value) != 512:
        return False
    if any(
        type(item) not in (int, float) or not -1.001 <= item <= 1.001 or not math.isfinite(item)
        for item in value
    ):
        return False
    return math.isclose(math.hypot(*value), 1.0, abs_tol=0.001, rel_tol=0)


def _identities(entries: list[dict[str, object]]) -> None:
    if not 1 <= len(entries) <= 10:
        raise GalleryError("Export limité à une à dix identités.")
    identifiers: set[str] = set()
    names: set[str] = set()
    for entry in entries:
        identifier, name = entry.get("id"), entry.get("name")
        valid = (
            set(entry) == {"id", "name", "reference_count", "prototype"}
            and isinstance(identifier, str)
            and re.fullmatch("[0-9a-f]{32}", identifier) is not None
            and _name(name)
            and _integer(entry.get("reference_count"), 1, 10000)
            and _prototype(entry.get("prototype"))
        )
        if not valid or identifier in identifiers or name in names:
            raise GalleryError("Identité ou prototype incompatible avec l'export.")
        identifiers.add(cast(str, identifier))
        names.add(cast(str, name))


def _counts(value: object) -> bool:
    if not isinstance(value, dict) or set(value) != COUNT_KEYS:
        return False
    counts = cast(dict[str, int], value)
    if not all(_integer(count, 0, 100000) for count in counts.values()):
        return False
    total = counts["total"]
    return (
        total > 0
        and all(count <= total for count in counts.values())
        and counts["usable"] + counts["unusable"] == total
        and counts["false_names_known"] + counts["false_names_unknown"] == counts["false_names"]
        and sum(
            counts[key]
            for key in (
                "correct_names",
                "false_names",
                "correct_unknown",
                "false_rejections",
                "uncertain",
                "unusable",
            )
        )
        == total
    )


def _qualification(value: dict[str, object]) -> None:
    keys = {"status", "scope", "frozen_context", "test_report", "counts"}
    hashes = [value.get(key) for key in ("frozen_context", "test_report")]
    if not (
        set(value) == keys
        and value.get("status") == "exploratory"
        and value.get("scope") == "held-out-local-corpus"
        and _counts(value.get("counts"))
        and all(
            isinstance(digest, str) and re.fullmatch("[0-9a-f]{64}", digest) for digest in hashes
        )
    ):
        raise GalleryError("Qualification du test final incompatible avec l'export.")


def encode_package(
    configuration: FaceConfiguration,
    policy: Policy,
    identities: list[dict[str, object]],
    qualification: dict[str, object],
    exported_at: datetime,
) -> bytes:
    pipeline = approved_pipeline(configuration)
    policy.validate()
    _identities(identities)
    _qualification(qualification)
    if not isinstance(exported_at, datetime) or exported_at.utcoffset() is None:
        raise GalleryError("Date d'export UTC indisponible.")
    package = {
        "format": "blamera-face-gallery",
        "schema": 1,
        "exported_utc": exported_at.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "profile": asdict(configuration.profile),
        "pipeline": pipeline,
        "policy": asdict(policy),
        "identities": identities,
        "qualification": qualification,
    }
    payload = json.dumps(
        package, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode("utf-8")
    if len(payload) > MAX_PACKAGE_BYTES:
        raise GalleryError("Galerie trop volumineuse pour l'export (120 Kio maximum).")
    return payload
