"""Frozen study scoring, independent of model loading and file acquisition."""

from collections import Counter
import numpy as np
from protocol_core import evaluate_predictions, mask_quality, match_boxes
from quality_metrics import rates, semantic_counts


def map_predictions(candidates, mapping, model, semantic=False):
    key = ("ade_" if semantic else "") + (
        "baseline_aliases" if model == "baseline" else "pf_aliases"
    )
    aliases = {row["id"]: row for row in mapping[key]}
    return [
        {
            **candidate,
            **aliases.get(
                candidate["class"], {"target": None, "possible_categories": []}
            ),
        }
        for candidate in candidates
    ]


def mask_record(truth, predictions, matches, cache, tolerance):
    # Box matches are fixed first; an empty mask is still a box TP but contributes zero quality.
    masks = [{**p, "mask": cache.get(p)} for p in predictions]
    result = mask_quality(truth, masks, matches, tolerance)
    values = [result["per_gt"][truth[gi]["id"]] for _, gi in matches]
    result["per_gt"] = {str(key): value for key, value in result["per_gt"].items()}
    return {
        **result,
        "matched_gt_ids": [truth[gi]["id"] for _, gi in matches],
        "matched_only_iou": float(np.mean([v["iou"] for v in values]))
        if values
        else None,
        "matched_only_boundary_f1": float(np.mean([v["boundary_f1"] for v in values]))
        if values
        else None,
    }


def score_lvis(candidates, truth, metadata, mapping, model, cache, tolerance):
    predictions = map_predictions(candidates, mapping, model)
    matches = match_boxes(predictions, truth)
    categories = []
    for group in mapping["groups"]:
        p = [p for p in predictions if p["target"] == group["key"]]
        g = [g for g in truth if g["target"] == group["key"]]
        result = evaluate_predictions(
            p,
            g,
            metadata["positive_category_ids"],
            metadata["neg_category_ids"],
            metadata["not_exhaustive_category_ids"],
        )
        categories.append(
            rates(
                {
                    "category": group["key"],
                    "scope": group["scope"],
                    "tp": result["tp"],
                    "fp": result["fp"],
                    "fn": result["fn"],
                    "ignored": dict(Counter(result["ignored"])),
                }
            )
        )
    geometry = {}
    geometry_masks_new = None
    for scope in ["all", "new", "common"]:
        groups = {
            g["key"] for g in mapping["groups"] if scope == "all" or g["scope"] == scope
        }
        g = [g for g in truth if g["target"] in groups]
        found = match_boxes(predictions, g, typed=False)
        if scope == "new" and cache is not None:
            geometry_masks_new = mask_record(g, predictions, found, cache, tolerance)
        geometry[scope] = {
            "tp": len(found),
            "fn": len(g) - len(found),
            "support": len(g),
        }
    if "all_ground_truth" in metadata:
        all_gt = metadata["all_ground_truth"]
        count = len(match_boxes(predictions, all_gt, typed=False))
        geometry["all_annotated"] = {
            "tp": count,
            "fn": len(all_gt) - count,
            "support": len(all_gt),
        }
    return {
        "categories": categories,
        "geometry": geometry,
        "geometry_masks_new": geometry_masks_new,
        "predictions": predictions,
        "unmapped_count": sum(p["target"] is None for p in predictions),
        "empty_masks": sum(not cache.get(p).any() for p in predictions)
        if cache
        else None,
        "masks": mask_record(truth, predictions, matches, cache, tolerance)
        if cache
        else None,
    }


def score_ade(candidates, truth, mapping, model, cache, tolerance):
    predictions = map_predictions(candidates, mapping, model, semantic=True)
    rows = {}
    for target, codes in mapping["ade_category_groups"].items():
        predicted = np.zeros(truth.shape, bool)
        for prediction in predictions:
            if prediction["target"] == target:
                predicted |= cache.get(prediction)
        rows[target] = semantic_counts(
            predicted, np.isin(truth, codes), truth != 0, tolerance
        )
    return {
        "semantic": rows,
        "predictions": predictions,
        "unmapped_count": sum(p["target"] is None for p in predictions),
        "empty_masks": sum(not cache.get(p).any() for p in predictions),
    }
