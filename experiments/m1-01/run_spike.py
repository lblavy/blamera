"""Bounded, disposable CPU experiment. Not application/production code."""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/reports/validation/m1-01"
MODEL = ROOT / "models/m1-01/yolo11n-seg/yolo11n-seg.pt"
os.environ.update({
    "YOLO_AUTOINSTALL": "False", "YOLO_OFFLINE": "True", "YOLO_VERBOSE": "False",
    "YOLO_CONFIG_DIR": str(ROOT / ".tools/m1-model-spike/yolo-config"),
    "MPLCONFIGDIR": str(ROOT / ".tools/m1-model-spike/mpl-config"),
    "TORCH_HOME": str(ROOT / ".tools/m1-model-spike/torch-cache"),
    "CUDA_VISIBLE_DEVICES": "", "OMP_NUM_THREADS": "4", "MKL_NUM_THREADS": "4",
})

import hashlib
from importlib.metadata import distributions
import json
import platform
import socket
import time
import traceback
import cv2
import numpy as np
import onnx
import onnxruntime as ort
import psutil
import torch
from ultralytics import YOLO, settings
from ultralytics.data.augment import LetterBox

OUT.mkdir(parents=True, exist_ok=True)
for key in ("sync", "clearml", "comet", "mlflow", "raytune", "wandb", "hub"):
    if key in settings:
        settings.update({key: False})
ort.disable_telemetry_events()
torch.set_num_threads(4)
torch.set_num_interop_threads(1)


def no_network(*args, **kwargs):
    raise RuntimeError("Network disabled during model loading, export and inference")


socket.socket.connect = no_network
socket.create_connection = no_network


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def measure(fn):
    warmups = []
    samples = []
    for target, count in ((warmups, 3), (samples, 20)):
        for _ in range(count):
            start = time.perf_counter_ns()
            fn()
            target.append((time.perf_counter_ns() - start) / 1_000_000)
    return {"warmup_ms": warmups, "samples_ms": samples,
            "median_ms": float(np.median(samples)), "p95_ms": float(np.percentile(samples, 95)),
            "min_ms": min(samples), "max_ms": max(samples)}


