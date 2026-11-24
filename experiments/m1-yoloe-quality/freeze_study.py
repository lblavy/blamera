"""Seal exact local inputs and sources; no inference or model-runtime imports."""

from pathlib import Path
from datetime import datetime, timezone
import importlib.metadata
import json, sys
from runner_core import sha256, verify_files

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(__file__).resolve().parent
BASE = ROOT / "build/reports/validation/m1-yoloe-quality"
PINS = {
    "protocol.json": "8f1b40970a6fa8e73910da30fa711d838c55533f99a2cdeef4151d0f83e5613f",
    "mapping-proposal.json": "7701f2609e1dd2d8836fe310772b5542ec02f74837b5b4fbb27f89f20ef7f82e",
    "selection-proposal.json": "15d3afbb6b597625fbbb6604b7ca93a81f0921a36d0afdc2ebde6b6bb5061feb",
}
VERSIONS = {
    "numpy": "2.5.4",
    "opencv-python": "5.0.0.93",
    "Pillow": "13.0.0",
    "onnxruntime": "1.31.0",
    "pycocotools": "2.0.11",
}


def verify_seal(root, path, expected):
    if sha256(path) != expected:
        raise ValueError("Seal SHA differs from reviewed reference")
    seal = json.loads(path.read_text(encoding="utf-8"))
    verify_files(root, seal["files"])
    for name, version in seal["versions"].items():
        if importlib.metadata.version(name) != version:
            raise ValueError("Dependency version drift: " + name)
    return seal


def create_seal():
    path = BASE / "sealed-manifest.json"
    if path.exists():
        raise ValueError("Do not overwrite an existing seal; preserve reviewed history")
    for name, expected in PINS.items():
        if sha256(SOURCE / name) != expected:
            raise ValueError("Reviewed phase1 source changed: " + name)
    inputs = BASE / "inputs-manifest.json"
    if (
        sha256(inputs)
        != "faa145fbe98b97bf55cbbd3ebfdc46bb2ae49b352ff6774f7d2d97a1110a08b8"
    ):
        raise ValueError("Acquired inputs changed")
    selection = json.loads(
        (SOURCE / "selection-proposal.json").read_text(encoding="utf-8")
    )
    config = json.loads((SOURCE / "protocol.json").read_text(encoding="utf-8"))
    acquired = json.loads(inputs.read_text(encoding="utf-8"))
    files = [
        {"path": str(inputs.relative_to(ROOT).as_posix()), "sha256": sha256(inputs)},
        *[{"path": row["path"], "sha256": row["sha256"]} for row in acquired["images"]],
    ]
    files += [
        {"path": row["mask_file"], "sha256": row["mask_sha256"]}
        for row in selection["ade"]
    ]
    files += [
        {"path": row["path"], "sha256": row["sha256"]}
        for row in config["models"].values()
    ]
    files.append(
        {
            "path": "build/reports/validation/m1-yoloe-quality/source/lvis_v1_val.json",
            "sha256": config["lvis_annotation_sha256"],
        }
    )
    code = (
        list(SOURCE.glob("*.py"))
        + [SOURCE / name for name in PINS]
        + [
            ROOT / "experiments/m1-yoloe/uv.lock",
            ROOT / "experiments/m1-yoloe/postprocess.py",
            ROOT / "experiments/m1-yoloe/metrics.py",
        ]
    )
    # Private rasterizer sources/native wheel are part of the sealed computation, not a global installation.
    addon = ROOT / ".tools/m1-yoloe-quality/pydeps"
    code += [
        p
        for p in addon.rglob("*")
        if p.is_file()
        and (
            p.suffix in {".py", ".pyd"}
            or p.name in {"METADATA", "LICENSE", "LICENSE.txt"}
        )
    ]
    files += [
        {"path": p.relative_to(ROOT).as_posix(), "sha256": sha256(p)}
        for p in sorted(code)
    ]
    for name, version in VERSIONS.items():
        if importlib.metadata.version(name) != version:
            raise ValueError("Wrong prepared dependency: " + name)
    # Inputs were independently checked in this phase; the future execution rechecks all frozen hashes.
    seal = {
        "status": "PHASE2_PREPARED_PENDING_RUNNER_REVIEW_NO_INFERENCE",
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "inference_executed": False,
        "inputs_manifest": files[0],
        "images": 144,
        "visuals": 12,
        "versions": VERSIONS,
        "python": sys.version,
        "files": files,
        "future_protocol": {
            "forwards": 288,
            "profiles": 18,
            "records": 2592,
            "CPU_intra_threads": 2,
            "CPU_inter_threads": 1,
            "mask_confidence": 0.35,
            "caps": [20, 100],
            "pre_cap": 300,
            "raw_outputs_serialized": False,
        },
        "input_inspection": "preflight-gt-inspection.json and preflight-gt-contact.png: independent before-prediction GT audit, not model quality",
    }
    path.write_text(json.dumps(seal, indent=2), encoding="utf-8")
    print(
        json.dumps(
            {
                "seal": str(path.relative_to(ROOT)),
                "sha256": sha256(path),
                "files": len(files),
                "inference_executed": False,
            }
        )
    )


if __name__ == "__main__":
    sys.path.insert(0, str(ROOT / ".tools/m1-yoloe-quality/pydeps"))
    create_seal()
