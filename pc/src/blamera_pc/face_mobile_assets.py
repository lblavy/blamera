"""Préparation locale des seuls assets faciaux générés, hors du dépôt suivi."""

import argparse
import hashlib
import json
import math
import os
import re
import shutil
import tempfile
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Sequence, cast

from PIL import Image

from .evaluation_data import Policy
from .face_data import FaceProfile
from .face_gallery_package import MAX_PACKAGE_BYTES, approved_pipeline, encode_package
from .face_models import FaceConfiguration, load_configuration
from .gallery import GalleryError

PARITY_TOLERANCES = {
    "bounds_abs_px": 1.0,
    "landmarks_abs_px": 1.0,
    "aligned_rgb_mae": 1.0,
    "embedding_cosine_min": 0.9999,
    "score_abs": 0.002,
}
JSON_DEPTH = 12
MAX_PARITY_MANIFEST = 1024 * 1024
MAX_PARITY_IMAGE = 8 * 1024 * 1024


class FaceAssetError(Exception):
    """An input failed validation; no private content belongs in its message."""


@dataclass(frozen=True)
class FaceAssetSource:
    path: Path
    relative: str
    size: int
    sha256: str


def validate_bootstrap(content: bytes) -> bytes:
    try:
        value = _object(content, MAX_PACKAGE_BYTES)
        configuration = load_configuration()
        keys = {
            "format",
            "schema",
            "exported_utc",
            "profile",
            "pipeline",
            "policy",
            "identities",
            "qualification",
        }
        if (
            set(value) != keys
            or value["format"] != "blamera-face-gallery"
            or type(value["schema"]) is not int
            or value["schema"] != 1
        ):
            raise ValueError
        if not _same_metadata(
            value["profile"], {"key": configuration.profile.key, "dimension": 512}
        ) or not _same_metadata(value["pipeline"], approved_pipeline(configuration)):
            raise ValueError
        date = value["exported_utc"]
        if (
            not isinstance(date, str)
            or re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z", date) is None
        ):
            raise ValueError
        exported = datetime.strptime(date, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
        policy = value["policy"]
        if not isinstance(policy, dict) or set(policy) != {"reject", "accept", "margin"}:
            raise ValueError
        identities, qualification = value["identities"], value["qualification"]
        if (
            not isinstance(identities, list)
            or not all(isinstance(item, dict) for item in identities)
            or not isinstance(qualification, dict)
        ):
            raise ValueError
        encode_package(configuration, Policy(**policy), identities, qualification, exported)
        return content
    except (ValueError, TypeError, KeyError, GalleryError, RecursionError, AttributeError):
        raise FaceAssetError("Galerie fournie invalide ou incompatible.") from None


def parity_asset_sources(directory: Path, profile: FaceProfile) -> list[FaceAssetSource]:
    try:
        manifest = directory / "manifest.json"
        content = _read(manifest, MAX_PARITY_MANIFEST)
        value = _object(content, MAX_PARITY_MANIFEST)
        if (
            set(value) != {"schema", "profile", "tolerances", "cases"}
            or type(value["schema"]) is not int
            or value["schema"] != 2
        ):
            raise ValueError
        if not _same_metadata(
            value["profile"], {"key": profile.key, "dimension": profile.dimension}
        ) or not _approved_tolerances(value["tolerances"]):
            raise ValueError
        cases = value["cases"]
        if not isinstance(cases, list) or not 1 <= len(cases) <= 20:
            raise ValueError
        sources = [_source(manifest, "faces/parity/manifest.json", content)]
        identifiers: set[str] = set()
        kinds: set[str] = set()
        for case in cases:
            _parity_case(case, directory, sources, identifiers, kinds)
        if kinds != {"single", "none", "multiple"}:
            raise ValueError
        return sources
    except (OSError, ValueError, TypeError, KeyError, RecursionError, AttributeError):
        raise FaceAssetError("Références publiques de parité invalides.") from None


def _same_metadata(value: object, approved: object) -> bool:
    # Android's integer fields reject JSON decimals, unlike Python's numeric equality.
    if type(value) is not type(approved):
        return False
    if isinstance(approved, dict):
        actual = cast(dict[str, object], value)
        return actual.keys() == approved.keys() and all(
            _same_metadata(actual[key], expected) for key, expected in approved.items()
        )
    if isinstance(approved, list):
        items = cast(list[object], value)
        return len(items) == len(approved) and all(
            _same_metadata(item, expected) for item, expected in zip(items, approved)
        )
    return value == approved


def _approved_tolerances(value: object) -> bool:
    return (
        isinstance(value, dict)
        and value == PARITY_TOLERANCES
        and all(type(number) in (int, float) for number in value.values())
    )


def _parity_case(
    case: object,
    directory: Path,
    sources: list[FaceAssetSource],
    identifiers: set[str],
    kinds: set[str],
) -> None:
    if not isinstance(case, dict):
        raise ValueError
    base = {"id", "image", "image_sha256", "width", "height", "expected_kind"}
    kind, identifier = case.get("expected_kind"), case.get("id")
    if (
        kind not in {"single", "none", "multiple"}
        or not isinstance(identifier, str)
        or re.fullmatch("[a-z0-9_-]{1,40}", identifier) is None
        or identifier in identifiers
    ):
        raise ValueError
    expected = (
        base
        | {
            "single": {"bounds", "landmarks", "aligned", "embedding"},
            "none": set(),
            "multiple": {"faces"},
        }[kind]
    )
    if set(case) != expected or not _dimension(case["width"]) or not _dimension(case["height"]):
        raise ValueError
    identifiers.add(identifier)
    kinds.add(kind)
    image = _parity_image(directory, case["image"], case["width"], case["height"])
    if image.sha256 != case["image_sha256"]:
        raise ValueError
    sources.append(image)
    for face in _face_expectations(case, kind):
        _single_expected(face, cast(int, case["width"]), cast(int, case["height"]))
        sources.append(_parity_image(directory, face["aligned"], 112, 112))


def _face_expectations(case: dict[str, object], kind: str) -> list[dict[str, object]]:
    if kind == "single":
        return [case]
    if kind == "none":
        return []
    faces = case["faces"]
    if not isinstance(faces, list) or not 2 <= len(faces) <= 20:
        raise ValueError
    keys = {"bounds", "landmarks", "aligned", "embedding"}
    if any(not isinstance(face, dict) or set(face) != keys for face in faces):
        raise ValueError
    return cast(list[dict[str, object]], faces)


def _single_expected(case: dict[str, object], width: int, height: int) -> None:
    bounds = _numbers(case["bounds"], 4)
    if not (0 <= bounds[0] < bounds[2] <= width and 0 <= bounds[1] < bounds[3] <= height):
        raise ValueError
    points = _numbers(case["landmarks"], 10)
    if any(
        not 0 <= number <= (width if index % 2 == 0 else height)
        for index, number in enumerate(points)
    ):
        raise ValueError
    embedding = _numbers(case["embedding"], 512)
    if not math.isclose(math.hypot(*embedding), 1.0, rel_tol=0, abs_tol=0.001):
        raise ValueError


def _numbers(value: object, size: int) -> list[float]:
    if (
        not isinstance(value, list)
        or len(value) != size
        or any(type(item) not in (int, float) or not math.isfinite(item) for item in value)
    ):
        raise ValueError
    return cast(list[float], value)


def _dimension(value: object) -> bool:
    return type(value) is int and 1 <= value <= 2048


def _parity_image(directory: Path, relative: object, width: int, height: int) -> FaceAssetSource:
    if not isinstance(relative, str):
        raise ValueError
    _relative(relative)
    path = directory / relative
    if not path.resolve().is_relative_to(directory.resolve()):
        raise ValueError
    content = _read(path, MAX_PARITY_IMAGE)
    with Image.open(path, formats=["PNG"]) as image:
        if image.size != (width, height) or image.mode != "RGB":
            raise ValueError
        image.load()
    return _source(path, "faces/parity/" + relative, content)


def _source(path: Path, relative: str, content: bytes) -> FaceAssetSource:
    return FaceAssetSource(path, relative, len(content), hashlib.sha256(content).hexdigest())


def _read(path: Path, limit: int) -> bytes:
    with path.open("rb") as stream:
        result = stream.read(limit + 1)
    if not result or len(result) > limit:
        raise ValueError
    return result


def _object(content: bytes, limit: int) -> dict[str, object]:
    if not 1 <= len(content) <= limit:
        raise ValueError
    value = json.loads(
        content.decode("utf-8"), object_pairs_hook=_unique, parse_constant=_nonfinite
    )
    if not isinstance(value, dict):
        raise ValueError
    _depth(value)
    return cast(dict[str, object], value)


def _nonfinite(value: str) -> object:
    raise ValueError("Non-finite JSON number")


def _unique(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key")
        result[key] = value
    return result


def _depth(value: object, level: int = 0) -> None:
    if isinstance(value, (dict, list)):
        if level >= JSON_DEPTH:
            raise ValueError
        for child in value.values() if isinstance(value, dict) else value:
            _depth(child, level + 1)


def prepare_face_assets(repository: Path, output: Path, sources: Sequence[FaceAssetSource]) -> None:
    stage: Path | None = None
    try:
        target = _output(repository, output)
        names = [_relative(source.relative) for source in sources]
        if len(names) != len(set(names)) or not sources:
            raise ValueError
        target.parent.mkdir(parents=True, exist_ok=True)
        stage = Path(tempfile.mkdtemp(prefix=".faceAssets-pending-", dir=target.parent))
        for source in sources:
            if source.path.resolve().is_relative_to(target):
                raise ValueError
            _copy(source, stage / source.relative)
        _publish(stage, target)
    except (OSError, ValueError):
        raise FaceAssetError(
            "Préparation des assets faciaux impossible ; sortie non partielle."
        ) from None
    finally:
        if stage is not None and stage.exists():
            _remove_generated(stage, repository)


def _output(repository: Path, output: Path) -> Path:
    repository = repository.resolve()
    expected = repository / "app/build/generated/faceAssets"
    if output.absolute() != expected or output.is_symlink() or output.resolve() != expected:
        raise ValueError
    if not output.parent.resolve().is_relative_to(repository):
        raise ValueError
    return output


def _relative(value: str) -> str:
    path = PurePosixPath(value)
    if (
        not value
        or "\\" in value
        or ":" in value
        or path.is_absolute()
        or ".." in path.parts
        or path.as_posix() != value
    ):
        raise ValueError
    return value


def _copy(source: FaceAssetSource, destination: Path) -> None:
    if (
        type(source.size) is not int
        or source.size <= 0
        or re.fullmatch("[a-f0-9]{64}", source.sha256) is None
    ):
        raise ValueError
    digest = hashlib.sha256()
    total = 0
    destination.parent.mkdir(parents=True, exist_ok=True)
    with source.path.open("rb") as original, destination.open("wb") as output:
        while block := original.read(65536):
            total += len(block)
            if total > source.size:
                raise ValueError
            digest.update(block)
            output.write(block)
    if total != source.size or digest.hexdigest() != source.sha256:
        raise ValueError


def _publish(stage: Path, target: Path) -> None:
    backup = target.with_name(".faceAssets-previous-" + uuid.uuid4().hex)
    repository = target.parents[3]
    if target.exists():
        os.replace(target, backup)
    try:
        os.replace(stage, target)
    except OSError:
        if backup.exists():
            os.replace(backup, target)
        raise
    if backup.exists():
        _remove_generated(backup, repository)


def _remove_generated(path: Path, repository: Path) -> None:
    parent = repository.resolve() / "app/build/generated"
    if (
        path.resolve().parent != parent
        or not path.name.startswith(".faceAssets-")
        or path.is_symlink()
    ):
        raise ValueError("Unverified generated directory")
    shutil.rmtree(path)


def asset_sources(
    configuration: FaceConfiguration, models: Path, parity: Path, gallery: Path | None
) -> list[FaceAssetSource]:
    approved_pipeline(configuration)
    sources = [
        FaceAssetSource(models / file.name, "faces/models/" + file.name, file.size, file.sha256)
        for file in configuration.files
    ]
    sources.extend(parity_asset_sources(parity, configuration.profile))
    if gallery is not None:
        content = validate_bootstrap(_read(gallery, MAX_PACKAGE_BYTES))
        sources.append(_source(gallery, "faces/bootstrap.json", content))
    return sources


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Prepare generated facial assets without publishing private data"
    )
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--models", type=Path, required=True)
    parser.add_argument("--parity", type=Path, required=True)
    parser.add_argument("--gallery", type=Path)
    args = parser.parse_args()
    try:
        sources = asset_sources(load_configuration(), args.models, args.parity, args.gallery)
        prepare_face_assets(args.repository, args.output, sources)
    except (FaceAssetError, GalleryError, OSError, ValueError):
        raise SystemExit("Préparation faciale refusée : vérifier les entrées locales.") from None
    print(
        f"Prepared facial assets: {len(sources)} files; personal gallery supplied: {args.gallery is not None}"
    )


if __name__ == "__main__":
    main()
