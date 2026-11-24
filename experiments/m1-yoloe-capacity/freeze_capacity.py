"""Pin all new exports plus nested immutable quality/Small computation and results."""

from pathlib import Path
from datetime import datetime, timezone
import sys, json, ast
from capacity_adapter import ORDER
from freeze_small import verify as verify_small
from freeze_study import verify_seal
from runner_core import verify_files, sha256
from small_adapter import vocabulary_translation
from parity_l import validate_diagnosis

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).resolve().parent
BASE = ROOT / "build/reports/validation/m1-yoloe-capacity"
OLD = ROOT / "build/reports/validation/m1-yoloe-quality"
SMALL = ROOT / "build/reports/validation/m1-yoloe-small"
SMALL_SHA = "38562deb71a3a1a9fc4c2749b1baff08def2cb6f84d86b25eb4a71a95ccdc445"


def verify(expected):
    verify_small(SMALL_SHA)
    return verify_seal(ROOT, BASE / "sealed-manifest.json", expected)


def create():
    target = BASE / "sealed-manifest.json"
    if target.exists():
        raise ValueError("Never overwrite the capacity seal")
    verify_small(SMALL_SHA)
    config = json.loads((SOURCE / "protocol.json").read_text(encoding="utf-8"))
    for directory, key in [(OLD, "nano_baseline"), (SMALL, "small")]:
        verify_files(
            ROOT,
            [
                {
                    "path": (directory / "results-v1/summary.json")
                    .relative_to(ROOT)
                    .as_posix(),
                    "sha256": config["references"][key]["summary"],
                },
                {
                    "path": (directory / "results-v1/per-image.jsonl")
                    .relative_to(ROOT)
                    .as_posix(),
                    "sha256": config["references"][key]["jsonl"],
                },
            ],
        )
    import onnx

    nano = onnx.load(ROOT / "models/m1-yoloe/yoloe-26n-seg-pf.onnx")
    names = ast.literal_eval(
        {item.key: item.value for item in nano.metadata_props}["names"]
    )
    paths = list(SOURCE.glob("*.py")) + [
        SOURCE / "protocol.json",
        OLD / "sealed-manifest.json",
        SMALL / "sealed-manifest.json",
        BASE / "source/acquisition.json",
        BASE / "source/asset-metadata.json",
        ROOT / config["licence"]["path"],
    ]
    verify_files(ROOT, [config["licence"]])
    model_checks = {}
    for size in ORDER:
        path = BASE / size / "export-parity.json"
        proof = json.loads(path.read_text(encoding="utf-8"))
        if size == "l":
            if proof["status"] != "L_CHANNEL_PARITY_REVIEWED_AMENDMENT":
                raise ValueError("Missing reviewed L amendment")
            validate_diagnosis(
                json.loads((BASE / "l/diagnosis.json").read_text(encoding="utf-8"))
            )
        elif proof["status"] != "EXPORT_PARITY_READY_NO_144_PREDICTIONS":
            raise ValueError("Incomplete export parity")
        canonical = {int(key): value for key, value in proof["canonical_names"].items()}
        candidate = {int(key): value for key, value in proof["candidate_names"].items()}
        if (
            names != canonical
            or vocabulary_translation(names, candidate)
            != proof["canonical_from_candidate_indices"]
        ):
            raise ValueError("Vocabulary drift for " + size)
        verify_files(ROOT, [proof["checkpoint"], proof["onnx"]])
        if proof["checkpoint"]["sha256"] != config["models"][size]["sha256"]:
            raise ValueError("Checkpoint provenance mismatch")
        paths += [
            path,
            ROOT / proof["checkpoint"]["path"],
            ROOT / proof["onnx"]["path"],
        ]
        model_checks[size] = {
            "onnx": proof["onnx"],
            "vocabulary_entries": 4585,
            "order_identity": proof["vocabulary_identity"],
            "parity": (
                "Reviewed L-only coordinate.05px amendment; other bounds unchanged, masks checked score.35 only"
                if size == "l"
                else "Native/ONNX two historical fixtures, original tolerances"
            ),
        }
    for directory in [OLD, SMALL]:
        paths += [
            directory / "results-v1/summary.json",
            directory / "results-v1/per-image.jsonl",
        ]
    paths += sorted((SMALL / "results-v1/panels").glob("*.png"))
    paths += [BASE / "l/diagnosis.json", BASE / "export-l.log"]
    files = [
        {"path": path.relative_to(ROOT).as_posix(), "sha256": sha256(path)}
        for path in paths
    ]
    result = {
        "status": "CAPACITY_PREPARED_PENDING_REVIEW_NO_144_PREDICTIONS",
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "order": ORDER,
        "original_seal": config["original_quality_seal"],
        "small_seal": SMALL_SHA,
        "files": files,
        "versions": {
            "numpy": "2.5.4",
            "opencv-python": "5.0.0.93",
            "Pillow": "13.0.0",
            "onnxruntime": "1.31.0",
            "pycocotools": "2.0.11",
            "torch": "2.14.1+cpu",
            "ultralytics": "8.4.190",
            "onnx": "1.23.2",
        },
        "models": model_checks,
        "expected_per_size": {"forwards": 144, "profiles": 12, "records": 1728},
        "references_reused_not_rerun": True,
    }
    target.write_text(json.dumps(result, indent=2), encoding="utf-8", newline="\n")
    print(
        json.dumps(
            {
                "seal": str(target.relative_to(ROOT)),
                "sha256": sha256(target),
                "files": len(files),
                "corpus_predictions": 0,
            }
        )
    )


if __name__ == "__main__":
    sys.path.insert(0, str(ROOT / ".tools/m1-yoloe-quality/pydeps"))
    create()
