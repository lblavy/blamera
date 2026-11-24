"""Local dataset and ORT boundaries. ORT is imported only inside explicit execution."""

from contextlib import contextmanager
import ast, json, gc, socket
import cv2
import numpy as np
from protocol_core import rasterize_polygons, validate_aliases


class GroundTruth:
    def __init__(self, root, selection, mapping):
        self.root = root
        self.mapping = mapping
        self.metadata = {row["id"]: row for row in selection["lvis"]}
        self.ade = {row["id"]: row for row in selection["ade"]}
        self.target = {
            identifier: group["key"]
            for group in mapping["groups"]
            for identifier in group["lvis_ids"]
        }
        path = (
            root / "build/reports/validation/m1-yoloe-quality/source/lvis_v1_val.json"
        )
        source = json.loads(path.read_text(encoding="utf-8"))
        self.annotations = {key: [] for key in self.metadata}
        for row in source["annotations"]:
            if row["image_id"] in self.annotations:
                self.annotations[row["image_id"]].append(row)

    def instances(self, record):
        truth = []
        for row in self.annotations[record["id"]]:
            if row["category_id"] not in self.target:
                continue
            x, y, w, h = row["bbox"]
            truth.append(
                {
                    "id": row["id"],
                    "category_id": row["category_id"],
                    "target": self.target[row["category_id"]],
                    "box": [x, y, x + w, y + h],
                    "mask": rasterize_polygons(
                        row["segmentation"], record["height"], record["width"]
                    ),
                }
            )
        return truth

    def all_boxes(self, record):
        return [
            {
                "id": row["id"],
                "box": [
                    row["bbox"][0],
                    row["bbox"][1],
                    row["bbox"][0] + row["bbox"][2],
                    row["bbox"][1] + row["bbox"][3],
                ],
            }
            for row in self.annotations[record["id"]]
        ]

    def semantic(self, record):
        path = self.root / self.ade[record["id"]]["mask_file"]
        mask = cv2.imread(str(path), cv2.IMREAD_UNCHANGED)
        if (
            mask is None
            or mask.ndim != 2
            or mask.shape != (record["height"], record["width"])
        ):
            raise ValueError("Invalid ADE ground truth")
        return mask


def load_image(root, record):
    image = cv2.imread(str(root / record["path"]), cv2.IMREAD_COLOR)
    if image is None or image.shape[:2] != (record["height"], record["width"]):
        raise ValueError("Invalid frozen JPEG")
    return image


@contextmanager
def blocked_network():
    original = socket.socket

    def blocked(*args, **kwargs):
        raise RuntimeError("Network forbidden during study execution")

    socket.socket = blocked
    try:
        yield
    finally:
        socket.socket = original


@contextmanager
def ort_session(root, model, config, mapping):
    import onnxruntime as ort

    ort.disable_telemetry_events()
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    options.inter_op_num_threads = 1
    options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
    options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    session = ort.InferenceSession(
        str(root / config["models"][model]["path"]),
        options,
        providers=["CPUExecutionProvider"],
    )
    owned = None
    try:
        names = ast.literal_eval(session.get_modelmeta().custom_metadata_map["names"])
        expected = 80 if model == "baseline" else 4585
        if len(names) != expected or set(names) != set(range(expected)):
            raise ValueError("Model vocabulary drift")
        prefix = "baseline" if model == "baseline" else "pf"
        targets = {group["key"]: group["lvis_ids"] for group in mapping["groups"]}
        validate_aliases(mapping[prefix + "_aliases"], names, targets)
        for alias in mapping["ade_" + prefix + "_aliases"]:
            if names.get(alias["id"]) != alias["name"]:
                raise ValueError("ADE vocabulary drift")

        class OwnedSession:
            def __init__(self, native):
                self.native = native
                self.names = names

            def run(self, tensor):
                return self.native.run(None, {self.native.get_inputs()[0].name: tensor})

        owned = OwnedSession(session)
        yield owned
    finally:
        if owned is not None:
            owned.native = None
        del session, options
        gc.collect()  # Release native session before opening the other model, not a memory measurement.
