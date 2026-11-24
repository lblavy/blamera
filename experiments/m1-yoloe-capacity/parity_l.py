"""L-only reviewed channel tolerance; finalize existing evidence without new inference."""

import math

BOUNDS = {
    "coordinates": 0.05,
    "scores": 0.02,
    "coefficients": 0.02,
    "prototypes": 0.005,
}


def validate_diagnosis(diagnosis):
    rows = diagnosis["fixtures"]
    if {row["id"] for row in rows} != {"nasa-astronaut", "synthetic-negative"} or len(
        rows
    ) != 2:
        raise ValueError("Expected exactly two historical fixtures")
    expected = {
        (mode, score, cap)
        for mode in [True, False]
        for score in [0.2, 0.35, 0.5]
        for cap in [20, 100]
    }
    for row in rows:
        for name, bound in BOUNDS.items():
            error = row["channel_errors"][name]["max_abs"]
            if not math.isfinite(error) or not 0 <= error <= bound:
                raise ValueError("L channel tolerance exceeded: " + name)
        if any(
            row["argmax"]["above_confidence"].get(str(score), -1) != 0
            for score in [0.2, 0.35, 0.5]
        ):
            raise ValueError("Argmax drift above declared score")
        profiles = row["profiles"]
        if (
            len(profiles) != 12
            or {(p["agnostic"], p["confidence"], p["cap"]) for p in profiles}
            != expected
        ):
            raise ValueError("Incomplete 12-profile comparison")
        for profile in profiles:
            if (
                not profile["same_anchor_class_order"]
                or profile["matches"] != profile["native_count"]
                or profile["native_count"] != profile["onnx_count"]
            ):
                raise ValueError("NMS/count/anchor/class/order drift")
            if profile["confidence"] == 0.35 and profile["matches"]:
                value = profile["mask_iou_min"]
                if value is None or not math.isfinite(value) or value < 0.99:
                    raise ValueError("Main-score mask parity below .99")


def finalize():
    from pathlib import Path
    import json, ast, onnx
    from capacity_adapter import small_adapter
    from runner_core import sha256

    root = Path(__file__).resolve().parents[2]
    base = root / "build/reports/validation/m1-yoloe-capacity"
    target = base / "l/export-parity.json"
    if target.exists():
        raise ValueError("Preserve existing L proof")
    diagnosis = json.loads((base / "l/diagnosis.json").read_text(encoding="utf-8"))
    validate_diagnosis(diagnosis)
    item = diagnosis["existing_onnx"]
    path = root / item["path"]
    if sha256(path) != item["sha256"]:
        raise ValueError("Do not replace the existing L ONNX")
    graph = onnx.load(path)
    onnx.checker.check_model(graph)
    metadata = {row.key: row.value for row in graph.metadata_props}
    candidate = ast.literal_eval(metadata["names"])
    nano = onnx.load(root / "models/m1-yoloe/yoloe-26n-seg-pf.onnx")
    canonical = ast.literal_eval(
        {row.key: row.value for row in nano.metadata_props}["names"]
    )
    translation = small_adapter.vocabulary_translation(canonical, candidate)
    if len(translation) != 4585:
        raise ValueError("Expected full vocabulary")
    config = json.loads(
        (Path(__file__).with_name("protocol.json")).read_text(encoding="utf-8")
    )
    checkpoint = config["models"]["l"]
    if sha256(root / checkpoint["path"]) != checkpoint["sha256"]:
        raise ValueError("L checkpoint drift")
    parameters = json.loads(
        (base / "x/export-parity.json").read_text(encoding="utf-8")
    )["parameters"]
    shape = lambda value: [
        d.dim_value or d.dim_param for d in value.type.tensor_type.shape.dim
    ]
    result = {
        "status": "L_CHANNEL_PARITY_REVIEWED_AMENDMENT",
        "model": "YOLOE-26l-seg-pf",
        "checkpoint": checkpoint,
        "onnx": item,
        "parameters": parameters,
        "metadata": metadata,
        "canonical_names": canonical,
        "candidate_names": candidate,
        "canonical_from_candidate_indices": translation,
        "vocabulary_identity": translation == list(range(4585)),
        "input_shapes": [shape(v) for v in graph.graph.input],
        "output_shapes": [shape(v) for v in graph.graph.output],
        "vocabulary_comparison": {
            "checked_entries": 4585,
            "all_names_equal": True,
            "order_equal": translation == list(range(4585)),
            "new_semantic_aliases": 0,
        },
        "original_raw_tolerance_stop": {
            "raw_bound": 0.02,
            "observed": 0.03759765625,
            "prototype_observed": 0.00010013580322265625,
            "log": "export-l.log",
            "log_sha256": sha256(base / "export-l.log"),
        },
        "reviewed_L_only_amendment": {
            "approved_before_144": True,
            "channels": BOUNDS,
            "coordinate_units": "network pixels640, .05=1/20 pixel",
            "scores_coefficients_prototypes_bounds_unchanged": True,
            "decisions": "All12profiles same anchor/class/order/count, argmax invariant above.2/.35/.5",
            "mask_check_scope": "Only score.35 caps20/100 and both NMS; IoU>=.99",
            "approval": "Orchestrator and independent reviewer accepted before any144-image forward",
        },
        "parity": diagnosis["fixtures"],
        "diagnosis_sha256": sha256(base / "l/diagnosis.json"),
        "no_reexport": True,
        "no144_predictions": True,
        "inherited_exporter": "experiments/m1-yoloe-small/prepare_export.py unchanged; initial L stop preserved",
        "cache_configuration": "Inherited workspace-only .tools/m1-yoloe-small caches; CPU environment unchanged",
        "export_seconds": None,
    }
    target.write_text(json.dumps(result, indent=2), encoding="utf-8", newline="\n")
    print(
        json.dumps(
            {
                "status": result["status"],
                "onnx": item,
                "vocabulary_identity": result["vocabulary_identity"],
                "original_stop_preserved": True,
                "new_inference": False,
            }
        )
    )


if __name__ == "__main__":
    finalize()
