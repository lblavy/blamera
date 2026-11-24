"""Size/path/reference adaptation only; inherited quality engines stay immutable."""

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "m1-yoloe-small"))
import small_adapter
from quality_metrics import paired_mask_scores
from study_summary import IMAGE_AXES, in_axis, summarize_pairs

ORDER = ("x", "m", "l")


def require_size(size):
    if size not in ORDER:
        raise ValueError("Only reviewed X, M and L sizes are allowed")
    return size


def profiles(protocol, size):
    require_size(size)
    return [
        {**row, "key": row["key"].replace("small-", size + "-", 1)}
        for row in small_adapter.small_profiles(protocol)
    ]


def destination(root, size):
    require_size(size)
    base = (root / "build/reports/validation/m1-yoloe-capacity").resolve()
    path = (base / size / "results-v1").resolve()
    if not path.is_relative_to(base):
        raise ValueError("Output escapes capacity workspace")
    if path.exists():
        raise ValueError("Preserve existing capacity results")
    return path


def compare_references(records, references, mapping):
    lookups = {
        model: {(row["key"], row["profile"]): row for row in rows}
        for model, rows in references.items()
    }
    pairs = []
    for row in records:
        if row["dataset"] != "lvis" or "-c0.35-" not in row["profile"]:
            continue
        _, mode, _, cap = row["profile"].split("-")
        for model, prefix in [
            ("baseline", "baseline-aware"),
            ("nano", "yoloe-" + mode),
            ("small", "small-" + mode),
        ]:
            reference = lookups[model].get((row["key"], f"{prefix}-c0.35-{cap}"))
            if reference is None:
                raise ValueError("Missing immutable " + model + " reference")
            for matching, key, scope in [
                ("typed", "masks", "common"),
                ("typed", "masks", "new"),
                ("new_class_agnostic", "geometry_masks_new", "new"),
            ]:
                first = small_adapter.restrict_mask(
                    reference[key], reference["gt_targets"], scope, mapping
                )
                second = small_adapter.restrict_mask(
                    row[key], row["gt_targets"], scope, mapping
                )
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


def aggregate_pairs(pairs):
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
        for model in ["baseline", "nano", "small"]
    }


def session_proof(proof):
    # The inherited session API uses the historical field name; only the index key is bridged.
    return {
        **proof,
        "canonical_from_small_indices": proof["canonical_from_candidate_indices"],
    }
