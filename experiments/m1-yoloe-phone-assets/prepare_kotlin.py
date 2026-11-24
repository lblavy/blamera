"""Additional CPU references from actual JVM tensors; never modifies v1 assets."""

import argparse
import base64
import importlib
import importlib.metadata
import json
import re
import sys
from pathlib import Path

from prepare_assets import (
    OUT,
    REPORT,
    ROOT,
    create_session,
    decode,
    digest,
    geometry,
    names_from,
    offline,
    save,
    save_json,
    sha,
    verify,
)


def validate_inputs(manifest, originals):
    rows = manifest["inputs"]
    expected = {row["id"]: row for row in originals}
    if (
        len(rows) != 14
        or len(expected) != 14
        or {row["id"] for row in rows} != set(expected)
    ):
        raise ValueError("Expected exactly the same fourteen unique inputs")
    changed = []
    for row in rows:
        source = expected[row["id"]]
        if (row["width"], row["height"]) != (source["width"], source["height"]):
            raise ValueError("Input dimensions changed")
        if (
            row["pixelSha256"] != source["pixelsSha256"]
            or row["pcPixelSha256"] != source["pixelsSha256"]
        ):
            raise ValueError("Decoded source RGB pixels changed")
        if row["pcTensorSha256"] != source["tensorSha256"]:
            raise ValueError("Parent PC tensor provenance changed")
        if not re.fullmatch("[0-9a-f]{64}", row["tensorSha256"]):
            raise ValueError("Invalid actual tensor SHA256")
        if row["tensorSha256"] != row["pcTensorSha256"]:
            changed.append(row["id"])
    return changed


def kotlin_catalog(source, inputs):
    replacements = {row["id"] + ".tensor.sha256": row for row in inputs}
    result, seen = [], set()
    for line in source.splitlines():
        key, separator, value = line.partition("=")
        if key in replacements:
            row = replacements[key]
            if value != row["pcTensorSha256"] or key in seen:
                raise ValueError("Original catalog tensor pin changed or duplicated")
            line = key + separator + row["tensorSha256"]
            seen.add(key)
        result.append(line)
    if seen != set(replacements):
        raise ValueError("Missing input tensor pin in original catalog")
    return "\n".join(result) + "\n"


def validate_variant(protocol):
    import numpy as np

    for record in protocol["sources"].values():
        verify(record)
    originals = json.loads((OUT / "inputs.json").read_text(encoding="utf-8"))
    manifest_path = verify(protocol["sources"]["manifest"])
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    changed = validate_inputs(manifest, originals)
    if changed != protocol["changedInputs"]:
        raise ValueError("Actual changed input set differs from reviewed protocol")
    for row in manifest["inputs"]:
        path = manifest_path.parent / (row["id"] + ".f32le")
        data = path.read_bytes()
        if len(data) != 1 * 3 * 640 * 640 * 4 or digest(data) != row["tensorSha256"]:
            raise ValueError("Actual JVM tensor bytes changed: " + row["id"])
        actual = np.frombuffer(data, dtype="<f4")
        parent_path = OUT / "tensors" / (row["id"] + ".f32le")
        if sha(parent_path) != row["pcTensorSha256"]:
            raise ValueError("Parent tensor changed")
        parent = np.fromfile(parent_path, dtype="<f4")
        if not np.isfinite(actual).all() or np.min(actual) < 0 or np.max(actual) > 1:
            raise ValueError("Invalid JVM tensor values")
        count = int(np.count_nonzero(actual != parent))
        maximum = float(np.max(np.abs(actual - parent)))
        if count != row["differentValues"] or abs(maximum - row["maxAbs"]) > 1e-9:
            raise ValueError("Tensor delta differs from JVM diagnostic")
    for record in protocol["references"]:
        verify(record)
    source = verify(protocol["sources"]["catalog"]).read_text(encoding="utf-8")
    properties = dict(
        line.split("=", 1)
        for line in source.splitlines()
        if line and not line.startswith("#")
    )
    for identifier in properties["files"].split(","):
        verify(
            {
                "file": properties[identifier + ".file"],
                "sha256": properties[identifier + ".sha256"],
                "size": int(properties[identifier + ".size"]),
            }
        )
    return manifest, originals, properties


