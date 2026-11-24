"""Exploratory CPU measurements on two public NASA portraits, not an accuracy study."""

import argparse
import json
import platform
import statistics
import subprocess
import sys
import time
from importlib.metadata import version
from pathlib import Path

import cv2 as cv
import numpy as np
import onnxruntime as ort
import psutil

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "models/m3-02"
REPORT = ROOT / "build/reports/validation/m3-02-face-evaluation"
MODELS = {
    "sface": "sface.onnx",
    "sface_constant": "sface-constant.onnx",
    "mobilefacenet": "w600k_mbf.onnx",
    "arcface_r50": "w600k_r50.onnx",
}


def fit(image):
    height, width = image.shape[:2]
    scale = min(640 / width, 640 / height)
    resized = cv.resize(image, (round(width * scale), round(height * scale)))
    canvas = np.zeros((640, 640, 3), np.uint8)
    canvas[: resized.shape[0], : resized.shape[1]] = resized
    return canvas


def samples():
    output = {}
    for name, file in [("collins", "astronaut.png"), ("glenn", "john-glenn.jpg")]:
        original = fit(cv.imread(str(DATA / file)))
        output[name] = {
            "reference": original,
            "dark": np.clip(original.astype(np.float32) * 0.55, 0, 255).astype(
                np.uint8
            ),
            "blur": cv.GaussianBlur(original, (7, 7), 0),
            "low_resolution": cv.resize(cv.resize(original, (160, 160)), (640, 640)),
            "rotation_15": cv.warpAffine(
                original, cv.getRotationMatrix2D((320, 320), 15, 1), (640, 640)
            ),
        }
    return output


def duration(action, repeats=5):
    for _ in range(2):
        action()
    measurements = []
    for _ in range(repeats):
        start = time.perf_counter_ns()
        action()
        measurements.append((time.perf_counter_ns() - start) / 1_000_000)
    return {
        "samples_ms": measurements,
        "median_ms": statistics.median(measurements),
        "min_ms": min(measurements),
        "max_ms": max(measurements),
    }


