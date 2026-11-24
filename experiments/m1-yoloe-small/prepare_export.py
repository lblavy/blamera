"""Bounded export/parity spike on NASA + uniform synthetic, never the 144 quality inputs."""

from pathlib import Path
import os, sys, json, ast, importlib.metadata, time
from small_adapter import vocabulary_translation, canonical_raw
from runner_core import sha256

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/reports/validation/m1-yoloe-small"
CHECKPOINT = ROOT / "models/m1-yoloe-small/yoloe-26s-seg-pf.pt"
EXPECTED = "01605da3f2e0e5325df45d5f5eef8e40b5b5c7d085399be3c33ac638258b150a"


def main():
    os.environ.update(
        YOLO_AUTOINSTALL="False",
        YOLO_OFFLINE="True",
        YOLO_VERBOSE="False",
        YOLO_CONFIG_DIR=str(ROOT / ".tools/m1-yoloe-small/yolo-config"),
        MPLCONFIGDIR=str(ROOT / ".tools/m1-yoloe-small/mpl"),
        TORCH_HOME=str(ROOT / ".tools/m1-yoloe-small/torch-cache"),
        CUDA_VISIBLE_DEVICES="",
        OMP_NUM_THREADS="2",
        MKL_NUM_THREADS="2",
    )
    if sha256(CHECKPOINT) != EXPECTED:
        raise ValueError("Official checkpoint checksum changed")
    target = CHECKPOINT.with_suffix(".onnx")
    if target.exists():
        raise ValueError("Preserve existing export; do not overwrite")
    for name, expected in {
        "ultralytics": "8.4.190",
        "torch": "2.14.1+cpu",
        "onnx": "1.23.2",
        "onnxruntime": "1.31.0",
    }.items():
        if importlib.metadata.version(name) != expected:
            raise ValueError("Pinned environment drift: " + name)
    from study_runtime import blocked_network
    import ssl  # SSL subclasses the real socket type at import; no network operation.

    with blocked_network():
        import torch, onnx, onnxruntime as ort, cv2, numpy as np
        from ultralytics import YOLOE, settings
        from legacy_geometry import prepare
        from study_postprocess import FrameDecoder, FrameMasks, validate_raw
        from protocol_core import match_boxes

        for key in ("sync", "clearml", "comet", "mlflow", "raytune", "wandb", "hub"):
            if key in settings:
                settings.update({key: False})
        torch.set_num_threads(2)
        torch.set_num_interop_threads(1)
        if torch.version.cuda is not None:
            raise ValueError("CPU-only environment required")
        model = YOLOE(str(CHECKPOINT))
        names = dict(model.names)
        historical = json.loads(
            (ROOT / "experiments/m1-yoloe/manifest.json").read_text(encoding="utf-8")
        )
        canonical = {
            int(key): value for key, value in historical["exports"]["names"].items()
        }
        translation = vocabulary_translation(canonical, names)
        if len(translation) != 4585:
            raise ValueError("Expected full 4585 vocabulary")
        params = dict(
            format="onnx",
            imgsz=640,
            batch=1,
            device="cpu",
            half=False,
            dynamic=False,
            simplify=False,
            opset=17,
            nms=False,
            end2end=False,
            verbose=False,
        )
        start = time.perf_counter()
        path = Path(model.export(**params))
        elapsed = time.perf_counter() - start
        if path.resolve() != target.resolve():
            raise ValueError("Unexpected export path")
        graph = onnx.load(path)
        onnx.checker.check_model(graph)
        metadata = {item.key: item.value for item in graph.metadata_props}
        exported = ast.literal_eval(metadata["names"])
        if exported != names:
            raise ValueError("Export vocabulary/order differs from checkpoint")
        model.model.to("cpu").eval()
        model.model.fuse(verbose=False)
        head = model.model.model[-1]
        head.export = True
        head.format = "onnx"
        head.dynamic = False
        head.end2end = False
        ort.disable_telemetry_events()
        options = ort.SessionOptions()
        options.intra_op_num_threads = 2
        options.inter_op_num_threads = 1
        options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
        options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        session = ort.InferenceSession(
            str(path), options, providers=["CPUExecutionProvider"]
        )
        corpus = json.loads(
            (ROOT / "experiments/m1-yoloe/corpus.json").read_text(encoding="utf-8")
        )
        parity = []
        for identifier in ["nasa-astronaut", "synthetic-negative"]:
            fixture = next(row for row in corpus["images"] if row["id"] == identifier)
            image_path = ROOT / fixture["image"]
            if sha256(image_path) != fixture["sha256"]:
                raise ValueError("Parity fixture changed")
            tensor, projection = prepare(cv2.imread(str(image_path)))
            with torch.inference_mode():
                native = model.model(torch.from_numpy(tensor))
            if not isinstance(native, (tuple, list)) or len(native) != 2:
                raise ValueError("Native branch not raw segmentation")
            native = [value.detach().cpu().numpy() for value in native]
            actual = session.run(None, {session.get_inputs()[0].name: tensor})
            validate_raw(*native, 4585)
            validate_raw(*actual, 4585)
            errors = [float(np.max(np.abs(a - b))) for a, b in zip(native, actual)]
            if errors[0] > 0.02 or errors[1] > 0.005:
                raise ValueError(
                    "Native/ONNX outputs exceed preregistered tolerances: "
                    + str(errors)
                )
            left, _ = FrameDecoder(*native, projection, 4585).select(0.35, True, 20)
            right, _ = FrameDecoder(*actual, projection, 4585).select(0.35, True, 20)
            to_pred = lambda rows: [
                {**r, "target": r["class"], "possible_categories": [r["class"]]}
                for r in rows
            ]
            truth = [
                {
                    **r,
                    "target": r["class"],
                    "category_id": r["class"],
                    "id": r["anchor"],
                }
                for r in left
            ]
            matches = match_boxes(to_pred(right), truth)
            if len(matches) != len(left) or len(matches) != len(right):
                raise ValueError("Native/ONNX detection mismatch")
            nc = FrameMasks(*native, projection, 4585)
            ac = FrameMasks(*actual, projection, 4585)
            ious = []
            for ri, li in matches:
                a, b = ac.get(right[ri]), nc.get(left[li])
                union = np.count_nonzero(a | b)
                ious.append(float(np.count_nonzero(a & b) / union) if union else 1.0)
            if min(ious, default=1.0) < 0.99:
                raise ValueError("Mask parity below .99")
            if identifier == "nasa-astronaut" and not left:
                raise ValueError("NASA parity must exercise a nonempty mask/detection")
            parity.append(
                {
                    "id": identifier,
                    "fixture": fixture["image"],
                    "fixture_sha256": fixture["sha256"],
                    "max_abs": errors,
                    "shape": [list(v.shape) for v in actual],
                    "detections": len(left),
                    "matched_mask_iou_min": min(ious, default=None),
                    "finite": True,
                }
            )
            del native, actual, nc, ac
        del session, options, model
        proof = {
            "status": "EXPORT_PARITY_READY_NO_144_PREDICTIONS",
            "checkpoint": {
                "path": str(CHECKPOINT.relative_to(ROOT).as_posix()),
                "sha256": EXPECTED,
                "bytes": CHECKPOINT.stat().st_size,
            },
            "onnx": {
                "path": str(path.relative_to(ROOT).as_posix()),
                "sha256": sha256(path),
                "bytes": path.stat().st_size,
            },
            "parameters": params,
            "export_seconds": elapsed,
            "metadata": metadata,
            "canonical_names": canonical,
            "small_names": names,
            "canonical_from_small_indices": translation,
            "vocabulary_identity": translation == list(range(4585)),
            "parity": parity,
            "tolerances": {
                "raw_max_abs": 0.02,
                "prototype_max_abs": 0.005,
                "mask_iou_min": 0.99,
            },
            "scope": "Export runs synthetic forwards; only two historical parity fixtures, none of the frozen144 inputs used",
            "volatile_metadata": "Exporter date recorded verbatim; no universal ONNX hash promised",
        }
        (OUT / "export-parity.json").write_text(
            json.dumps(proof, indent=2), encoding="utf-8"
        )
        print(
            json.dumps(
                {
                    key: proof[key]
                    for key in ["status", "onnx", "vocabulary_identity", "parity"]
                },
                indent=2,
            ),
            flush=True,
        )


if __name__ == "__main__":
    main()
