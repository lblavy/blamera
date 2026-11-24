"""Explicit study runner. Importing this file performs no loading or inference."""

from pathlib import Path
import json, sys, math
import numpy as np
from study_postprocess import FrameDecoder, FrameMasks, build_profiles
from study_evaluation import score_lvis, score_ade, map_predictions
from study_summary import aggregate_records
from runner_core import run_frames, verify_files, sha256
from study_runtime import GroundTruth, load_image, ort_session, blocked_network
from study_panels import save_panel, compose_panels

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "build/reports/validation/m1-yoloe-quality"
SOURCE = Path(__file__).resolve().parent


def serializable(value):
    if isinstance(value, np.ndarray):
        return value.tolist()
    if isinstance(value, np.generic):
        return value.item()
    raise TypeError(type(value).__name__)


class StudyEvaluation:
    def __init__(self, root, selection, mapping, truth, panels):
        self.root = root
        self.mapping = mapping
        self.truth = truth
        self.panels = panels
        self.visual = set(map(str, selection["visual_ids"]))

    def __call__(self, record, model, names, raw, proto, projection, profiles):
        decoder = FrameDecoder(raw, proto, projection, len(names))
        cache = FrameMasks(raw, proto, projection, len(names), limit=200)
        tolerance = max(1, round(0.005 * math.hypot(record["width"], record["height"])))
        instance_truth = (
            self.truth.instances(record) if record["dataset"] == "lvis" else None
        )
        semantic_truth = (
            self.truth.semantic(record) if record["dataset"] == "ade" else None
        )
        visual = str(record["id"]) in self.visual
        if visual and model == "baseline":
            self.save_truth(record, instance_truth, semantic_truth)
        for profile in profiles:
            selected, stats = decoder.select(
                profile["confidence"], profile["agnostic_nms"], profile["cap"]
            )
            masks = cache if profile["mask_metrics"] else None
            if record["dataset"] == "lvis":
                metadata = {
                    **self.truth.metadata[record["id"]],
                    "all_ground_truth": self.truth.all_boxes(record),
                }
                scored = score_lvis(
                    selected,
                    instance_truth,
                    metadata,
                    self.mapping,
                    model,
                    masks,
                    tolerance,
                )
                scored["image_axes"] = [record["stratum"]]
                if metadata.get("annotated_instances", 0) >= 20:
                    scored["image_axes"].append("rich_images")
                if metadata.get("target_small_instances", 0) >= 1:
                    scored["image_axes"].append("images_with_small_target")
                scored["annotated_instances"] = metadata.get(
                    "annotated_instances", len(metadata["all_ground_truth"])
                )
                scored["gt_targets"] = {
                    str(row["id"]): row["target"] for row in instance_truth
                }
            elif masks:
                scored = score_ade(
                    selected, semantic_truth, self.mapping, model, masks, tolerance
                )
            else:
                predictions = map_predictions(
                    selected, self.mapping, model, semantic=True
                )
                scored = {
                    "predictions": predictions,
                    "unmapped_count": sum(p["target"] is None for p in predictions),
                    "empty_masks": None,
                    "semantic": None,
                }
            for prediction in scored["predictions"]:
                prediction["name"] = names[prediction["class"]]
            if visual and profile["mask_metrics"] and profile["cap"] == 20:
                self.save_predictions(record, profile, scored["predictions"], cache)
            # Only compact dictionaries survive the frame; neither raw outputs nor source masks enter JSON.
            compact = [
                {
                    key: value
                    for key, value in prediction.items()
                    if key != "network_box"
                }
                for prediction in scored["predictions"]
            ]
            yield {
                **scored,
                "predictions": compact,
                "key": record["key"],
                "id": record["id"],
                "dataset": record["dataset"],
                "stratum": record["stratum"],
                "profile": profile["key"],
                "stats": stats,
            }

    def save_truth(self, record, instances, semantic):
        image = load_image(self.root, record)
        key = record["key"].replace(":", "-")
        save_panel(self.panels / f"{key}-image.png", image, record["key"], [])
        items = (
            [
                {"mask": g["mask"], "label": f"GT {g['id']} {g['target']}"}
                for g in instances
            ]
            if instances is not None
            else [
                {
                    "mask": np.isin(semantic, codes),
                    "label": f"GT {target} codes {codes}; void=0 ignored",
                }
                for target, codes in self.mapping["ade_category_groups"].items()
            ]
        )
        save_panel(
            self.panels / f"{key}-gt.png",
            image,
            "Partial LVIS GT / semantic ADE GT",
            items,
        )

    def save_predictions(self, record, profile, predictions, cache):
        image = load_image(self.root, record)
        key = record["key"].replace(":", "-")
        mode = "agnostic" if profile["agnostic_nms"] else "aware"
        items = [
            {
                "segments": cache.segments(p),
                "label": f"{p['name']} {p['score']:.3f} -> {p['target'] or 'UNMAPPED'}",
            }
            for p in predictions
        ]
        save_panel(
            self.panels / f"{key}-{profile['model']}-{mode}.png",
            image,
            profile["key"],
            items,
        )


