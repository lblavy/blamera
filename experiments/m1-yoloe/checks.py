"""Parity, cold lifecycle and separate deployment export; preserves raw and app models."""

from run_evaluation import (
    ROOT,
    OUT,
    SPEC,
    BASE,
    PF,
    NAMES80,
    PFNAMES,
    create,
    prepare,
    decode,
    sha,
    summarize_times,
)
from pathlib import Path
import hashlib
import gc, json, time
import numpy as np
import torch, onnx, onnxruntime as ort
from ultralytics import YOLO, YOLOE
from postprocess import source_box, source_mask, contours
from metrics import match_instances


def native_outputs(model, tensor):
    with torch.inference_mode():
        result = model.model(torch.from_numpy(tensor))
    assert isinstance(result, (tuple, list)) and len(result) == 2
    return [x.detach().cpu().numpy() for x in result]


def compact_decode(rows, proto, p):
    result = []
    for row in rows[0]:
        if row[4] < 0.35:
            continue
        box = row[:4]
        coeff = row[6:]
        logits = (coeff @ proto[0].reshape(32, -1)).reshape(160, 160)
        xs = np.arange(160) * 4
        ys = xs
        active = (
            (logits > 0)
            & (xs >= max(box[0], p["left"]))[None, :]
            & (xs < min(box[2], p["left"] + p["sw"]))[None, :]
            & (ys >= max(box[1], p["top"]))[:, None]
            & (ys < min(box[3], p["top"] + p["sh"]))[:, None]
        )
        if not active.any():
            continue
        result.append(
            {
                "class": int(row[5]),
                "score": float(row[4]),
                "box": source_box(box, p),
                "mask": source_mask(logits, box, p),
                "segments": contours(logits, box, p),
            }
        )
    return result


