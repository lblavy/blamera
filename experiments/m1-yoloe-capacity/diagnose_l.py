"""Bounded L-only raw-channel diagnosis, existing export and two historical fixtures only."""

from pathlib import Path
import os, sys, json, ssl
from capacity_adapter import require_size
from runner_core import sha256

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "build/reports/validation/m1-yoloe-capacity"


def channel_error(left, right, offset=0):
    import numpy as np

    errors = np.abs(left - right)
    position = np.unravel_index(int(np.argmax(errors)), errors.shape)
    a, b = float(left[position]), float(right[position])
    return {
        "max_abs": float(errors[position]),
        "position_local": [int(v) for v in position],
        "raw_channel": int(position[1] + offset),
        "native": a,
        "onnx": b,
        "relative_at_max": abs(a - b) / max(abs(a), abs(b), 1e-12),
    }


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
    from study_runtime import blocked_network

    with blocked_network():
        import torch, onnxruntime as ort, cv2, numpy as np
        from ultralytics import YOLOE, settings
        from legacy_geometry import prepare
        from study_postprocess import FrameDecoder, FrameMasks, validate_raw
        from protocol_core import match_boxes

        for key in ("sync", "clearml", "comet", "mlflow", "raytune", "wandb", "hub"):
            if key in settings:
                settings.update({key: False})
        torch.set_num_threads(2)
        torch.set_num_interop_threads(1)
        checkpoint = ROOT / "models/m1-yoloe-capacity/l/yoloe-26l-seg-pf.pt"
        path = checkpoint.with_suffix(".onnx")
        if (
            sha256(checkpoint)
            != "8b0ff5ecfd5baa0ebed509efb00aacff52faae2f6770e7ffa2a223231b9653c0"
        ):
            raise ValueError("Checkpoint changed")
        model = YOLOE(str(checkpoint))
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
        rows = []
        for identifier in ["nasa-astronaut", "synthetic-negative"]:
            fixture = next(row for row in corpus["images"] if row["id"] == identifier)
            if sha256(ROOT / fixture["image"]) != fixture["sha256"]:
                raise ValueError("Fixture drift")
            tensor, projection = prepare(cv2.imread(str(ROOT / fixture["image"])))
            with torch.inference_mode():
                native = [
                    v.detach().cpu().numpy()
                    for v in model.model(torch.from_numpy(tensor))
                ]
            actual = session.run(None, {session.get_inputs()[0].name: tensor})
            validate_raw(*native, 4585)
            validate_raw(*actual, 4585)
            channels = {
                "coordinates": channel_error(native[0][:, :4], actual[0][:, :4]),
                "scores": channel_error(native[0][:, 4:4589], actual[0][:, 4:4589], 4),
                "coefficients": channel_error(
                    native[0][:, 4589:], actual[0][:, 4589:], 4589
                ),
                "prototypes": channel_error(native[1], actual[1]),
            }
            a, b = native[0][0, 4:4589], actual[0][0, 4:4589]
            ca, cb = a.argmax(axis=0), b.argmax(axis=0)
            sa, sb = a.max(axis=0), b.max(axis=0)
            argmax = {
                "all_anchor_differences": int(np.count_nonzero(ca != cb)),
                "above_confidence": {
                    str(t): int(np.count_nonzero((ca != cb) & ((sa >= t) | (sb >= t))))
                    for t in [0.2, 0.35, 0.5]
                },
            }
            nd, ad = (
                FrameDecoder(*native, projection, 4585),
                FrameDecoder(*actual, projection, 4585),
            )
            nm, am = (
                FrameMasks(*native, projection, 4585),
                FrameMasks(*actual, projection, 4585),
            )
            profiles = []
            for agnostic in [True, False]:
                for score in [0.2, 0.35, 0.5]:
                    for cap in [20, 100]:
                        left, _ = nd.select(score, agnostic, cap)
                        right, _ = ad.select(score, agnostic, cap)
                        truth = [
                            {
                                **v,
                                "target": v["class"],
                                "category_id": v["class"],
                                "id": v["anchor"],
                            }
                            for v in left
                        ]
                        predictions = [
                            {
                                **v,
                                "target": v["class"],
                                "possible_categories": [v["class"]],
                            }
                            for v in right
                        ]
                        matches = match_boxes(predictions, truth)
                        ious = []
                        if score == 0.35:
                            for ri, li in matches:
                                m, n = am.get(right[ri]), nm.get(left[li])
                                union = np.count_nonzero(m | n)
                                ious.append(
                                    float(np.count_nonzero(m & n) / union)
                                    if union
                                    else 1.0
                                )
                        profiles.append(
                            {
                                "agnostic": agnostic,
                                "confidence": score,
                                "cap": cap,
                                "native_count": len(left),
                                "onnx_count": len(right),
                                "matches": len(matches),
                                "same_anchor_class_order": [
                                    (v["anchor"], v["class"]) for v in left
                                ]
                                == [(v["anchor"], v["class"]) for v in right],
                                "mask_iou_min": min(ious, default=None),
                            }
                        )
            rows.append(
                {
                    "id": identifier,
                    "fixture": fixture["image"],
                    "sha256": fixture["sha256"],
                    "scale": projection["scale"],
                    "channel_errors": channels,
                    "argmax": argmax,
                    "profiles": profiles,
                }
            )
            del native, actual, nd, ad, nm, am
        result = {
            "status": "DIAGNOSIS_COMPLETE_PENDING_PARITY_ARBITRATION",
            "existing_onnx": {
                "path": str(path.relative_to(ROOT).as_posix()),
                "sha256": sha256(path),
                "bytes": path.stat().st_size,
            },
            "no_reexport": True,
            "original_tolerance_unchanged": True,
            "no144_predictions": True,
            "fixtures": rows,
        }
        (BASE / "l/diagnosis.json").write_text(
            json.dumps(result, indent=2), encoding="utf-8", newline="\n"
        )
        print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
