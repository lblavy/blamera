"""Experimental protocol rules only. No network, model loading or predictions."""

import hashlib
import numpy as np
import cv2


def validate_aliases(rows, vocabulary, target_categories=None):
    aliases = {}
    for row in rows:
        identifier = row["id"]
        if identifier in aliases or vocabulary.get(identifier) != row["name"]:
            raise ValueError("Duplicate alias or vocabulary drift")
        if row["relation"] not in {"exact", "synonym", "subtype", "coarse"}:
            raise ValueError("Unreviewed semantic relation")
        if not row["possible_categories"]:
            raise ValueError("Missing verification scope")
        policy = row.get("unmatched_policy", "verified_scope")
        if policy not in {"verified_scope", "always_unknown"}:
            raise ValueError("Unknown unmatched policy")
        if target_categories is not None:
            outside = set(row["possible_categories"]) - set(
                target_categories[row["target"]]
            )
            if outside and policy != "always_unknown":
                raise ValueError("Alias extends beyond evaluated GT scope")
        aliases[identifier] = dict(row)
    return aliases


def box_iou(a, b):
    width = max(0, min(a[2], b[2]) - max(a[0], b[0]))
    height = max(0, min(a[3], b[3]) - max(a[1], b[1]))
    intersection = width * height
    area = max(0, a[2] - a[0]) * max(0, a[3] - a[1]) + max(0, b[2] - b[0]) * max(
        0, b[3] - b[1]
    )
    return intersection / (area - intersection) if area > intersection else 0.0


def match_boxes(predictions, ground_truth, typed=True):
    used = set()
    matches = []
    order = sorted(range(len(predictions)), key=lambda i: (-predictions[i]["score"], i))
    for pi in order:
        prediction = predictions[pi]
        candidates = []
        for gi, truth in enumerate(ground_truth):
            compatible = not typed or (
                prediction["target"] == truth["target"]
                and truth["category_id"] in prediction["possible_categories"]
            )
            if gi not in used and compatible:
                candidates.append(
                    (box_iou(prediction["box"], truth["box"]), -truth["id"], gi)
                )
        if candidates:
            overlap, _, gi = max(candidates)
            if overlap >= 0.5:
                used.add(gi)
                matches.append((pi, gi))
    return matches


def unmatched_status(prediction, positive, negative, non_exhaustive):
    possible = set(prediction["possible_categories"])
    if not possible:
        return "unmapped"
    if prediction.get("unmatched_policy") == "always_unknown":
        return "outside_target_scope"
    if possible & non_exhaustive:
        return "non_exhaustive"
    if possible <= positive | negative:
        return "fp"
    return "unverified"


def evaluate_predictions(predictions, ground_truth, positive, negative, non_exhaustive):
    positive, negative, non_exhaustive = (
        set(positive),
        set(negative),
        set(non_exhaustive),
    )
    if positive & negative:
        raise ValueError("Contradictory verification metadata")
    matches = match_boxes(predictions, ground_truth)
    used = {pi for pi, _ in matches}
    statuses = [
        unmatched_status(p, positive, negative, non_exhaustive)
        for pi, p in enumerate(predictions)
        if pi not in used
    ]
    return {
        "tp": len(matches),
        "fp": statuses.count("fp"),
        "fn": len(ground_truth) - len(matches),
        "ignored": [status for status in statuses if status != "fp"],
        "matches": matches,
    }


def boundary(mask):
    padded = np.pad(mask, 1, constant_values=False)
    inside = padded[1:-1, :-2] & padded[1:-1, 2:] & padded[:-2, 1:-1] & padded[2:, 1:-1]
    return mask & ~inside


def dilate(mask, radius):
    if radius < 0:
        raise ValueError("Negative dilation radius")
    kernel = np.ones((radius * 2 + 1, radius * 2 + 1), dtype=np.uint8)
    return cv2.dilate(
        mask.astype(np.uint8), kernel, borderType=cv2.BORDER_CONSTANT, borderValue=0
    ).astype(bool)


