"""Explicit Small-only study; baseline/Nano result files are immutable references."""

from pathlib import Path
from contextlib import contextmanager
from types import SimpleNamespace
import sys, json, ssl
from small_adapter import (
    small_profiles,
    canonical_raw,
    native_mapping,
    compare_references,
    aggregate_comparisons,
)
from runner_core import run_frames, sha256
from study_runner import StudyEvaluation, serializable
from study_runtime import GroundTruth, load_image, ort_session, blocked_network
from study_summary import aggregate_records
import cv2, numpy as np

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "build/reports/validation/m1-yoloe-small"
FROZEN = ROOT / "experiments/m1-yoloe-quality"
OLD = ROOT / "build/reports/validation/m1-yoloe-quality"


@contextmanager
def small_session(root, proof, protocol, mapping):
    translation = proof["canonical_from_small_indices"]
    canonical = {int(key): value for key, value in proof["canonical_names"].items()}
    config = {**protocol, "models": {"yoloe": proof["onnx"]}}
    with ort_session(
        root, "yoloe", config, native_mapping(mapping, translation)
    ) as owned:

        def run(tensor):
            raw, proto = owned.run(tensor)
            return canonical_raw(raw, translation), proto

        yield SimpleNamespace(names=canonical, run=run)


def compose_comparisons(output, keys):
    for key in keys:
        old = OLD / "results-v1/panels"
        paths = [
            old / f"{key}-image.png",
            old / f"{key}-gt.png",
            old / f"{key}-baseline-aware.png",
            old / f"{key}-yoloe-agnostic.png",
            output / "panels" / f"{key}-yoloe-agnostic.png",
        ]
        images = [cv2.imread(str(path)) for path in paths]
        if any(image is None for image in images):
            raise ValueError("Missing one of the fixed baseline/Nano/Small panels")
        height = max(image.shape[0] for image in images)
        joined = np.hstack(
            [
                cv2.copyMakeBorder(
                    image,
                    0,
                    height - image.shape[0],
                    0,
                    0,
                    cv2.BORDER_CONSTANT,
                    value=(255, 255, 255),
                )
                for image in images
            ]
        )
        if not cv2.imwrite(str(output / "panels" / f"{key}-comparison.png"), joined):
            raise ValueError("Cannot save Small comparison")


def execute(root, proof, output, seal_sha):
    protocol = json.loads((FROZEN / "protocol.json").read_text(encoding="utf-8"))
    mapping = json.loads((FROZEN / "mapping-proposal.json").read_text(encoding="utf-8"))
    selection = json.loads(
        (FROZEN / "selection-proposal.json").read_text(encoding="utf-8")
    )
    inputs = json.loads((OLD / "inputs-manifest.json").read_text(encoding="utf-8"))
    references = [
        json.loads(line)
        for line in (OLD / "results-v1/per-image.jsonl").open(encoding="utf-8")
    ]
    if output.exists():
        raise ValueError(
            "Preserve existing outputs; choose a new reviewed run destination"
        )
    output.mkdir(parents=True)
    evaluator = StudyEvaluation(
        root,
        selection,
        mapping,
        GroundTruth(root, selection, mapping),
        output / "panels",
    )
    records = []
    with (output / "per-image.jsonl").open("w", encoding="utf-8") as stream:

        def emit(row):
            row = {**row, "evaluated_model": "YOLOE-26s-seg-pf"}
            stream.write(json.dumps(row, default=serializable, allow_nan=False) + "\n")
            stream.flush()
            records.append(row)

        with blocked_network():
            forwards = run_frames(
                inputs["images"],
                small_profiles(protocol),
                lambda _: small_session(root, proof, protocol, mapping),
                lambda row: load_image(root, row),
                evaluator,
                emit,
            )
    if forwards != 144 or len(records) != 1728:
        raise ValueError("Incomplete Small-only pass")
    summary = aggregate_records(records, mapping)
    pairs = compare_references(records, references, mapping)
    summary.update(same_gt_pairs=pairs, paired_strata=aggregate_comparisons(pairs))
    keys = [
        row["key"].replace(":", "-")
        for row in inputs["images"]
        if str(row["id"]) in evaluator.visual
    ]
    compose_comparisons(output, keys)
    (output / "summary.json").write_text(
        json.dumps(
            {
                "status": "complete",
                "small_seal_sha256": seal_sha,
                "reference_results_reused_not_rerun": True,
                "forwards": forwards,
                "records": len(records),
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
    from freeze_small import verify

    parser = argparse.ArgumentParser(
        description="Small-only pass requires independent review before --execute"
    )
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--seal-sha256", required=True)
    parser.add_argument(
        "--output", default="build/reports/validation/m1-yoloe-small/results-v1"
    )
    args = parser.parse_args()
    sys.path.insert(0, str(ROOT / ".tools/m1-yoloe-quality/pydeps"))
    verify(args.seal_sha256)
    if not args.execute:
        print("Small/source/data/reference hashes verified; no corpus inference")
        return
    output = (ROOT / args.output).resolve()
    if not output.is_relative_to(BASE.resolve()):
        raise ValueError("Output outside ignored Small experiment")
    proof = json.loads((BASE / "export-parity.json").read_text(encoding="utf-8"))
    execute(ROOT, proof, output, args.seal_sha256)


if __name__ == "__main__":
    main()