def main():
    assert torch.version.cuda is None and not torch.cuda.is_available()
    assert sha(MODEL) == "55ed65c56c91713d23e8402371c6c49a6fd84f257f7dce452e8d70e41dcbe152"
    image = cv2.imread(str(OUT / "astronaut.png"))
    assert image is not None and image.shape == (512, 512, 3)
    synthetic = np.zeros((512, 512, 3), dtype=np.uint8)
    cv2.rectangle(synthetic, (100, 100), (350, 400), (0, 0, 255), -1)
    cv2.circle(synthetic, (350, 150), 60, (255, 255, 0), -1)
    cv2.imwrite(str(OUT / "synthetic.png"), synthetic)
    start = time.perf_counter()
    native = YOLO(str(MODEL))
    native.model.to("cpu").eval()
    native.model.fuse(verbose=False)
    load_seconds = time.perf_counter() - start
    start = time.perf_counter()
    exported = Path(native.export(format="onnx", imgsz=640, batch=1, device="cpu", half=False,
                                  dynamic=False, simplify=False, opset=17, nms=False, verbose=False))
    export_seconds = time.perf_counter() - start
    graph = onnx.load(exported)
    onnx.checker.check_model(graph)
    options = ort.SessionOptions()
    options.intra_op_num_threads = 4
    options.inter_op_num_threads = 1
    start = time.perf_counter()
    session = ort.InferenceSession(str(exported), sess_options=options, providers=["CPUExecutionProvider"])
    session_seconds = time.perf_counter() - start
    exported_model = YOLO(str(exported), task="segment")
    head = native.model.model[-1]
    report = {
        "status": "running", "python": platform.python_version(), "platform": platform.platform(),
        "logical_cpus": os.cpu_count(), "threads": 4, "torch_cuda_version": torch.version.cuda,
        "onnx_providers": session.get_providers(), "network_socket_blocked": True,
        "settings": {k: settings[k] for k in ("sync", "clearml", "comet", "mlflow", "raytune", "wandb") if k in settings},
        "versions": {d.metadata["Name"]: d.version for d in distributions()},
        "weight": {"sha256": sha(MODEL), "bytes": MODEL.stat().st_size},
        "onnx": {"sha256": sha(exported), "bytes": exported.stat().st_size,
                 "metadata": {p.key: p.value for p in graph.metadata_props},
                 "inputs": [{"name": x.name, "shape": x.shape, "type": x.type} for x in session.get_inputs()],
                 "outputs": [{"name": x.name, "shape": x.shape, "type": x.type} for x in session.get_outputs()]},
        "timings_seconds": {"load_native": load_seconds, "export": export_seconds, "ort_session": session_seconds},
        "images": {},
    }
    for name, frame in (("astronaut", image), ("synthetic", synthetic)):
        letterboxed = LetterBox(new_shape=(640, 640), auto=False)(image=frame)
        array = np.ascontiguousarray(letterboxed[:, :, ::-1].transpose(2, 0, 1)[None], dtype=np.float32) / 255
        tensor = torch.from_numpy(array)
        head.export, head.format, head.dynamic = True, "onnx", False
        with torch.inference_mode():
            reference = [x.detach().cpu().numpy() for x in native.model(tensor)]
            actual = session.run(None, {session.get_inputs()[0].name: array})
            assert len(reference) == len(actual) == 2
            comparisons = []
            for left, right in zip(reference, actual):
                assert np.isfinite(left).all() and np.isfinite(right).all()
                np.testing.assert_allclose(left, right, rtol=1e-3, atol=1e-3)
                comparisons.append({"shape": list(left.shape), "max_abs_difference": float(np.max(np.abs(left-right))),
                                    "mean_abs_difference": float(np.mean(np.abs(left-right)))})
            raw_pt = measure(lambda: native.model(tensor))
            raw_ort = measure(lambda: session.run(None, {session.get_inputs()[0].name: array}))
        head.export = False
        kwargs = dict(source=frame, device="cpu", imgsz=640, conf=0.25, iou=0.7, verbose=False, save=False)
        pt_result = native.predict(**kwargs)[0]
        ort_result = exported_model.predict(**kwargs)[0]
        left_boxes = pt_result.boxes.data.cpu().numpy()
        right_boxes = ort_result.boxes.data.cpu().numpy()
        assert left_boxes.shape == right_boxes.shape
        np.testing.assert_allclose(left_boxes, right_boxes, rtol=1e-3, atol=0.1)
        mask_ious = []
        if len(left_boxes):
            left_masks = pt_result.masks.data.cpu().numpy() > 0.5
            right_masks = ort_result.masks.data.cpu().numpy() > 0.5
            for left, right in zip(left_masks, right_masks):
                intersection, union = np.logical_and(left, right).sum(), np.logical_or(left, right).sum()
                assert union > 0
                mask_ious.append(float(intersection / union))
            assert min(mask_ious) > 0.99
        if name == "astronaut":
            assert len(left_boxes) > 0 and mask_ious
        cv2.imwrite(str(OUT / (name + "-native-preview.png")), pt_result.plot())
        report["images"][name] = {
            "source_shape": list(frame.shape), "input_shape": list(array.shape), "raw_parity": comparisons,
            "boxes_xyxy_conf_class": left_boxes.tolist(), "classes": [native.names[int(x)] for x in pt_result.boxes.cls],
            "mask_shape": list(pt_result.masks.data.shape) if pt_result.masks is not None else None,
            "mask_nonzero_pixels": [int(np.count_nonzero(x)) for x in pt_result.masks.data.cpu().numpy()] if pt_result.masks is not None else [],
            "mask_ious_pt_onnx": mask_ious,
            "raw_native_forward": raw_pt, "raw_ort_forward": raw_ort,
            "native_pipeline": measure(lambda: native.predict(**kwargs)),
            "onnx_pipeline": measure(lambda: exported_model.predict(**kwargs)),
        }
        (OUT / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    report["sampled_rss_bytes_after_benchmarks"] = psutil.Process().memory_info().rss
    report["status"] = "passed"
    (OUT / "results.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({"status": report["status"], "model": report["weight"], "onnx": report["onnx"],
                      "images": {k: {"classes": v["classes"], "mask_ious": v["mask_ious_pt_onnx"],
                                     "native_median_ms": v["native_pipeline"]["median_ms"],
                                     "ort_median_ms": v["onnx_pipeline"]["median_ms"]} for k,v in report["images"].items()}}, indent=2))


if __name__ == "__main__":
    try:
        main()
    except Exception:
        (OUT / "failure.txt").write_text(traceback.format_exc(), encoding="utf-8")
        raise
