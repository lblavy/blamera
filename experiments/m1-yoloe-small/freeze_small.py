"""Seal the Small adapter against the untouched original quality experiment."""

from pathlib import Path
from datetime import datetime, timezone
import sys, json, ast
from small_adapter import vocabulary_translation
from freeze_study import verify_seal, VERSIONS
from runner_core import sha256, verify_files

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).resolve().parent
BASE = ROOT / "build/reports/validation/m1-yoloe-small"
OLD = ROOT / "build/reports/validation/m1-yoloe-quality"
OLD_SHA = "6ff9b6b463dde7b28e7f065438aa7a6162437b4ccf16d9d226ee38fa4543dcb2"


def verify(expected):
    verify_seal(ROOT, OLD / "sealed-manifest.json", OLD_SHA)
    return verify_seal(ROOT, BASE / "sealed-manifest.json", expected)


def create():
    target = BASE / "sealed-manifest.json"
    if target.exists():
        raise ValueError("Never overwrite a previous Small seal")
    verify_seal(ROOT, OLD / "sealed-manifest.json", OLD_SHA)
    proof = json.loads((BASE / "export-parity.json").read_text(encoding="utf-8"))
    protocol = json.loads((SOURCE / "protocol.json").read_text(encoding="utf-8"))
    verify_files(
        ROOT,
        [
            protocol["export_proof"],
            {
                "path": "build/reports/validation/m1-yoloe-quality/results-v1/summary.json",
                "sha256": protocol["reference_results"]["summary_sha256"],
            },
            {
                "path": "build/reports/validation/m1-yoloe-quality/results-v1/per-image.jsonl",
                "sha256": protocol["reference_results"]["per_image_sha256"],
            },
        ],
    )
    # This metadata check loads graph bytes only, no inference session or forward.
    import onnx

    nano = onnx.load(ROOT / "models/m1-yoloe/yoloe-26n-seg-pf.onnx")
    names = ast.literal_eval(
        {item.key: item.value for item in nano.metadata_props}["names"]
    )
    canonical = {int(key): value for key, value in proof["canonical_names"].items()}
    small = {int(key): value for key, value in proof["small_names"].items()}
    if (
        names != canonical
        or vocabulary_translation(names, small) != proof["canonical_from_small_indices"]
    ):
        raise ValueError("Full actual Nano/Small vocabulary mismatch")
    paths = list(SOURCE.glob("*.py")) + [
        SOURCE / "protocol.json",
        ROOT / "experiments/m1-yoloe/manifest.json",
        OLD / "sealed-manifest.json",
        OLD / "inputs-manifest.json",
        OLD / "results-v1/summary.json",
        OLD / "results-v1/per-image.jsonl",
        BASE / "export-parity.json",
        BASE / "source/acquisition.json",
        BASE / "source/asset-metadata.json",
        ROOT / proof["checkpoint"]["path"],
        ROOT / proof["onnx"]["path"],
        ROOT / "models/m1-yoloe-small/LICENSE-assets-AGPL.txt",
    ]
    paths += sorted((OLD / "results-v1/panels").glob("*.png"))
    files = [
        {"path": path.relative_to(ROOT).as_posix(), "sha256": sha256(path)}
        for path in paths
    ]
    verify_files(ROOT, [proof["checkpoint"], proof["onnx"]])
    result = {
        "status": "SMALL_PREPARED_PENDING_REVIEW_NO_144_PREDICTIONS",
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "original_quality_seal_sha256": OLD_SHA,
        "files": files,
        "versions": {
            **VERSIONS,
            "torch": "2.14.1+cpu",
            "ultralytics": "8.4.190",
            "onnx": "1.23.2",
        },
        "cpu_environment": "Read-only historical Python3.12.14/Ultralytics8.4.190/Torch2.14.1+cpu/ORT1.31.0",
        "expected": {
            "new_forwards": 144,
            "profiles": 12,
            "records": 1728,
            "reference_forwards_reused": 288,
        },
        "vocabulary": {
            "classes": 4585,
            "full_actual_nano_metadata_verified": True,
            "identity_order": proof["vocabulary_identity"],
        },
    }
    target.write_text(json.dumps(result, indent=2), encoding="utf-8", newline="\n")
    print(
        json.dumps(
            {
                "seal": str(target.relative_to(ROOT)),
                "sha256": sha256(target),
                "files": len(files),
                "quality_corpus_predictions": 0,
            }
        )
    )


if __name__ == "__main__":
    sys.path.insert(0, str(ROOT / ".tools/m1-yoloe-quality/pydeps"))
    create()
