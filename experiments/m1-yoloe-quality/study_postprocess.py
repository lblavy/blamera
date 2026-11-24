"""Bounded raw-output decoding for the preregistered profiles, no inference."""

import numpy as np
from legacy_geometry import source_box, source_mask, contours
from protocol_core import box_iou


def validate_raw(raw, proto, class_count):
    if raw.shape != (1, 4 + class_count + 32, 8400) or proto.shape != (1, 32, 160, 160):
        raise ValueError("Unexpected raw segmentation shapes")
    if (
        raw.dtype != np.float32
        or proto.dtype != np.float32
        or not np.isfinite(raw).all()
        or not np.isfinite(proto).all()
    ):
        raise ValueError("Nonfinite or non-FP32 outputs")


def candidate_pool(raw, projection, class_count, confidence):
    values = raw[0]
    scores = values[4 : 4 + class_count]
    classes = scores.argmax(axis=0)
    best = scores[classes, np.arange(8400)]
    if np.any(best < 0) or np.any(best > 1):
        raise ValueError("Invalid class scores")
    indices = sorted(
        np.where(best >= confidence)[0], key=lambda i: (-float(best[i]), int(i))
    )
    candidates = []
    total = 0
    for index in indices:
        cx, cy, width, height = values[:4, index]
        box = np.array(
            [cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2],
            np.float32,
        )
        source = source_box(box, projection)
        if source[2] <= source[0] or source[3] <= source[1]:
            continue
        total += 1
        if len(candidates) < 300:
            candidates.append(
                {
                    "anchor": int(index),
                    "class": int(classes[index]),
                    "score": float(best[index]),
                    "box": source,
                    "network_box": box,
                }
            )
    return candidates, total


class FrameDecoder:
    """Validate one forward once, cache only compact <=300 candidate lists."""

    def __init__(self, raw, proto, projection, class_count):
        validate_raw(raw, proto, class_count)
        self.raw = raw
        self.projection = projection
        self.class_count = class_count
        self.pools = {}
        self.selected = {}

    def select(self, confidence=0.35, agnostic=False, cap=20):
        if cap not in {20, 100} or confidence not in {0.2, 0.35, 0.5}:
            raise ValueError("Undeclared profile")
        if confidence not in self.pools:
            self.pools[confidence] = candidate_pool(
                self.raw, self.projection, self.class_count, confidence
            )
        candidates, total = self.pools[confidence]
        key = (confidence, agnostic)
        if key not in self.selected:
            selected = []
            for candidate in candidates:
                if all(
                    (not agnostic and old["class"] != candidate["class"])
                    or box_iou(old["network_box"], candidate["network_box"]) <= 0.5
                    for old in selected
                ):
                    selected.append(candidate)
                if len(selected) == 100:
                    break
            self.selected[key] = selected
        selected = self.selected[key][:cap]
        return selected, {
            "candidates_before_cap": total,
            "candidate_count": len(candidates),
            "selected_boxes": len(selected),
        }


def select_candidates(
    raw, proto, projection, class_count, confidence=0.35, agnostic=False, cap=20
):
    return FrameDecoder(raw, proto, projection, class_count).select(
        confidence, agnostic, cap
    )


def build_profiles(config):
    profiles = []
    for base in config["profiles"]:
        for confidence in config["confidence_sensitivity"]:
            for cap in config["max_detections"]:
                mode = "agnostic" if base["agnostic_nms"] else "aware"
                profiles.append(
                    {
                        **base,
                        "confidence": confidence,
                        "cap": cap,
                        "mask_metrics": confidence == 0.35,
                        "key": f"{base['model']}-{mode}-c{confidence:.2f}-k{cap}",
                    }
                )
    return profiles


class FrameMasks:
    def __init__(self, raw, proto, projection, class_count, limit=200):
        self.raw = raw
        self.proto = proto
        self.projection = projection
        self.class_count = class_count
        self.limit = limit
        self.cache = {}

    def get(self, candidate):
        anchor = candidate["anchor"]
        if anchor not in self.cache:
            if len(self.cache) >= self.limit:
                raise ValueError("Mask cache bound exceeded")
            logits = (
                self.raw[0, 4 + self.class_count :, anchor]
                @ self.proto[0].reshape(32, -1)
            ).reshape(160, 160)
            box = candidate["network_box"]
            p = self.projection
            points = np.arange(160) * 4
            active = (
                (logits > 0)
                & (points >= max(box[0], p["left"]))[None, :]
                & (points < min(box[2], p["left"] + p["sw"]))[None, :]
                & (points >= max(box[1], p["top"]))[:, None]
                & (points < min(box[3], p["top"] + p["sh"]))[:, None]
            )
            mask = (
                source_mask(logits, box, p)
                if active.any()
                else np.zeros((p["h"], p["w"]), dtype=bool)
            )
            self.cache[anchor] = {"mask": mask, "logits": logits}
        return self.cache[anchor]["mask"]

    def segments(self, candidate):
        mask = self.get(candidate)
        if not mask.any():
            return np.empty((0, 2, 2), dtype=np.float32)
        return contours(
            self.cache[candidate["anchor"]]["logits"],
            candidate["network_box"],
            self.projection,
        )