def worker(name, threads):
    cv.setNumThreads(1)
    ort.set_default_logger_severity(3)
    process = psutil.Process()
    rss_initial = process.memory_info().rss
    start = time.perf_counter_ns()
    detector = cv.FaceDetectorYN.create(
        str(DATA / "yunet.onnx"), "", (640, 640), 0.8, 0.3, 5000
    )
    aligner = cv.FaceRecognizerSF.create(str(DATA / "sface.onnx"), "")
    options = ort.SessionOptions()
    options.log_severity_level = 3
    options.intra_op_num_threads = threads
    options.inter_op_num_threads = 1
    session = ort.InferenceSession(
        str(DATA / MODELS[name]), options, providers=["CPUExecutionProvider"]
    )
    load_ms = (time.perf_counter_ns() - start) / 1_000_000
    rss_loaded = process.memory_info().rss
    input_name = session.get_inputs()[0].name

    def embedding(aligned):
        blob = cv.dnn.blobFromImage(aligned, 1.0, (112, 112), (0, 0, 0), swapRB=True)
        if not name.startswith("sface"):
            blob = (blob - 127.5) / 127.5
        vector = session.run(None, {input_name: blob})[0].reshape(-1)
        return vector / np.linalg.norm(vector)

    def detect(image):
        detected = detector.detect(image)[1]
        return (
            []
            if detected is None
            else sorted(detected, key=lambda face: -float(face[2] * face[3]))
        )

    def pipeline(image):
        face = detect(image)[0]
        return embedding(aligner.alignCrop(image, face))

    images = samples()
    vectors = {}
    face_counts = {}
    for identity, variants in images.items():
        vectors[identity] = {}
        for variant, image in variants.items():
            faces = detect(image)
            face_counts[f"{identity}/{variant}"] = len(faces)
            if faces:
                vectors[identity][variant] = embedding(
                    aligner.alignCrop(image, faces[0])
                )
    blank = np.zeros((640, 640, 3), np.uint8)
    face_counts["negative_uniform"] = len(detect(blank))
    collage = np.concatenate(
        [images["collins"]["reference"], images["glenn"]["reference"]], axis=1
    )
    face_counts["two_identities_collage"] = len(detect(fit(collage)))
    reference = images["collins"]["reference"]
    aligned = aligner.alignCrop(reference, detect(reference)[0])
    positive = []
    negative = []
    for identity, variants in vectors.items():
        base = variants["reference"]
        other = vectors["glenn" if identity == "collins" else "collins"]["reference"]
        for variant, vector in variants.items():
            if variant != "reference":
                positive.append(
                    {
                        "identity": identity,
                        "variant": variant,
                        "cosine": float(base @ vector),
                    }
                )
            negative.append(
                {
                    "query_identity": identity,
                    "variant": variant,
                    "cosine": float(other @ vector),
                }
            )
    parity = None
    if name.startswith("sface"):
        oracle = aligner.feature(aligned).reshape(-1)
        oracle /= np.linalg.norm(oracle)
        parity = float(oracle @ embedding(aligned))
    result = {
        "model": name,
        "ort_threads": threads,
        "bytes": (DATA / MODELS[name]).stat().st_size,
        "dimensions": len(vectors["collins"]["reference"]),
        "graph_inputs": [
            {"name": value.name, "shape": value.shape, "type": value.type}
            for value in session.get_inputs()
        ],
        "graph_outputs": [
            {"name": value.name, "shape": value.shape, "type": value.type}
            for value in session.get_outputs()
        ],
        "providers": session.get_providers(),
        "load_ms_includes_yunet_and_sface_aligner": load_ms,
        "detector_ms": duration(lambda: detect(reference)),
        "embedding_ms": duration(lambda: embedding(aligned)),
        "sface_opencv_embedding_ms": duration(lambda: aligner.feature(aligned))
        if name.startswith("sface")
        else None,
        "detect_align_embed_ms": duration(lambda: pipeline(reference)),
        "face_counts": face_counts,
        "positive_derived_same_photo": positive,
        "negative_two_distinct_identities": negative,
        "sface_opencv_onnxruntime_normalized_cosine": parity,
        "rss_initial_bytes": rss_initial,
        "rss_loaded_bytes": rss_loaded,
        "rss_after_inference_bytes": process.memory_info().rss,
        "peak_process_bytes": getattr(process.memory_info(), "peak_wset", None),
        "memory_limitation": "Fresh process per candidate; includes both OpenCV SFace alignment session and YuNet even for ArcFace/MBF; not model-only RAM or Android RSS.",
    }
    print(json.dumps(result))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", choices=MODELS)
    parser.add_argument("--threads", type=int, choices=[1, 4], default=1)
    args = parser.parse_args()
    if args.model:
        worker(args.model, args.threads)
        return
    records = []
    for name in MODELS:
        completed = subprocess.run(
            [sys.executable, __file__, "--model", name, "--threads", str(args.threads)],
            capture_output=True,
            timeout=90,
            check=True,
        )
        (REPORT / f"{name}-threads{args.threads}.stderr.log").write_bytes(
            completed.stderr
        )
        record = json.loads(completed.stdout.decode())
        records.append(record)
        print(name, record["detect_align_embed_ms"]["median_ms"], flush=True)
    report = {
        "environment": {
            "platform": platform.platform(),
            "processor": platform.processor(),
            "python": sys.version,
            "packages": {
                name: version(name)
                for name in [
                    "numpy",
                    "onnxruntime",
                    "opencv-python-headless",
                    "pillow",
                    "psutil",
                ]
            },
            "ort_threads": args.threads,
            "opencv_threads": 1,
            "warmups": 2,
            "repeats": 5,
        },
        "protocol": "YuNet 640x640 letterbox; largest face; OpenCV SFace alignCrop 5 points 112x112 shared by every embedding candidate; RGB NCHW raw SFace, (RGB-127.5)/127.5 InsightFace.",
        "limitations": [
            "Two public NASA portraits only; transformations of the same image are not independent photos/sessions or a statistical identity test.",
            "Shared YuNet/SFace alignment is a controlled embedding comparison, not the official SCRFD+InsightFace complete pipeline.",
            "No personal gallery, no final thresholds, no FAR/TAR/recognition accuracy, no Android measurement.",
        ],
        "models": records,
    }
    (REPORT / f"results-threads{args.threads}.json").write_text(
        json.dumps(report, indent=2) + "\n", encoding="utf-8"
    )


if __name__ == "__main__":
    main()
