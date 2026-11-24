"""Explicit offline stages for personal debug assets; importing does no work."""

import argparse
import ast
import base64
import hashlib
import importlib.metadata
import importlib.util
import json
import os
import shutil
import socket
import sys
import time
from contextlib import contextmanager
from pathlib import Path

from asset_core import compare_detections, packed_mask, raw_rows, validate_compact

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "models/m1-yoloe-phone-assets"
REPORT = ROOT / "build/reports/validation/m1-yoloe-phone-assets"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def verify(record):
    path = ROOT / record["file"]
    if sha(path) != record["sha256"] or path.stat().st_size != record["size"]:
        raise ValueError("Frozen source changed: " + record["file"])
    return path


def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        if path.read_bytes() != data:
            raise ValueError("Preserve existing output: " + str(path))
        return
    path.write_bytes(data)


def save_json(path, value):
    save(path, (json.dumps(value, indent=2, ensure_ascii=False) + "\n").encode("utf-8"))


@contextmanager
def offline():
    original = socket.socket

    def blocked(*args, **kwargs):
        raise RuntimeError("Network forbidden during phone asset preparation")

    socket.socket = blocked
    try:
        yield
    finally:
        socket.socket = original


def geometry():
    folder = ROOT / "experiments/m1-yoloe"
    sys.path.insert(0, str(folder))
    try:
        spec = importlib.util.spec_from_file_location(
            "phone_geometry", folder / "postprocess.py"
        )
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module
    finally:
        sys.path.remove(str(folder))


def setup(protocol):
    sys.dont_write_bytecode = True
    for name, version in protocol["versions"].items():
        if importlib.metadata.version(name) != version:
            raise ValueError("Pinned dependency drift: " + name)
    os.environ.update(
        YOLO_AUTOINSTALL="False",
        YOLO_OFFLINE="True",
        YOLO_VERBOSE="False",
        YOLO_CONFIG_DIR=str(OUT / "runtime/yolo"),
        MPLCONFIGDIR=str(OUT / "runtime/mpl"),
        TORCH_HOME=str(OUT / "runtime/torch"),
        CUDA_VISIBLE_DEVICES="",
        OMP_NUM_THREADS="2",
        MKL_NUM_THREADS="2",
        OPENBLAS_NUM_THREADS="2",
    )
    OUT.mkdir(parents=True, exist_ok=True)
    REPORT.mkdir(parents=True, exist_ok=True)
    verify(protocol["manifest"])
    verify(protocol["license"])
    for model in protocol["models"].values():
        verify(model["raw"])
        if model["checkpoint"]:
            verify(model["checkpoint"])


def prepare(protocol):
    import cv2
    import numpy as np

    geo = geometry()
    inputs = []
    sources = [(name, verify(row), row) for name, row in protocol["fixtures"].items()]
    for record in protocol["images"]:
        if record["dataset"] == "lvis" and record["licence"]["id"] not in (1, 2, 4, 5):
            raise ValueError("Fixture license does not permit this derivative")
        path = ROOT / record["path"]
        if sha(path) != record["sha256"]:
            raise ValueError("Frozen JPEG changed: " + record["id"])
        sources.append((record["id"], path, record))
    for identifier, source, record in sources:
        image = cv2.imread(str(source), cv2.IMREAD_COLOR)
        if image is None:
            raise ValueError("Unreadable image: " + str(source))
        destination = OUT / "inputs" / (identifier + ".png")
        if source.suffix == ".png":
            data = source.read_bytes()
        else:
            success, encoded = cv2.imencode(".png", image)
            if not success:
                raise ValueError("PNG conversion failed")
            data = encoded.tobytes()
        save(destination, data)
        canonical = cv2.imread(str(destination), cv2.IMREAD_COLOR)
        if not np.array_equal(canonical, image):
            raise ValueError("PNG pixels differ from frozen CPU JPEG decode")
        tensor, projection = geo.prepare(canonical)
        tensor_bytes = tensor.astype("<f4", copy=False).tobytes()
        save(OUT / "tensors" / (identifier + ".f32le"), tensor_bytes)
        inputs.append(
            {
                "id": identifier,
                "file": destination.relative_to(ROOT).as_posix(),
                "sha256": digest(data),
                "size": len(data),
                "width": image.shape[1],
                "height": image.shape[0],
                "originalSha256": record["sha256"],
                "pixelsSha256": digest(canonical[:, :, ::-1].tobytes()),
                "tensorSha256": digest(tensor_bytes),
                "projection": projection,
            }
        )
    save_json(OUT / "inputs.json", inputs)
    save(OUT / "LICENSE-models-AGPL.txt", verify(protocol["license"]).read_bytes())
    notice = {
        "scope": "Private personal noncommercial research APK only; do not publish or redistribute the corpus/APK.",
        "conversion": "JPEG decoded OpenCV BGR -> lossless PNG, no ICC; pixel hash is row-major RGB8; tensor float32 little endian NCHW.",
        "nasa": "NASA Eileen Collins astronaut portrait, public domain; source https://raw.githubusercontent.com/scikit-image/scikit-image/v0.19.3/skimage/data/astronaut.png ; frozen skimage astronaut fixture.",
        "synthetic": "Project-generated geometric image; same immutable bytes as previous benchmark.",
        "images": protocol["images"],
        "models": protocol["models"],
        "modelLicense": protocol["license"],
        "usage": "ADE local noncommercial research; photos never publicly redistributed. Flickr image licenses remain individual per source below.",
    }
    save_json(OUT / "NOTICE.json", notice)
    print(json.dumps({"stage": "prepare", "inputs": len(inputs)}), flush=True)


