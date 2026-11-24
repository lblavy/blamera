"""Small-only adaptation; frozen quality engine stays read-only."""

from pathlib import Path
import sys
import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "m1-yoloe-quality"))
from study_postprocess import build_profiles
from quality_metrics import paired_mask_scores
from study_summary import IMAGE_AXES, in_axis, summarize_pairs


def small_profiles(protocol):
    return [
        {**row, "key": row["key"].replace("yoloe-", "small-", 1)}
        for row in build_profiles(protocol)
        if row["model"] == "yoloe"
    ]


def vocabulary_translation(reference, candidate):
    expected = set(range(len(reference)))
    if set(reference) != expected or set(candidate) != expected:
        raise ValueError("Vocabulary index/count drift")
    if len(set(reference.values())) != len(reference) or len(
        set(candidate.values())
    ) != len(candidate):
        raise ValueError("Duplicate vocabulary names")
    reverse = {name: index for index, name in candidate.items()}
    if set(reference.values()) != set(reverse):
        raise ValueError("Vocabulary semantics differ; no new synonym mapping allowed")
    return [reverse[reference[index]] for index in range(len(reference))]


def canonical_raw(raw, translation):
    if translation == list(range(len(translation))):
        return raw
    if raw.ndim != 3 or raw.shape[1] != 4 + len(translation) + 32:
        raise ValueError("Invalid segmentation channel shape")
    result = raw.copy()
    result[:, 4 : 4 + len(translation)] = raw[:, 4 + np.asarray(translation), :]
    return result


def restrict_mask(record, targets, scope, mapping):
    groups = {row["key"] for row in mapping["groups"] if row["scope"] == scope}
    allowed = {key for key, value in targets.items() if value in groups}
    return {
        **record,
        "matched_gt_ids": [
            identifier
            for identifier in record["matched_gt_ids"]
            if str(identifier) in allowed
        ],
        "per_gt": {
            key: value for key, value in record["per_gt"].items() if key in allowed
        },
    }


def compare_references(small_records, reference_records, mapping):
    lookup = {(row["key"], row["profile"]): row for row in reference_records}
    pairs = []
    for row in small_records:
        if row["dataset"] != "lvis" or "-c0.35-" not in row["profile"]:
            continue
        _, mode, _, cap = row["profile"].split("-")
        for model, profile in [
            ("baseline", f"baseline-aware-c0.35-{cap}"),
            ("nano", f"yoloe-{mode}-c0.35-{cap}"),
        ]:
            reference = lookup.get((row["key"], profile))
            if reference is None:
                raise ValueError("Missing immutable reference record")
            for matching, key, scope in [
                ("typed", "masks", "common"),
                ("typed", "masks", "new"),
                ("new_class_agnostic", "geometry_masks_new", "new"),
            ]:
                first = restrict_mask(
                    reference[key], reference["gt_targets"], scope, mapping
                )
                second = restrict_mask(row[key], row["gt_targets"], scope, mapping)
                pairs.append(
                    {
                        "key": row["key"],
                        "stratum": row["stratum"],
                        "image_axes": row.get("image_axes", [row["stratum"]]),
                        "reference_model": model,
                        "matching": matching,
                        "scope": scope,
                        "cap": cap,
                        "pf_mode": mode,
                        **paired_mask_scores(first, second),
                    }
                )
    return pairs


def aggregate_comparisons(pairs):
    return {
        model: {
            scope: {
                axis: summarize_pairs(
                    [
                        row
                        for row in pairs
                        if row["reference_model"] == model
                        and row["scope"] == scope
                        and in_axis(row, axis)
                    ]
                )
                for axis in ["all", *IMAGE_AXES]
            }
            for scope in ["common", "new"]
        }
        for model in ["baseline", "nano"]
    }


def native_mapping(mapping, translation):
    return {
        **mapping,
        **{
            key: [{**row, "id": translation[row["id"]]} for row in mapping[key]]
            for key in ["pf_aliases", "ade_pf_aliases"]
        },
    }