def actual_reference(identifier, item, row, session, labels, tensor_folder):
    import numpy as np

    data = (tensor_folder / (item["id"] + ".f32le")).read_bytes()
    if digest(data) != row["tensorSha256"]:
        raise ValueError("JVM tensor changed immediately before inference")
    tensor = np.frombuffer(data, dtype="<f4").reshape(1, 3, 640, 640)
    outputs = session.run(None, {session.get_inputs()[0].name: tensor})
    from asset_core import validate_compact

    validate_compact(*outputs, len(labels))
    detections = decode(*outputs, item["projection"])
    geo = geometry()
    for detection in detections:
        network_box = detection["box"]
        mask = detection.pop("mask")
        detection.update(
            nameEnglish=labels[detection["classId"]],
            networkBox=network_box,
            box=geo.source_box(network_box, item["projection"]),
            maskBase64=base64.b64encode(mask).decode("ascii"),
            maskSha256=digest(mask),
            emptyMask=not any(mask),
        )
    return {
        "model": identifier,
        "input": item["id"],
        "width": item["width"],
        "height": item["height"],
        "tensorSha256": row["tensorSha256"],
        "parentTensorSha256": row["pcTensorSha256"],
        "projection": item["projection"],
        "maskWidth": 160,
        "maskHeight": 160,
        "maskBitOrder": "MSB-first row-major",
        "preprocess": "kotlin-float32",
        "detections": detections,
        "emptyMasks": sum(d["emptyMask"] for d in detections),
        "scope": "Compact CPU reference on actual JVM tensor; raw parent not re-inferred",
    }


def run_references(protocol, manifest, originals, properties):
    if (OUT / "kotlin-references").exists():
        raise ValueError("Preserve existing Kotlin references; no implicit retry")
    rows = {row["id"]: row for row in manifest["inputs"]}
    tensor_folder = (ROOT / protocol["sources"]["manifest"]["file"]).parent
    index = []
    for identifier in properties["models"].split(","):
        session = create_session(ROOT / properties[identifier + ".file"])
        labels = names_from(session, int(properties[identifier + ".classes"]))
        for item in originals:
            destination = (
                OUT / "kotlin-references" / identifier / (item["id"] + ".json")
            )
            if item["id"] in protocol["changedInputs"]:
                result = actual_reference(
                    identifier, item, rows[item["id"]], session, labels, tensor_folder
                )
                save_json(destination, result)
                mode = "new_compact_forward"
            else:
                source = OUT / "references" / identifier / destination.name
                result = json.loads(source.read_text(encoding="utf-8"))
                if result["tensorSha256"] != rows[item["id"]]["tensorSha256"]:
                    raise ValueError("Copied reference does not match actual JVM input")
                save(destination, source.read_bytes())
                mode = "byte_identical_v1_copy"
            index.append(
                {
                    "model": identifier,
                    "input": item["id"],
                    "mode": mode,
                    "file": destination.relative_to(ROOT).as_posix(),
                    "sha256": sha(destination),
                    "detections": len(result["detections"]),
                    "emptyMasks": result["emptyMasks"],
                }
            )
            print(json.dumps(index[-1]), flush=True)
        del session
    original = verify(protocol["sources"]["catalog"]).read_text(encoding="utf-8")
    header = "# Input-only variant: actual JVM float32 preprocessing; all 22 packaged leaves unchanged.\n"
    header += "preprocess.mode=kotlin-float32\n"
    header += "source.catalog.sha256=" + protocol["sources"]["catalog"]["sha256"] + "\n"
    header += (
        "source.kotlin.manifest.sha256="
        + protocol["sources"]["manifest"]["sha256"]
        + "\n"
    )
    header += (
        "source.kotlin.manifest.file=" + protocol["sources"]["manifest"]["file"] + "\n"
    )
    save(
        OUT / "catalog-kotlin.properties",
        (header + kotlin_catalog(original, manifest["inputs"])).encode("utf-8"),
    )
    save_json(
        REPORT / "kotlin-complete.json",
        {
            "status": "PC_KOTLIN_INPUT_REFERENCES_READY_PHONE_NOT_VALIDATED",
            "newForwards": len(protocol["changedInputs"]) * 4,
            "copiedReferences": (14 - len(protocol["changedInputs"])) * 4,
            "catalogSha256": sha(OUT / "catalog-kotlin.properties"),
            "provenance": protocol,
            "references": index,
        },
    )


def main():
    sys.dont_write_bytecode = True
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stage", choices=("validate", "references"))
    args = parser.parse_args()
    protocol = json.loads(
        Path(__file__).with_name("kotlin-protocol.json").read_text(encoding="utf-8")
    )
    for name, version in protocol["versions"].items():
        if importlib.metadata.version(name) != version:
            raise ValueError("Pinned environment drift: " + name)
    importlib.import_module("ssl")
    with offline():
        manifest, originals, properties = validate_variant(protocol)
        if args.stage == "references":
            run_references(protocol, manifest, originals, properties)
        else:
            print(
                json.dumps(
                    {
                        "stage": "validated_without_inference",
                        "changedInputs": protocol["changedInputs"],
                    }
                ),
                flush=True,
            )


if __name__ == "__main__":
    main()
