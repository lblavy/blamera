"""Frozen16-image local CPU research experiment, not production or Android."""

import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
os.environ.update(
    YOLO_AUTOINSTALL="False",
    YOLO_OFFLINE="True",
    YOLO_VERBOSE="False",
    YOLO_CONFIG_DIR=str(ROOT / ".tools/m1-yoloe/yolo-config"),
    MPLCONFIGDIR=str(ROOT / ".tools/m1-yoloe/mpl"),
    TORCH_HOME=str(ROOT / ".tools/m1-yoloe/torch-cache"),
    CUDA_VISIBLE_DEVICES="",
    OMP_NUM_THREADS="2",
    MKL_NUM_THREADS="2",
    OPENBLAS_NUM_THREADS="2",
)
import hashlib, json, socket, time, traceback
import cv2, numpy as np, onnxruntime as ort, psutil, torch
from metrics import match_instances, mask_iou
from postprocess import prepare, decode

OUT = ROOT / "build/reports/validation/m1-yoloe"
SPEC = json.loads(
    (ROOT / "experiments/m1-yoloe/corpus.json").read_text(encoding="utf-8")
)
BASE = ROOT / "models/m1-01/yolo11n-seg/yolo11n-seg.onnx"
PF = ROOT / "models/m1-yoloe/yoloe-26n-seg-pf.onnx"
NAMES80 = {int(k): v for k, v in SPEC["names80"].items()}
PFNAMES = {
    int(k): v for k, v in json.loads((OUT / "export.json").read_text())["names"].items()
}
MAPPING = SPEC["common_mapping"]
REVERSE = {i: int(c) for c, row in MAPPING.items() for i in row["pf_ids"]}


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def blocked(*a, **k):
    raise RuntimeError("Network blocked during evaluation")


socket.socket.connect = blocked
socket.create_connection = blocked
ort.disable_telemetry_events()
torch.set_num_threads(2)
torch.set_num_interop_threads(1)
cv2.setNumThreads(1)


def create(path):
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    options.inter_op_num_threads = 1
    options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
    return ort.InferenceSession(
        str(path), sess_options=options, providers=["CPUExecutionProvider"]
    )


def ground_truth(item, image):
    if item["id"].startswith("coco-"):
        h, w = image.shape[:2]
        objects = []
        for row in (ROOT / item["annotation"]).read_text().splitlines():
            values = np.asarray(list(map(float, row.split())))
            poly = values[1:].reshape(-1, 2) * [w, h]
            mask = np.zeros((h, w), np.uint8)
            cv2.fillPoly(mask, [np.rint(poly).astype(np.int32)], 1)
            objects.append(
                {
                    "class": int(values[0]),
                    "box": [
                        float(poly[:, 0].min()),
                        float(poly[:, 1].min()),
                        float(poly[:, 0].max()),
                        float(poly[:, 1].max()),
                    ],
                    "mask": mask.astype(bool),
                }
            )
        return objects
    return None


def summarize_times(samples):
    return {
        "n": len(samples),
        "median_ms": float(np.median(samples)),
        "p95_ms": float(np.percentile(samples, 95, method="linear")),
        "samples_ms": samples,
    }


def render(image, gt, predictions, names, destination):
    panels = []
    for title, objects, labels in [("GT (green)", gt or [], NAMES80)] + [
        (k, predictions[k], names[k]) for k in predictions
    ]:
        view = image.copy()
        for d in objects:
            if "segments" in d:
                cv2.polylines(
                    view,
                    [np.rint(s).astype(np.int32) for s in d["segments"]],
                    False,
                    (0, 255, 255),
                    1,
                    cv2.LINE_AA,
                )
            else:
                edges, _ = cv2.findContours(
                    d["mask"].astype(np.uint8), cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE
                )
                cv2.drawContours(view, edges, -1, (0, 220, 0), 1)
            text = labels.get(d["class"], str(d["class"])) + (
                f" {d['score']:.2f}" if "score" in d else ""
            )
            x, y = d["box"][:2]
            cv2.putText(
                view,
                text,
                (max(0, int(x)), max(12, int(y))),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.35,
                (0, 255, 255),
                1,
                cv2.LINE_AA,
            )
        w = 400
        h = round(view.shape[0] * w / view.shape[1])
        view = cv2.resize(view, (w, h))
        canvas = np.full((h + 28, w, 3), 30, np.uint8)
        canvas[28:] = view
        cv2.putText(
            canvas, title, (5, 18), cv2.FONT_HERSHEY_SIMPLEX, 0.45, (255, 255, 255), 1
        )
        panels.append(canvas)
    assert cv2.imwrite(str(destination), np.concatenate(panels, axis=1))