def run():
    import cv2

    result = json.loads((OUT / "results.json").read_text())
    test_ids = ["nasa-astronaut", "coco-000000000164"]
    items = [next(i for i in SPEC["images"] if i["id"] == iid) for iid in test_ids]
    inputs = [prepare(cv2.imread(str(ROOT / i["image"]))) for i in items]
    raw_sha = sha(PF)
    base_sha = sha(BASE)
    for name, path, klass, weights, names in [
        (
            "baseline",
            BASE,
            YOLO,
            ROOT / "models/m1-01/yolo11n-seg/yolo11n-seg.pt",
            NAMES80,
        ),
        ("pf", PF, YOLOE, ROOT / "models/m1-yoloe/yoloe-26n-seg-pf.pt", PFNAMES),
    ]:
        session = create(path)
        model = klass(str(weights))
        model.model.to("cpu").eval()
        model.model.fuse(verbose=False)
        head = model.model.model[-1]
        head.export = True
        head.format = "onnx"
        head.dynamic = False
        head.end2end = False
        torch.set_num_threads(2)
        for item, (tensor, projection) in zip(items, inputs):
            outputs = native_outputs(model, tensor)
            actual = session.run(None, {"images": tensor})
            errors = [float(np.max(np.abs(a - b))) for a, b in zip(outputs, actual)]
            assert all(np.isfinite(x).all() for x in outputs + actual)
            assert errors[0] <= 0.02 and errors[1] <= 0.005, errors
            pt, _ = decode(*outputs, projection, names)
            ox, _ = decode(*actual, projection, names)
            parity = match_instances(ox, pt)
            assert parity["fp"] == 0 and parity["fn"] == 0, parity
            assert min(parity["matched_mask_iou"], default=1) >= 0.99, parity
            result["parity"][name + "-" + item["id"]] = {
                "shapes": [list(a.shape) for a in actual],
                "max_abs": errors,
                "matched_mask_iou_min": min(parity["matched_mask_iou"], default=None),
                "detections": len(pt),
            }
            del outputs, actual, pt, ox
        del model
        gc.collect()
        for item, (tensor, projection) in zip(items, inputs):
            samples = []
            phases = []
            for repeat in range(13):
                start = time.perf_counter()
                data = path.read_bytes()
                assert hashlib.sha256(data).hexdigest() == (
                    base_sha if name == "baseline" else raw_sha
                )
                read_ms = (time.perf_counter() - start) * 1000
                opts = ort.SessionOptions()
                opts.intra_op_num_threads = 2
                opts.inter_op_num_threads = 1
                tick = time.perf_counter()
                cold = ort.InferenceSession(
                    data, sess_options=opts, providers=["CPUExecutionProvider"]
                )
                load_ms = (time.perf_counter() - tick) * 1000
                tick = time.perf_counter()
                outputs = cold.run(None, {"images": tensor})
                infer_ms = (time.perf_counter() - tick) * 1000
                tick = time.perf_counter()
                pred, stage = decode(*outputs, projection, names)
                post_ms = (time.perf_counter() - tick) * 1000
                elapsed = (time.perf_counter() - start) * 1000
                if repeat >= 3:
                    samples.append(elapsed)
                    phases.append(
                        {
                            "read_hash_ms": read_ms,
                            "session_ms": load_ms,
                            "inference_ms": infer_ms,
                            "python_post_contours_ms": post_ms,
                        }
                    )
                del outputs, cold, pred, data
                gc.collect()
            result["cold"][name + "-" + item["id"]] = {
                **summarize_times(samples),
                "phase_samples": phases,
                "note": "file read/hash + new session + inference + Python postprocess/contours; excludes preprocessing, renderer and GC; not Kotlin timing",
            }
        del session
        gc.collect()
    (OUT / "results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    # ONE separate compact export; never overwrite raw ONNX.
    folder = ROOT / "models/m1-yoloe/deployment"
    folder.mkdir(exist_ok=True)
    checkpoint = folder / "yoloe-26n-seg-pf.pt"
    checkpoint.write_bytes((ROOT / "models/m1-yoloe/yoloe-26n-seg-pf.pt").read_bytes())
    compact = Path(
        YOLOE(str(checkpoint)).export(
            format="onnx",
            imgsz=640,
            batch=1,
            device="cpu",
            half=False,
            dynamic=False,
            simplify=False,
            opset=17,
            nms=True,
            end2end=False,
            conf=0.35,
            iou=0.5,
            max_det=20,
            agnostic_nms=False,
            verbose=False,
        )
    )
    graph = onnx.load(compact)
    onnx.checker.check_model(graph)
    assert any(n.op_type == "NonMaxSuppression" for n in graph.graph.node)
    compact_session = create(compact)
    raw_session = create(PF)
    deployment = {
        "path": str(compact.relative_to(ROOT)),
        "sha256": sha(compact),
        "bytes": compact.stat().st_size,
        "metadata": {x.key: x.value for x in graph.metadata_props},
        "cases": [],
    }
    for item, (tensor, projection) in zip(items, inputs):
        raw = raw_session.run(None, {"images": tensor})
        small = compact_session.run(None, {"images": tensor})
        assert small[0].shape == (1, 20, 38) and small[1].shape == (1, 32, 160, 160)
        assert np.isfinite(small[0]).all() and np.isfinite(small[1]).all()
        assert np.max(np.abs(raw[1] - small[1])) < 0.005
        original, _ = decode(*raw, projection, PFNAMES)
        deployed = compact_decode(*small, projection)
        matched = match_instances(deployed, original)
        assert matched["fp"] == 0 and matched["fn"] == 0, matched
        assert min(matched["matched_mask_iou"], default=1) >= 0.99, matched
        durations = {"raw_no_nms": [], "compact_with_native_nms": []}
        for repeat in range(13):
            for key, sess in (
                [
                    ("raw_no_nms", raw_session),
                    ("compact_with_native_nms", compact_session),
                ]
                if repeat % 2
                else [
                    ("compact_with_native_nms", compact_session),
                    ("raw_no_nms", raw_session),
                ]
            ):
                tick = time.perf_counter()
                v = sess.run(None, {"images": tensor})
                ms = (time.perf_counter() - tick) * 1000
                if repeat >= 3:
                    durations[key].append(ms)
                del v
        deployment["cases"].append(
            {
                "id": item["id"],
                "output_shapes": [list(a.shape) for a in small],
                "raw_output_bytes": sum(a.nbytes for a in raw),
                "compact_output_bytes": sum(a.nbytes for a in small),
                "matched": matched,
                "timings": {k: summarize_times(v) for k, v in durations.items()},
                "note": "raw excludes NMS; compact includes native NMS without preliminary top300; not equal end-to-end comparisons",
            }
        )
        del raw, small
        gc.collect()
    result["deployment"] = deployment
    assert sha(PF) == raw_sha and sha(BASE) == base_sha
    (OUT / "results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    (OUT / "checks.json").write_text(
        json.dumps(
            {
                "parity": result["parity"],
                "cold": result["cold"],
                "deployment": deployment,
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    print(
        json.dumps(
            {
                "parity": result["parity"],
                "cold": {
                    k: {x: v[x] for x in ["median_ms", "p95_ms"]}
                    for k, v in result["cold"].items()
                },
                "deployment": {k: v for k, v in deployment.items() if k != "metadata"},
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    run()
