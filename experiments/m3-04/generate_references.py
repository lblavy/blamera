"""Public, reproducible references for the Android face pipeline."""

import argparse
import hashlib
import io
import json
import math
import re
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "pc/src"))
from blamera_pc.face_data import (  # noqa: E402
    FaceCandidate,
    FaceEngine,
    normalize_vector,
    validate_candidates,
    validate_crop,
)
from blamera_pc.face_engine import OpenCvFaceEngine  # noqa: E402
from blamera_pc.face_models import load_configuration  # noqa: E402
from blamera_pc.gallery import GalleryError, Photo  # noqa: E402

SOURCE_SHA256 = "88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5"
TOLERANCES = {
    "bounds_abs_px": 1.0,
    "landmarks_abs_px": 1.0,
    "aligned_rgb_mae": 1.0,
    "embedding_cosine_min": 0.9999,
    "score_abs": 0.002,
}


def _png(image: Image.Image) -> bytes:
    output = io.BytesIO()
    image.save(output, "PNG", compress_level=9)
    return output.getvalue()


def _inputs(portrait: bytes) -> dict[str, tuple[bytes, str]]:
    with Image.open(io.BytesIO(portrait)) as original, original.convert("RGB") as image:
        pair = Image.new("RGB", (2 * image.width, image.height))
        pair.paste(image, (0, 0))
        pair.paste(image, (image.width, 0))
        blank = Image.new("RGB", (320, 240))
        return {
            "portrait": (_png(image), "single"),
            "blank": (_png(blank), "none"),
            "multiple": (_png(pair), "multiple"),
        }


def _single(
    image: bytes, candidate: FaceCandidate, engine: FaceEngine, aligned_path: str
) -> tuple[dict[str, object], bytes]:
    sample = engine.encode(image, candidate)
    validate_crop(sample.crop)
    normalize_vector(sample.vector, 512)
    if not math.isclose(math.hypot(*sample.vector), 1.0, abs_tol=0.000001):
        raise ValueError("The reference embedding must already be unit normalized.")
    values = candidate.values
    return {
        "bounds": [values[0], values[1], values[0] + values[2], values[1] + values[3]],
        "landmarks": list(values[4:14]),
        "aligned": aligned_path,
        "embedding": list(sample.vector),
    }, sample.crop


def _case(
    identifier: str,
    image: bytes,
    kind: str,
    engine: FaceEngine,
    files: dict[str, bytes],
) -> dict[str, object]:
    with Image.open(io.BytesIO(image)) as picture:
        width, height = picture.size
    candidates = engine.detect(image)
    validate_candidates(Photo("", "", image, width, height), tuple(candidates))
    count_matches = {
        "single": len(candidates) == 1,
        "none": not candidates,
        "multiple": len(candidates) >= 2,
    }
    if not count_matches[kind]:
        raise ValueError(f"Unexpected face count for the public {identifier} fixture.")
    path = f"images/{identifier}.png"
    files[path] = image
    result: dict[str, object] = {
        "id": identifier,
        "image": path,
        "image_sha256": hashlib.sha256(image).hexdigest(),
        "width": width,
        "height": height,
        "expected_kind": kind,
    }
    if kind == "single":
        values, aligned = _single(
            image, candidates[0], engine, f"expected/{identifier}-aligned.png"
        )
        result.update(values)
        files[str(values["aligned"])] = aligned
    elif kind == "multiple":
        faces = []
        # The PC engine returns descending scores, preserving detector order on ties.
        for index, candidate in enumerate(candidates):
            values, aligned = _single(
                image, candidate, engine, f"expected/{identifier}-{index}-aligned.png"
            )
            faces.append(values)
            files[str(values["aligned"])] = aligned
        result["faces"] = faces
    return result


def reference_artifacts(portrait: bytes, engine: FaceEngine) -> dict[str, bytes]:
    profile = engine.profile
    if profile.dimension != 512 or not re.fullmatch("[0-9a-f]{64}", profile.key):
        raise ValueError("The expected ArcFace profile is invalid.")
    files: dict[str, bytes] = {}
    try:
        cases = [
            _case(name, image, kind, engine, files)
            for name, (image, kind) in _inputs(portrait).items()
        ]
    except GalleryError as error:
        raise ValueError("The public reference model output is invalid.") from error
    manifest = {
        "schema": 2,
        "profile": {"key": profile.key, "dimension": profile.dimension},
        "tolerances": TOLERANCES.copy(),
        "cases": cases,
    }
    files["manifest.json"] = (
        json.dumps(manifest, sort_keys=True, indent=2, allow_nan=False).encode("utf-8") + b"\n"
    )
    return files


def generate(
    source: Path, destination: Path, engine: FaceEngine, expected_sha256: str
) -> dict[str, object]:
    original = source.read_bytes()
    if hashlib.sha256(original).hexdigest() != expected_sha256:
        raise ValueError("The pinned public source checksum does not match.")
    files = reference_artifacts(original, engine)
    _publish(destination, files)
    return {
        "cases": 3,
        "files": len(files),
        "manifest_sha256": hashlib.sha256(files["manifest.json"]).hexdigest(),
    }


def _publish(destination: Path, files: dict[str, bytes]) -> None:
    destination = destination.resolve()
    if (ROOT / "build").resolve() not in destination.parents:
        raise ValueError("Reference output must stay below the workspace build directory.")
    if destination.exists():
        existing = _existing_artifacts(destination, files)
        if existing != files:
            raise ValueError("The existing reference set differs; choose a new output directory.")
        return
    destination.parent.mkdir(parents=True, exist_ok=True)
    # The staging directory and its automatic cleanup remain inside verified build/.
    with tempfile.TemporaryDirectory(
        prefix=".face-references-", dir=destination.parent
    ) as temporary:
        staging = Path(temporary)
        for name, content in files.items():
            path = staging / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
        staging.rename(destination)


def _existing_artifacts(destination: Path, expected: dict[str, bytes]) -> dict[str, bytes]:
    existing = {}
    for path in destination.rglob("*"):
        if not path.is_file():
            continue
        name = path.relative_to(destination).as_posix()
        if name not in expected or destination not in path.resolve().parents:
            raise ValueError("The output directory contains an unrelated or linked file.")
        if path.stat().st_size != len(expected[name]):
            raise ValueError("An existing reference artifact has a different size.")
        existing[name] = path.read_bytes()
    return existing


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "build/fixtures/m3-05")
    arguments = parser.parse_args()
    destination = arguments.output.resolve()
    if (ROOT / "build").resolve() not in destination.parents:
        parser.error("Reference output must stay below the workspace build directory.")
    engine = OpenCvFaceEngine(load_configuration())
    report = generate(ROOT / "models/m3-02/astronaut.png", destination, engine, SOURCE_SHA256)
    print(json.dumps(report, sort_keys=True))


if __name__ == "__main__":
    main()
