"""Auditable aggregation and void-aware semantic metrics, not model execution."""

from collections import Counter, defaultdict
import numpy as np
import cv2
from protocol_core import boundary, dilate


def semantic_counts(prediction, truth, valid, tolerance=1):
    prediction, truth, valid = (
        prediction.astype(bool),
        truth.astype(bool),
        valid.astype(bool),
    )
    if prediction.shape != truth.shape or truth.shape != valid.shape or truth.ndim != 2:
        raise ValueError("Incompatible semantic masks")
    if tolerance < 0:
        raise ValueError("Negative boundary tolerance")
    # A boundary requires its four neighbours to be verified too. Void and image edges cannot fabricate contours.
    cross = np.array([[0, 1, 0], [1, 1, 1], [0, 1, 0]], np.uint8)
    known = cv2.erode(
        valid.astype(np.uint8), cross, borderType=cv2.BORDER_CONSTANT, borderValue=0
    ).astype(bool)
    p = boundary(prediction) & known
    g = boundary(truth) & known
    return {
        "intersection": int(np.count_nonzero(prediction & truth & valid)),
        "union": int(np.count_nonzero((prediction | truth) & valid)),
        "pred_pixels": int(np.count_nonzero(prediction & valid)),
        "gt_pixels": int(np.count_nonzero(truth & valid)),
        "valid_pixels": int(np.count_nonzero(valid)),
        "pred_boundary": int(np.count_nonzero(p)),
        "gt_boundary": int(np.count_nonzero(g)),
        "pred_boundary_hits": int(np.count_nonzero(p & dilate(g, tolerance))),
        "gt_boundary_hits": int(np.count_nonzero(g & dilate(p, tolerance))),
    }


def semantic_boundary_f1(counts):
    p, g = counts["pred_boundary"], counts["gt_boundary"]
    if p == 0 and g == 0:
        return None
    precision = counts["pred_boundary_hits"] / p if p else 0.0
    recall = counts["gt_boundary_hits"] / g if g else 0.0
    return 2 * precision * recall / (precision + recall) if precision + recall else 0.0


def aggregate_semantic(rows):
    keys = [
        "intersection",
        "union",
        "pred_pixels",
        "gt_pixels",
        "valid_pixels",
        "pred_boundary",
        "gt_boundary",
        "pred_boundary_hits",
        "gt_boundary_hits",
    ]
    counts = {key: sum(row[key] for row in rows) for key in keys}
    return {
        **counts,
        "iou": counts["intersection"] / counts["union"] if counts["union"] else None,
        "boundary_f1": semantic_boundary_f1(counts),
        "images": len(rows),
    }


def rates(row):
    tp, fp, fn = row["tp"], row["fp"], row["fn"]
    if min(tp, fp, fn) < 0:
        raise ValueError("Negative detection counts")
    return {
        **row,
        "precision": tp / (tp + fp) if tp + fp else None,
        "recall": tp / (tp + fn) if tp + fn else None,
    }


def sum_detection_rows(rows):
    ignored = Counter()
    for row in rows:
        ignored.update(row.get("ignored", {}))
    return rates(
        {
            **{key: sum(row[key] for row in rows) for key in ["tp", "fp", "fn"]},
            "ignored": dict(ignored),
        }
    )


def aggregate_categories(rows):
    groups = defaultdict(list)
    for row in rows:
        groups[row["category"]].append(row)
    categories = {
        key: sum_detection_rows(items) for key, items in sorted(groups.items())
    }
    macro = {}
    for key in ["precision", "recall"]:
        values = [row[key] for row in categories.values() if row[key] is not None]
        macro[key] = float(np.mean(values)) if values else None
    return {"micro": sum_detection_rows(rows), "macro": macro, "categories": categories}


def paired_mask_scores(first, second):
    ids = sorted(set(first["matched_gt_ids"]) & set(second["matched_gt_ids"]))

    def mean(record):
        values = [record["per_gt"][str(identifier)] for identifier in ids]
        return {
            key: float(np.mean([row[key] for row in values])) if values else None
            for key in ["iou", "boundary_f1"]
        }

    return {
        "gt_ids": ids,
        "count": len(ids),
        "first": mean(first),
        "second": mean(second),
    }