def execute(root, seal, output):
    protocol = json.loads((SOURCE / "protocol.json").read_text(encoding="utf-8"))
    selection = json.loads(
        (SOURCE / "selection-proposal.json").read_text(encoding="utf-8")
    )
    mapping = json.loads((SOURCE / "mapping-proposal.json").read_text(encoding="utf-8"))
    inputs = json.loads(
        (root / seal["inputs_manifest"]["path"]).read_text(encoding="utf-8")
    )
    if output.exists():
        raise ValueError(
            "Output already exists; preserve previous complete or failed run"
        )
    output.mkdir(parents=True)
    truth = GroundTruth(root, selection, mapping)
    evaluator = StudyEvaluation(root, selection, mapping, truth, output / "panels")
    records = []
    with (output / "per-image.jsonl").open("w", encoding="utf-8") as stream:

        def emit(row):
            stream.write(json.dumps(row, default=serializable, allow_nan=False) + "\n")
            stream.flush()
            records.append(row)

        with blocked_network():
            forwards = run_frames(
                inputs["images"],
                build_profiles(protocol),
                lambda model: ort_session(root, model, protocol, mapping),
                lambda row: load_image(root, row),
                evaluator,
                emit,
            )
    if forwards != 288 or len(records) != 2592:
        raise ValueError("Incomplete forwards/profile records")
    summary = aggregate_records(records, mapping)
    keys = [
        row["key"].replace(":", "-")
        for row in inputs["images"]
        if str(row["id"]) in evaluator.visual
    ]
    compose_panels(output / "panels", keys)
    (output / "summary.json").write_text(
        json.dumps(
            {
                "status": "complete",
                "seal_sha256": sha256(BASE / "sealed-manifest.json"),
                "forwards": forwards,
                **summary,
            },
            indent=2,
            default=serializable,
            allow_nan=False,
        ),
        encoding="utf-8",
    )


def main():
    import argparse
    from freeze_study import verify_seal

    parser = argparse.ArgumentParser(
        description="No prediction unless --execute and exact seal SHA are supplied after independent review"
    )
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--seal-sha256", required=True)
    parser.add_argument(
        "--output", default="build/reports/validation/m1-yoloe-quality/results-v1"
    )
    args = parser.parse_args()
    sys.path.insert(0, str(ROOT / ".tools/m1-yoloe-quality/pydeps"))
    seal = verify_seal(ROOT, BASE / "sealed-manifest.json", args.seal_sha256)
    if not args.execute:
        print(
            "Frozen sources and inputs verified; no model imported or prediction executed"
        )
        return
    output = (ROOT / args.output).resolve()
    if not output.is_relative_to(BASE.resolve()):
        raise ValueError("Results must stay inside ignored study directory")
    sys.path.insert(0, str(ROOT / ".tools/m1-yoloe-quality/pydeps"))
    execute(ROOT, seal, output)


if __name__ == "__main__":
    main()
