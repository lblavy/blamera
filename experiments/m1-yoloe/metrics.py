"""Small experimental matching metrics; no AP claim."""

import numpy as np


def mask_iou(a, b, valid=None):
    if valid is None:
        valid = np.ones_like(a, dtype=bool)
    union = np.count_nonzero((a | b) & valid)
    return float(np.count_nonzero((a & b) & valid) / union) if union else None


def box_iou(a, b):
    ix = max(0, min(a[2], b[2]) - max(a[0], b[0]))
    iy = max(0, min(a[3], b[3]) - max(a[1], b[1]))
    inter = ix * iy
    union = (
        max(0, a[2] - a[0]) * max(0, a[3] - a[1])
        + max(0, b[2] - b[0]) * max(0, b[3] - b[1])
        - inter
    )
    return inter / union if union else 0


def match_instances(pred, gt):
    used = set()
    matches = []
    ious = []
    for pi in sorted(range(len(pred)), key=lambda i: -pred[i]["score"]):
        candidates = [
            (box_iou(pred[pi]["box"], g["box"]), gi)
            for gi, g in enumerate(gt)
            if gi not in used and g["class"] == pred[pi]["class"]
        ]
        if not candidates:
            continue
        overlap, gi = max(candidates)
        if overlap < 0.5:
            continue
        used.add(gi)
        matches.append([pi, gi])
        ious.append(mask_iou(pred[pi]["mask"], gt[gi]["mask"]))
    return {
        "tp": len(matches),
        "fp": len(pred) - len(matches),
        "fn": len(gt) - len(matches),
        "matched_mask_iou": ious,
        "matches": matches,
    }
