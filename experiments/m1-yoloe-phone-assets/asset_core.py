"""Pure contracts for the bounded personal Android benchmark bundle."""

import numpy as np


def raw_rows(raw, classes, agnostic):
    if raw.shape != (1, 4 + classes + 32, 8400) or not np.isfinite(raw).all():
        raise ValueError("Unexpected raw parent output")
    values = raw[0]
    categories = values[4 : 4 + classes].argmax(axis=0)
    scores = values[4 + categories, np.arange(8400)]
    candidates = np.flatnonzero(scores > 0.35)
    candidates = sorted(candidates, key=lambda i: (-float(scores[i]), int(i)))[:300]
    selected = []
    for anchor in candidates:
        center, size = values[:2, anchor], values[2:4, anchor]
        box = np.concatenate((center - size / 2, center + size / 2))
        if any(
            (agnostic or int(categories[anchor]) == int(old[5]))
            and box_iou(box, old[:4]) > 0.5
            for old in selected
        ):
            continue
        selected.append(
            np.concatenate(
                (
                    box,
                    [scores[anchor], categories[anchor]],
                    values[4 + classes :, anchor],
                )
            )
        )
        if len(selected) == 20:
            break
    result = np.zeros((1, 20, 38), np.float32)
    if selected:
        result[0, : len(selected)] = selected
    return result


def box_iou(a, b):
    intersection = np.maximum(
        0, np.minimum(a[2:], b[2:]) - np.maximum(a[:2], b[:2])
    ).prod()
    union = (
        np.maximum(0, a[2:] - a[:2]).prod()
        + np.maximum(0, b[2:] - b[:2]).prod()
        - intersection
    )
    return float(intersection / union) if union > 0 else 0.0


def validate_compact(rows, prototypes, classes):
    if rows.shape != (1, 20, 38) or prototypes.shape != (1, 32, 160, 160):
        raise ValueError("Unexpected compact segmentation shape")
    if not np.isfinite(rows).all() or not np.isfinite(prototypes).all():
        raise ValueError("Nonfinite compact segmentation output")
    active = rows[0, rows[0, :, 4] >= 0.35]
    if np.any(active[:, 5] < 0) or np.any(active[:, 5] >= classes):
        raise ValueError("Class outside pinned vocabulary")
    if np.any(active[:, 5] != np.floor(active[:, 5])):
        raise ValueError("Fractional class identifier")


def packed_mask(row, prototypes, projection):
    logits = (row[6:] @ prototypes[0].reshape(32, -1)).reshape(160, 160)
    axis = np.arange(160) * 4
    left = max(row[0], projection["left"])
    top = max(row[1], projection["top"])
    right = min(row[2], projection["left"] + projection["sw"])
    bottom = min(row[3], projection["top"] + projection["sh"])
    mask = (logits > 0) & ((axis >= left) & (axis < right))[None, :]
    mask &= ((axis >= top) & (axis < bottom))[:, None]
    return np.packbits(mask.reshape(-1), bitorder="big").tobytes()


def compare_detections(actual, expected):
    if len(actual) != len(expected):
        raise ValueError(f"Detection count drift: {len(actual)} != {len(expected)}")
    unmatched = list(expected)
    bounds, scores, ious = [], [], []
    for row in actual:
        candidates = [r for r in unmatched if r["classId"] == row["classId"]]
        if not candidates:
            raise ValueError("Detection class drift")
        match = min(
            candidates, key=lambda r: np.max(np.abs(np.array(r["box"]) - row["box"]))
        )
        unmatched.remove(match)
        bounds.append(float(np.max(np.abs(np.array(match["box"]) - row["box"]))))
        scores.append(abs(match["score"] - row["score"]))
        a, b = [
            np.unpackbits(np.frombuffer(r["mask"], np.uint8)) > 0 for r in (row, match)
        ]
        union = np.count_nonzero(a | b)
        ious.append(float(np.count_nonzero(a & b) / union) if union else 1.0)
    if (
        max(bounds, default=0) > 0.05
        or max(scores, default=0) > 0.0001
        or min(ious, default=1) < 0.99
    ):
        raise ValueError(
            f"Compact/raw parity exceeded bounds: boxes={bounds}, scores={scores}, masks={ious}"
        )
    return {
        "matched": len(actual),
        "boxMaxAbs": max(bounds, default=0),
        "scoreMaxAbs": max(scores, default=0),
        "maskIoUMin": min(ious, default=1),
    }