def create_session(path):
    import onnxruntime as ort

    ort.disable_telemetry_events()
    options = ort.SessionOptions()
    options.intra_op_num_threads, options.inter_op_num_threads = 2, 1
    options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
    options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    return ort.InferenceSession(str(path), options, providers=["CPUExecutionProvider"])


def names_from(session, count):
    names = ast.literal_eval(session.get_modelmeta().custom_metadata_map["names"])
    if set(names) != set(range(count)):
        raise ValueError("Vocabulary IDs differ from fixed contiguous order")
    return [names[i] for i in range(count)]


def export_models(protocol):
    import onnx
    import torch
    from ultralytics import YOLOE, settings

    for key in ("sync", "clearml", "comet", "mlflow", "raytune", "wandb", "hub"):
        if key in settings:
            settings.update({key: False})
    torch.set_num_threads(2)
    torch.set_num_interop_threads(1)
    if torch.version.cuda is not None:
        raise ValueError("CPU-only PyTorch required")
    for identifier, model in protocol["models"].items():
        target = OUT / identifier / (identifier + ".onnx")
        proof = REPORT / ("export-" + identifier + ".json")
        if proof.exists():
            verify(json.loads(proof.read_text(encoding="utf-8"))["onnx"])
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            raise ValueError(
                "Unproven existing export; preserve for diagnosis: " + str(target)
            )
        start = time.perf_counter()
        if identifier == "baseline":
            shutil.copyfile(verify(protocol["baselineCompact"]), target)
        else:
            checkpoint = target.with_suffix(".pt")
            save(checkpoint, verify(model["checkpoint"]).read_bytes())
            actual = Path(
                YOLOE(str(checkpoint)).export(
                    **protocol["parameters"], agnostic_nms=True
                )
            )
            if actual.resolve() != target.resolve():
                raise ValueError("Unexpected export destination")
        graph = onnx.load(target)
        onnx.checker.check_model(graph)
        if not any(node.op_type == "NonMaxSuppression" for node in graph.graph.node):
            raise ValueError("Compact graph does not contain NMS")
        session = create_session(target)
        labels = names_from(session, model["classes"])
        reference = create_session(verify(model["raw"]))
        if labels != names_from(reference, model["classes"]):
            raise ValueError("Raw/compact vocabulary order drift")
        save_json(OUT / ("labels" + str(model["classes"]) + ".json"), labels)
        save_json(
            proof,
            {
                "onnx": {
                    "file": target.relative_to(ROOT).as_posix(),
                    "sha256": sha(target),
                    "size": target.stat().st_size,
                },
                "source": model,
                "parameters": {
                    **protocol["parameters"],
                    "agnostic_nms": model["agnostic"],
                },
                "metadata": {field.key: field.value for field in graph.metadata_props},
                "seconds": time.perf_counter() - start,
            },
        )
        print(
            json.dumps(
                {
                    "stage": "export",
                    "model": identifier,
                    "sha256": sha(target),
                    "bytes": target.stat().st_size,
                }
            ),
            flush=True,
        )
        del session, reference, graph


def decode(rows, proto, projection):
    return [
        {
            "classId": int(row[5]),
            "score": float(row[4]),
            "box": row[:4].tolist(),
            "mask": packed_mask(row, proto, projection),
        }
        for row in rows[0]
        if row[4] >= 0.35
    ]


