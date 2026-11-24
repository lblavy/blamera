"""Aggregate compact records; all profiles and declared image axes retained."""

from collections import defaultdict
import numpy as np
from quality_metrics import aggregate_categories, aggregate_semantic, paired_mask_scores

IMAGE_AXES = ["targeted", "control", "rich_images", "images_with_small_target"]


def mean_quality(values):
    return {
        key: float(np.mean([v[key] for v in values])) if values else None
        for key in ["iou", "boundary_f1"]
    }


def summarize_masks(rows, key="masks"):
    records = [row[key] for row in rows if row.get(key) is not None]
    all_gt = [value for record in records for value in record["per_gt"].values()]
    matched = [
        record["per_gt"][str(identifier)]
        for record in records
        for identifier in record["matched_gt_ids"]
    ]
    return {
        "support": len(all_gt),
        "all_gt": mean_quality(all_gt),
        "matched_support": len(matched),
        "matched_only": mean_quality(matched),
    }


def summarize_geometry(rows):
    counts = {
        scope: {
            key: sum(row["geometry"][scope][key] for row in rows)
            for key in ["tp", "fn", "support"]
        }
        for scope in ["all", "new", "common", "all_annotated"]
    }
    for value in counts.values():
        value["recall"] = value["tp"] / value["support"] if value["support"] else None
    return counts


def in_axis(row, axis):
    return axis == "all" or axis in row.get("image_axes", [row["stratum"]])


def summarize_stratum(rows):
    return {
        **aggregate_categories(
            [category for row in rows for category in row["categories"]]
        ),
        "images": len(rows),
        "gt_instances": sum(len(row["gt_targets"]) for row in rows),
        "annotated_instances": sum(row.get("annotated_instances", 0) for row in rows),
        "masks": summarize_masks(rows),
        "geometry_masks_new": summarize_masks(rows, "geometry_masks_new"),
        "localization": summarize_geometry(rows),
        "unmapped_count": sum(row["unmapped_count"] for row in rows),
        "empty_masks": sum(row["empty_masks"] or 0 for row in rows)
        if any(row["masks"] is not None for row in rows)
        else None,
    }


def collect_pairs(records):
    paired = []
    lookup = {(row["key"], row["profile"]): row for row in records}
    for row in records:
        if row["dataset"] != "lvis" or not row["profile"].startswith(
            "baseline-aware-c0.35-"
        ):
            continue
        cap = row["profile"].split("-")[-1]
        for mode in ["agnostic", "aware"]:
            other = lookup.get((row["key"], f"yoloe-{mode}-c0.35-{cap}"))
            if other is None:
                continue
            for matching, key in [
                ("typed", "masks"),
                ("new_class_agnostic", "geometry_masks_new"),
            ]:
                if row.get(key) is not None and other.get(key) is not None:
                    paired.append(
                        {
                            "key": row["key"],
                            "stratum": row["stratum"],
                            "image_axes": row.get("image_axes", [row["stratum"]]),
                            "matching": matching,
                            "cap": cap,
                            "pf_mode": mode,
                            **paired_mask_scores(row[key], other[key]),
                        }
                    )
    return paired


def summarize_pairs(pairs):
    groups = defaultdict(list)
    for row in pairs:
        groups[f"{row['matching']}-{row['pf_mode']}-{row['cap']}"].append(row)
    results = {}
    for key, rows in groups.items():
        support = sum(row["count"] for row in rows)
        results[key] = {
            "images": len(rows),
            "matched_images": sum(row["count"] > 0 for row in rows),
            "matched_gt": support,
            **{
                model: {
                    metric: sum(
                        row[model][metric] * row["count"]
                        for row in rows
                        if row["count"]
                    )
                    / support
                    if support
                    else None
                    for metric in ["iou", "boundary_f1"]
                }
                for model in ["first", "second"]
            },
        }
    return results


def aggregate_records(records, mapping):
    profiles = defaultdict(list)
    for row in records:
        profiles[row["profile"]].append(row)
    result = {}
    for profile, rows in profiles.items():
        lvis = [row for row in rows if row["dataset"] == "lvis"]
        categories = [category for row in lvis for category in row["categories"]]
        mask_categories = {}
        for group in mapping["groups"]:
            values = [
                value
                for row in lvis
                if row["masks"]
                for identifier, value in row["masks"]["per_gt"].items()
                if row["gt_targets"][identifier] == group["key"]
            ]
            mask_categories[group["key"]] = {
                "support": len(values),
                **mean_quality(values),
            }
        ade = [row for row in rows if row["dataset"] == "ade" and row.get("semantic")]
        result[profile] = {
            "images": len(rows),
            "lvis": aggregate_categories(categories),
            "strata": {
                axis: summarize_stratum([row for row in lvis if in_axis(row, axis)])
                for axis in IMAGE_AXES
            },
            "scopes": {
                scope: aggregate_categories(
                    [row for row in categories if row["scope"] == scope]
                )
                for scope in ["common", "new"]
            },
            "localization": summarize_geometry(lvis),
            "masks": {**summarize_masks(lvis), "categories": mask_categories},
            "geometry_masks_new": summarize_masks(lvis, "geometry_masks_new"),
            "semantic": {
                key: aggregate_semantic([row["semantic"][key] for row in ade])
                for key in mapping["ade_category_groups"]
            }
            if ade
            else None,
            "empty_masks": sum(row["empty_masks"] or 0 for row in rows)
            if any(row["masks"] is not None for row in lvis)
            else None,
            "unmapped_count": sum(row["unmapped_count"] for row in rows),
        }
    paired = collect_pairs(records)
    return {
        "profiles": result,
        "same_gt_pairs": paired,
        "paired_strata": {
            axis: summarize_pairs([row for row in paired if in_axis(row, axis)])
            for axis in ["all", *IMAGE_AXES]
        },
        "axis_scope": "Overlapping image strata. Small-target images contain >=1 evaluated GT area<32^2, but metrics include ALL evaluated GT in those images; no object-only small/occlusion metric.",
        "scope": "Restricted preregistered diagnostic, not official AP or population prevalence",
    }
