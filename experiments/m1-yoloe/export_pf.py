"""Exploratory PF export; no app imports, encoder, hidden downloads or GPU."""

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
)
import hashlib, json, socket, time
import torch
from ultralytics import YOLOE, settings
import onnx

for key in ("sync", "clearml", "comet", "mlflow", "raytune", "wandb", "hub"):
    if key in settings:
        settings.update({key: False})


def blocked(*a, **k):
    raise RuntimeError("Network forbidden during export/inference")


socket.socket.connect = blocked
socket.create_connection = blocked
torch.set_num_threads(2)
torch.set_num_interop_threads(1)
checkpoint = ROOT / "models/m1-yoloe/yoloe-26n-seg-pf.pt"
assert (
    hashlib.sha256(checkpoint.read_bytes()).hexdigest()
    == "ae805a9ac11c85214588b32da8248d68a516e1d071465e1e86a03cbfadb66ba7"
)
assert torch.version.cuda is None
model = YOLOE(str(checkpoint))
names = dict(model.names)
start = time.perf_counter()
path = Path(
    model.export(
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
)
graph = onnx.load(path)
onnx.checker.check_model(graph)
result = {
    "onnx": str(path.relative_to(ROOT)),
    "onnx_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
    "onnx_size": path.stat().st_size,
    "export_seconds": time.perf_counter() - start,
    "input": [
        {
            "name": x.name,
            "shape": [d.dim_value or d.dim_param for d in x.type.tensor_type.shape.dim],
        }
        for x in graph.graph.input
    ],
    "output": [
        {
            "name": x.name,
            "shape": [d.dim_value or d.dim_param for d in x.type.tensor_type.shape.dim],
        }
        for x in graph.graph.output
    ],
    "metadata": {x.key: x.value for x in graph.metadata_props},
    "names": names,
    "export_args": model.exporter.args
    if hasattr(model, "exporter")
    else "explicit in source",
}
(ROOT / "build/reports/validation/m1-yoloe/export.json").write_text(
    json.dumps(result, indent=2, default=str), encoding="utf-8"
)
print(
    json.dumps(
        {k: v for k, v in result.items() if k not in ("names", "metadata")}, indent=2
    )
)