def evaluate():
    assert (
        sha(BASE) == "38325721a41fcd732b0a30512ffa8d564b1a8a74ca8c1d6b7e7f37609b2d321b"
    )
    assert (
        sha(ROOT / "models/m1-01/yolo11n-seg/yolo11n-seg.pt")
        == "55ed65c56c91713d23e8402371c6c49a6fd84f257f7dce452e8d70e41dcbe152"
    )
    assert torch.version.cuda is None
    sessions = {}
    loads = {}
    for name, path in [("baseline", BASE), ("pf", PF)]:
        start = time.perf_counter()
        sessions[name] = create(path)
        loads[name] = (time.perf_counter() - start) * 1000
    timings = {k: [] for k in sessions}
    warmups = {k: [] for k in sessions}
    rss = []
    records = []
    aggregate = {k: [] for k in ["baseline", "pf-common", "pf-full"]}
    process = psutil.Process()
    names = {"baseline": NAMES80, "pf-common": NAMES80, "pf-full": PFNAMES}
    (OUT / "visuals").mkdir(exist_ok=True)
    for image_index, item in enumerate(SPEC["images"]):
        image_path = ROOT / item["image"]
        assert sha(image_path) == item["sha256"]
        if item.get("annotation"):
            assert sha(ROOT / item["annotation"]) == item["annotation_sha256"]
        image = cv2.imread(str(image_path))
        assert image is not None
        start = time.perf_counter()
        tensor, projection = prepare(image)
        pre_ms = (time.perf_counter() - start) * 1000
        local = {k: [] for k in sessions}
        # Alternate order across repetitions and images; sessions remain warm and reuse same input.
        for rep in range(13):
            order = (
                ["baseline", "pf"]
                if (rep + image_index) % 2 == 0
                else ["pf", "baseline"]
            )
            for name in order:
                tick = time.perf_counter()
                values = sessions[name].run(None, {"images": tensor})
                elapsed = (time.perf_counter() - tick) * 1000
                if rep < 3:
                    warmups[name].append(elapsed)
                else:
                    timings[name].append(elapsed)
                    local[name].append(elapsed)
                rss.append(
                    {
                        "phase": "warm-forward",
                        "model": name,
                        "image": item["id"],
                        "rss_bytes": process.memory_info().rss,
                    }
                )
                del values
        predictions = {}
        stages = {}
        diagnostics = {}
        for name in ["baseline", "pf"]:
            values = sessions[name].run(None, {"images": tensor})
            assert all(np.isfinite(a).all() for a in values)
            configs = ["baseline"] if name == "baseline" else ["pf-common", "pf-full"]
            for config in configs:
                mapping = MAPPING if config == "pf-common" else None
                tick = time.perf_counter()
                pred, phase = decode(
                    values[0],
                    values[1],
                    projection,
                    NAMES80 if name == "baseline" else PFNAMES,
                    mapping,
                )
                phase["total_post_ms"] = (time.perf_counter() - tick) * 1000
                predictions[config] = pred
                stages[config] = phase
                larger, _ = decode(
                    values[0],
                    values[1],
                    projection,
                    NAMES80 if name == "baseline" else PFNAMES,
                    mapping,
                    max_det=100,
                )
                diagnostics[config] = {
                    "max100_count": len(larger),
                    "max20_count": len(pred),
                }
            del values
        gt = ground_truth(item, image)
        quality = {}
        if gt is not None:
            for config, pred in predictions.items():
                common = (
                    [
                        {**d, "class": REVERSE[d["class"]]}
                        for d in pred
                        if d["class"] in REVERSE
                    ]
                    if config == "pf-full"
                    else pred
                )
                quality[config] = match_instances(common, gt)
                aggregate[config].append(quality[config])
        if item["id"].startswith("ADE"):
            annotation = cv2.imread(
                str(ROOT / item["annotation"]), cv2.IMREAD_UNCHANGED
            )
            assert annotation.shape == image.shape[:2]
            valid = annotation != 0
            for config, pred in predictions.items():
                quality[config] = {}
                for label, classid in SPEC["semantic_extension"].items():
                    predicted = np.zeros(annotation.shape, bool)
                    for d in pred:
                        if config == "pf-full" and PFNAMES[d["class"]] == label:
                            predicted |= d["mask"]
                    target = annotation == classid
                    quality[config][label] = {
                        "intersection_union_iou": mask_iou(predicted, target, valid),
                        "gt_pixels": int(np.count_nonzero(target & valid)),
                        "pred_pixels": int(np.count_nonzero(predicted & valid)),
                    }
        serial = {
            k: [
                {
                    key: value
                    for key, value in d.items()
                    if key not in ("mask", "segments")
                }
                | {
                    "mask_pixels": int(d["mask"].sum()),
                    "contour_segments": len(d["segments"]),
                    "name": names[k].get(d["class"]),
                }
                for d in pred
            ]
            for k, pred in predictions.items()
        }
        records.append(
            {
                "id": item["id"],
                "image_shape": list(image.shape),
                "preprocessing_ms": pre_ms,
                "inference": {k: summarize_times(v) for k, v in local.items()},
                "postprocess": stages,
                "predictions": serial,
                "quality": quality,
                "cap100_diagnostic": diagnostics,
            }
        )
        render(image, gt, predictions, names, OUT / "visuals" / f"{item['id']}.png")
        masks = {
            f"{k}-{i}": d["mask"]
            for k, preds in predictions.items()
            for i, d in enumerate(preds)
        }
        np.savez_compressed(OUT / f"masks-{item['id']}.npz", **masks)
        (OUT / "partial-results.json").write_text(
            json.dumps(records, indent=2), encoding="utf-8"
        )
        print(
            item["id"],
            {k: len(v) for k, v in predictions.items()},
            "ORT medians",
            {k: round(np.median(v), 2) for k, v in local.items()},
            flush=True,
        )
    totals = {}
    for config, rows in aggregate.items():
        tp = sum(r["tp"] for r in rows)
        fp = sum(r["fp"] for r in rows)
        fn = sum(r["fn"] for r in rows)
        miou = [i for r in rows for i in r["matched_mask_iou"] if i is not None]
        totals[config] = {
            "tp": tp,
            "fp": fp,
            "fn": fn,
            "precision": tp / (tp + fp) if tp + fp else None,
            "recall": tp / (tp + fn) if tp + fn else None,
            "matched_mask_iou_mean": float(np.mean(miou)) if miou else None,
            "matched_mask_iou_n": len(miou),
        }
    result = {
        "protocol_sha256": sha(ROOT / "experiments/m1-yoloe/corpus.json"),
        "models": {
            "baseline": {"sha256": sha(BASE), "bytes": BASE.stat().st_size},
            "pf": {"sha256": sha(PF), "bytes": PF.stat().st_size},
        },
        "session_load_ms": loads,
        "warm_inference": {k: summarize_times(v) for k, v in timings.items()},
        "warmup_samples_ms": warmups,
        "rss_samples": rss,
        "rss_sample_max_bytes": max(x["rss_bytes"] for x in rss),
        "rss_note": "shared Python process with PyTorch import+two ORT sessions; sampled not peak and not Android",
        "threads": {
            "ort_intra": 2,
            "ort_inter": 1,
            "pytorch_intra": torch.get_num_threads(),
            "pytorch_inter": torch.get_num_interop_threads(),
            "process_threads_sample": process.num_threads(),
        },
        "common_instance_totals": totals,
        "records": records,
        "cold": {},
        "parity": {},
    }
    (OUT / "results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print("TOTALS", json.dumps(totals), flush=True)


if __name__ == "__main__":
    try:
        evaluate()
    except Exception:
        (OUT / "failure.txt").write_text(traceback.format_exc(), encoding="utf-8")
        raise