def reference_case(model_id, spec, item, session, parent, labels):
    import numpy as np

    tensor_data = (OUT / "tensors" / (item["id"] + ".f32le")).read_bytes()
    if digest(tensor_data) != item["tensorSha256"]:
        raise ValueError("Prepared tensor changed")
    tensor = np.frombuffer(tensor_data, dtype="<f4").reshape(1, 3, 640, 640)
    compact = session.run(None, {session.get_inputs()[0].name: tensor})
    raw = parent.run(None, {parent.get_inputs()[0].name: tensor})
    validate_compact(*compact, spec["classes"])
    actual = decode(*compact, item["projection"])
    expected = decode(
        raw_rows(raw[0], spec["classes"], spec["agnostic"]), raw[1], item["projection"]
    )
    prototype_error = float(np.max(np.abs(compact[1] - raw[1])))
    if prototype_error > 0.005:
        raise ValueError("Prototype drift exceeds 0.005")
    comparison = {
        **compare_detections(actual, expected),
        "prototypeMaxAbs": prototype_error,
    }
    geo = geometry()
    serialized = []
    for detection in actual:
        network_box = detection["box"]
        mask = detection.pop("mask")
        serialized.append(
            {
                **detection,
                "nameEnglish": labels[detection["classId"]],
                "networkBox": network_box,
                "box": geo.source_box(network_box, item["projection"]),
                "maskBase64": base64.b64encode(mask).decode("ascii"),
                "maskSha256": digest(mask),
                "emptyMask": not any(mask),
            }
        )
    return {
        "model": model_id,
        "input": item["id"],
        "width": item["width"],
        "height": item["height"],
        "tensorSha256": item["tensorSha256"],
        "projection": item["projection"],
        "maskWidth": 160,
        "maskHeight": 160,
        "maskBitOrder": "MSB-first row-major",
        "detections": serialized,
        "emptyMasks": sum(d["emptyMask"] for d in serialized),
        "rawCompactParity": comparison,
    }


def references(protocol):
    inputs = json.loads((OUT / "inputs.json").read_text(encoding="utf-8"))
    for identifier, model in protocol["models"].items():
        proof = json.loads(
            (REPORT / ("export-" + identifier + ".json")).read_text(encoding="utf-8")
        )
        session = create_session(verify(proof["onnx"]))
        parent = create_session(verify(model["raw"]))
        labels = names_from(session, model["classes"])
        for item in inputs:
            path = OUT / "references" / identifier / (item["id"] + ".json")
            if path.exists():
                continue
            try:
                result = reference_case(
                    identifier, model, item, session, parent, labels
                )
                save_json(path, result)
            except Exception as error:
                save_json(
                    REPORT / "STOP.json",
                    {"model": identifier, "input": item["id"], "error": str(error)},
                )
                raise
            print(
                json.dumps(
                    {
                        "stage": "reference",
                        "model": identifier,
                        "input": item["id"],
                        "detections": len(result["detections"]),
                        "emptyMasks": result["emptyMasks"],
                        "parity": result["rawCompactParity"],
                    }
                ),
                flush=True,
            )
        del session, parent
    catalog(protocol, inputs)


def catalog(protocol, inputs):
    leaves = []
    lines = [
        "# Private debug-personal assets; no public corpus/APK redistribution.",
        "models=" + ",".join(protocol["models"]),
        "inputs=" + ",".join(r["id"] for r in inputs),
        "performance.inputs=nasa,lvis_371699",
    ]

    def leaf(identifier, path):
        leaves.append(identifier)
        lines.extend(
            [
                f"{identifier}.file={path.relative_to(ROOT).as_posix()}",
                f"{identifier}.asset={path.name}",
                f"{identifier}.size={path.stat().st_size}",
                f"{identifier}.sha256={sha(path)}",
            ]
        )

    for identifier, model in protocol["models"].items():
        leaf(identifier, OUT / identifier / (identifier + ".onnx"))
        lines.extend(
            [
                f"{identifier}.classes={model['classes']}",
                f"{identifier}.labels=labels{model['classes']}",
                f"{identifier}.source.sha256={model['raw']['sha256']}",
                f"{identifier}.agnostic={str(model['agnostic']).lower()}",
            ]
        )
    for count in (80, 4585):
        leaf("labels" + str(count), OUT / ("labels" + str(count) + ".json"))
    for item in inputs:
        identifier = item["id"]
        leaf(identifier, ROOT / item["file"])
        for suffix, key in [
            ("width", "width"),
            ("height", "height"),
            ("original.sha256", "originalSha256"),
            ("pixels.sha256", "pixelsSha256"),
            ("tensor.sha256", "tensorSha256"),
        ]:
            lines.append(f"{identifier}.{suffix}={item[key]}")
    leaf("license", OUT / "LICENSE-models-AGPL.txt")
    leaf("notice", OUT / "NOTICE.json")
    lines.insert(4, "files=" + ",".join(leaves))
    save(OUT / "catalog.properties", ("\n".join(lines) + "\n").encode("utf-8"))
    save_json(
        REPORT / "complete.json",
        {
            "status": "PC_REFERENCES_READY_PHONE_NOT_VALIDATED",
            "references": len(inputs) * 4,
            "catalogSha256": sha(OUT / "catalog.properties"),
            "scope": "14 frozen inputs; raw parent top300 vs compact native NMS without pre-cap; no 144-image quality claim",
        },
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stage", choices=("prepare", "export", "references"))
    args = parser.parse_args()
    protocol = json.loads(
        Path(__file__).with_name("protocol.json").read_text(encoding="utf-8")
    )
    setup(protocol)
    importlib.import_module(
        "ssl"
    )  # SSL subclasses the real socket type before blocking.

    with offline():
        {"prepare": prepare, "export": export_models, "references": references}[
            args.stage
        ](protocol)


if __name__ == "__main__":
    main()