def boundary_f1(a, b, tolerance):
    a, b = boundary(a), boundary(b)
    if not a.any() or not b.any():
        return 0.0
    precision = np.count_nonzero(a & dilate(b, tolerance)) / np.count_nonzero(a)
    recall = np.count_nonzero(b & dilate(a, tolerance)) / np.count_nonzero(b)
    return (
        float(2 * precision * recall / (precision + recall))
        if precision + recall
        else 0.0
    )


def mask_quality(ground_truth, predictions, matches, tolerance=1):
    if tolerance < 0:
        raise ValueError("Negative boundary tolerance")
    per_gt = {g["id"]: {"iou": 0.0, "boundary_f1": 0.0} for g in ground_truth}
    for pi, gi in matches:
        a, b = (
            predictions[pi]["mask"].astype(bool),
            ground_truth[gi]["mask"].astype(bool),
        )
        if a.shape != b.shape or a.ndim != 2:
            raise ValueError("Incompatible masks")
        union = np.count_nonzero(a | b)
        per_gt[ground_truth[gi]["id"]] = {
            "iou": float(np.count_nonzero(a & b) / union) if union else 0.0,
            "boundary_f1": boundary_f1(a, b, tolerance),
        }
    values = list(per_gt.values())
    return {
        "all_gt_mask_iou": float(np.mean([x["iou"] for x in values]))
        if values
        else None,
        "all_gt_boundary_f1": float(np.mean([x["boundary_f1"] for x in values]))
        if values
        else None,
        "per_gt": per_gt,
    }


def rank(identifier, seed, stratum):
    return hashlib.sha256(
        f"blamera-quality-v1:{seed}:{stratum}:{identifier}".encode()
    ).hexdigest()


def coverage_needed(selected, groups, support):
    return {
        name
        for name, ids in groups.items()
        if sum(bool(row["categories"] & ids) for row in selected) < support
    }


def cover_groups(available, groups, support, seed):
    selected = []
    needed = coverage_needed(selected, groups, support)
    while needed:

        def gain(row):
            return sum(bool(row["categories"] & groups[name]) for name in needed)

        candidates = [row for row in available if gain(row) > 0]
        if not candidates:
            raise ValueError("Insufficient category support")
        best = min(
            candidates, key=lambda row: (-gain(row), rank(row["id"], seed, "targeted"))
        )
        selected.append(best)
        available.remove(best)
        needed = coverage_needed(selected, groups, support)
    return selected


def fill_stratum(selected, available, tag, minimum, seed):
    while sum(row[tag] for row in selected) < minimum:
        candidates = [row for row in available if row[tag]]
        if not candidates:
            raise ValueError("Insufficient stratum support")
        best = min(candidates, key=lambda row: rank(row["id"], seed, tag))
        selected.append(best)
        available.remove(best)


def select_metadata(
    pool, groups, seed, control_count, target_count, support, rich_min=0, small_min=0
):
    ordered = sorted(pool, key=lambda row: rank(row["id"], seed, "control"))
    control = [row["id"] for row in ordered[:control_count]]
    available = [row for row in pool if row["id"] not in set(control)]
    selected = cover_groups(available, groups, support, seed)
    for tag, minimum in [("rich", rich_min), ("small", small_min)]:
        fill_stratum(selected, available, tag, minimum, seed)
    remaining = sorted(available, key=lambda row: rank(row["id"], seed, "fill"))
    if len(selected) > target_count or len(selected) + len(remaining) < target_count:
        raise ValueError("Selection exceeds declared size")
    selected.extend(remaining[: target_count - len(selected)])
    return {"control": control, "targeted": [row["id"] for row in selected]}


def rasterize_polygons(polygons, height, width):
    if not polygons or height <= 0 or width <= 0:
        raise ValueError("Missing polygon geometry")
    from pycocotools import mask as mask_utils

    encoded = mask_utils.frPyObjects(polygons, height, width)
    return mask_utils.decode(mask_utils.merge(encoded)).astype(bool)


def localization_quality(predictions, ground_truth):
    matches = match_boxes(predictions, ground_truth, typed=False)
    return {
        "recall": len(matches) / len(ground_truth) if ground_truth else None,
        "matches": matches,
    }


def previous_ade_samples(rows):
    return [
        {"id": row["id"], "sha256": row["sha256"]}
        for row in rows
        if row["id"].lower().startswith("ade")
    ]
