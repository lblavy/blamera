"""Bounded exploratory port of application preprocessing, NMS and logit isocontours."""

import time
import numpy as np
from metrics import box_iou


def prepare(image):
    h, w = image.shape[:2]
    scale = np.float32(640 / max(w, h))
    sw, sh = int(np.rint(np.float32(w) * scale)), int(np.rint(np.float32(h) * scale))
    left, top = (640 - sw) // 2, (640 - sh) // 2
    xs = np.maximum(0, (np.arange(sw, dtype=np.float32) + 0.5) * w / sw - 0.5)
    ys = np.maximum(0, (np.arange(sh, dtype=np.float32) + 0.5) * h / sh - 0.5)
    x0 = np.minimum(xs.astype(int), w - 1)
    x1 = np.minimum(x0 + 1, w - 1)
    dx = xs - x0
    y0 = np.minimum(ys.astype(int), h - 1)
    y1 = np.minimum(y0 + 1, h - 1)
    dy = ys - y0
    rgb = image[:, :, ::-1].astype(np.float32)
    a = (
        rgb[y0[:, None], x0[None, :]] * (1 - dx)[None, :, None]
        + rgb[y0[:, None], x1[None, :]] * dx[None, :, None]
    )
    b = (
        rgb[y1[:, None], x0[None, :]] * (1 - dx)[None, :, None]
        + rgb[y1[:, None], x1[None, :]] * dx[None, :, None]
    )
    canvas = np.full((640, 640, 3), 114, dtype=np.float32)
    canvas[top : top + sh, left : left + sw] = np.rint(
        a * (1 - dy)[:, None, None] + b * dy[:, None, None]
    )
    return np.ascontiguousarray(
        canvas.transpose(2, 0, 1)[None] / 255, dtype=np.float32
    ), {
        "w": w,
        "h": h,
        "scale": float(scale),
        "left": left,
        "top": top,
        "sw": sw,
        "sh": sh,
    }


def source_box(box, p):
    return [
        float(np.clip((box[0] - p["left"]) / p["scale"], 0, p["w"])),
        float(np.clip((box[1] - p["top"]) / p["scale"], 0, p["h"])),
        float(np.clip((box[2] - p["left"]) / p["scale"], 0, p["w"])),
        float(np.clip((box[3] - p["top"]) / p["scale"], 0, p["h"])),
    ]


def interpolate(logits, xs, ys):
    x = np.clip(np.asarray(xs, dtype=np.float32) / 4 - 0.5, 0, 159)
    y = np.clip(np.asarray(ys, dtype=np.float32) / 4 - 0.5, 0, 159)
    x0 = x.astype(int)
    x1 = np.minimum(x0 + 1, 159)
    dx = x - x0
    y0 = y.astype(int)
    y1 = np.minimum(y0 + 1, 159)
    dy = y - y0
    a = (
        logits[y0[:, None], x0[None, :]] * (1 - dx)[None, :]
        + logits[y0[:, None], x1[None, :]] * dx[None, :]
    )
    b = (
        logits[y1[:, None], x0[None, :]] * (1 - dx)[None, :]
        + logits[y1[:, None], x1[None, :]] * dx[None, :]
    )
    return a * (1 - dy)[:, None] + b * dy[:, None]


def source_mask(logits, box, p):
    xs = (np.arange(p["w"], dtype=np.float32) + 0.5) * p["scale"] + p["left"]
    ys = (np.arange(p["h"], dtype=np.float32) + 0.5) * p["scale"] + p["top"]
    mask = interpolate(logits, xs, ys) > 0
    mask &= (
        (xs >= box[0])[None, :]
        & (xs < box[2])[None, :]
        & (ys >= box[1])[:, None]
        & (ys < box[3])[:, None]
    )
    return mask


CASES = [
    [],
    [3, 0],
    [0, 1],
    [3, 1],
    [1, 2],
    [3, 0, 1, 2],
    [0, 2],
    [3, 2],
    [3, 2],
    [0, 2],
    [0, 1, 2, 3],
    [1, 2],
    [3, 1],
    [0, 1],
    [3, 0],
    [],
]
CORNERS = [(0, 0), (1, 0), (1, 1), (0, 1)]
EDGES = [(0, 1), (1, 2), (3, 2), (0, 3)]


