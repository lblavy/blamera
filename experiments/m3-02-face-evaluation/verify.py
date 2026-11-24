"""Verify artifact integrity and the experimental constant-input transformation."""

import json
from hashlib import sha256
from pathlib import Path

import cv2 as cv
import numpy as np
import onnx
import onnxruntime as ort
from benchmark import DATA, REPORT, ROOT, samples


def main():
    manifest = json.loads(
        (ROOT / "experiments/m3-02-face-evaluation/model-assets.json").read_text()
    )
    checked = []
    for record in manifest["files"]:
        name = record["name"]
        assert Path(name).name == name
        assert sha256((DATA / name).read_bytes()).hexdigest() == record["sha256"]
        checked.append(name)
    original = onnx.load(str(DATA / "sface.onnx"))
    constant = onnx.load(str(DATA / "sface-constant.onnx"))
    assert [value.SerializeToString() for value in original.graph.initializer] == [
        value.SerializeToString() for value in constant.graph.initializer
    ]
    assert [value.SerializeToString() for value in original.graph.node] == [
        value.SerializeToString() for value in constant.graph.node
    ]
    assert len(original.graph.input) == 175 and len(constant.graph.input) == 1
    options = ort.SessionOptions()
    options.log_severity_level = 3
    options.intra_op_num_threads = 1
    sessions = [
        ort.InferenceSession(
            str(DATA / file), options, providers=["CPUExecutionProvider"]
        )
        for file in ["sface.onnx", "sface-constant.onnx"]
    ]
    detector = cv.FaceDetectorYN.create(
        str(DATA / "yunet.onnx"), "", (640, 640), 0.8, 0.3, 5000
    )
    aligner = cv.FaceRecognizerSF.create(str(DATA / "sface.onnx"), "")
    cv.setNumThreads(1)
    errors, similarities = [], []
    for variants in samples().values():
        for image in variants.values():
            face = detector.detect(image)[1][0]
            aligned = aligner.alignCrop(image, face)
            blob = cv.dnn.blobFromImage(aligned, 1, (112, 112), (0, 0, 0), swapRB=True)
            values = [
                session.run(None, {"data": blob})[0].ravel() for session in sessions
            ]
            errors.append(float(np.max(np.abs(values[0] - values[1]))))
            normalized = [value / np.linalg.norm(value) for value in values]
            similarities.append(float(normalized[0] @ normalized[1]))
    assert max(errors) <= 1e-4
    assert min(similarities) >= 0.99999
    result = {
        "asset_hashes_checked": checked,
        "nodes_and_weights_identical": True,
        "original_inputs": 175,
        "constant_inputs": 1,
        "parity_samples": len(errors),
        "maximum_absolute_feature_error": max(errors),
        "minimum_normalized_cosine": min(similarities),
        "thresholds_asserted": {
            "maximum_absolute_error": 1e-4,
            "minimum_cosine": 0.99999,
        },
    }
    (REPORT / "verification.json").write_text(
        json.dumps(result, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