def contours(logits, box, p):
    l, t, r, b = (
        max(box[0], p["left"]),
        max(box[1], p["top"]),
        min(box[2], p["left"] + p["sw"]),
        min(box[3], p["top"] + p["sh"]),
    )
    if r <= l or b <= t:
        return np.zeros((0, 2, 2), np.float32)

    def axis(a, b):
        return np.r_[
            a,
            a,
            np.arange(2, 640, 4)[
                (np.arange(2, 640, 4) > a) & (np.arange(2, 640, 4) < b)
            ],
            b,
            b,
        ]

    xs, ys = axis(l, r), axis(t, b)
    grid = np.full((len(ys), len(xs)), -1, dtype=np.float32)
    grid[1:-1, 1:-1] = interpolate(logits, xs[1:-1], ys[1:-1])
    code = (
        (grid[:-1, :-1] > 0).astype(int)
        + (grid[:-1, 1:] > 0) * 2
        + (grid[1:, 1:] > 0) * 4
        + (grid[1:, :-1] > 0) * 8
    )
    segments = []
    for case in range(1, 15):
        yy, xx = np.where(code == case)
        for i in range(0, len(CASES[case]), 2):
            ends = []
            for edge in CASES[case][i : i + 2]:
                a, b = EDGES[edge]
                ax, ay = CORNERS[a]
                bx, by = CORNERS[b]
                value = grid[yy + ay, xx + ax]
                other = grid[yy + by, xx + bx]
                weight = value / (value - other)
                x = xs[xx + ax] + (xs[xx + bx] - xs[xx + ax]) * weight
                y = ys[yy + ay] + (ys[yy + by] - ys[yy + ay]) * weight
                ends.append(
                    np.stack(
                        [
                            np.clip((x - p["left"]) / p["scale"], 0, p["w"]),
                            np.clip((y - p["top"]) / p["scale"], 0, p["h"]),
                        ],
                        axis=-1,
                    )
                )
            segments.append(np.stack(ends, axis=1))
    result = np.concatenate(segments, axis=0) if segments else np.zeros((0, 2, 2))
    result = result[np.any(result[:, 0] != result[:, 1], axis=1)]
    assert len(result) <= 8000, "contour complexity exceeds application bound"
    return result.astype(np.float32)


def decode(raw, proto, p, names, mapping=None, max_det=20, agnostic=False):
    start = time.perf_counter()
    nc = len(names)
    assert raw.shape == (1, 4 + nc + 32, 8400) and proto.shape == (1, 32, 160, 160)
    assert np.isfinite(raw).all() and np.isfinite(proto).all()
    values = raw[0]
    scores = values[4 : 4 + nc]
    if mapping is not None:
        # Max synonyms per target class BEFORE argmax/NMS/cap, while network still computed4585.
        scores = np.stack(
            [
                np.max(scores[item["pf_ids"]], axis=0)
                if item["pf_ids"]
                else np.zeros(8400)
                for item in mapping.values()
            ]
        )
    classes = scores.argmax(axis=0)
    best = scores[classes, np.arange(8400)]
    indices = np.where(best >= 0.35)[0]
    indices = sorted(indices, key=lambda i: (-float(best[i]), int(i)))
    candidates = []
    for i in indices:
        cx, cy, w, h = values[:4, i]
        box = np.array([cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2], np.float32)
        sb = source_box(box, p)
        if sb[2] > sb[0] and sb[3] > sb[1]:
            candidates.append((int(i), int(classes[i]), float(best[i]), box, sb))
        if len(candidates) == 300:
            break
    selected = []
    for candidate in candidates:
        if all(
            (not agnostic and old[1] != candidate[1])
            or box_iou(old[3], candidate[3]) <= 0.5
            for old in selected
        ):
            selected.append(candidate)
        if len(selected) == max_det:
            break
    detection_seconds = time.perf_counter() - start
    mask_seconds = 0.0
    contour_seconds = 0.0
    results = []
    for anchor, cls, score, box, sb in selected:
        tick = time.perf_counter()
        logits = (values[4 + nc :, anchor] @ proto[0].reshape(32, -1)).reshape(160, 160)
        # Application discards empty coarse masks before drawing interpolated contours.
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
        mask = source_mask(logits, box, p)
        mask_seconds += time.perf_counter() - tick
        tick = time.perf_counter()
        segments = contours(logits, box, p)
        contour_seconds += time.perf_counter() - tick
        results.append(
            {
                "class": cls,
                "score": score,
                "box": sb,
                "mask": mask,
                "segments": segments,
            }
        )
    return results, {
        "candidate_nms_ms": detection_seconds * 1000,
        "mask_ms": mask_seconds * 1000,
        "contour_ms": contour_seconds * 1000,
        "candidate_count": len(candidates),
        "selected_before_empty_mask": len(selected),
    }
