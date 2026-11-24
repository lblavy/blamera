"""One size at a time; references and inherited engines are never rerun or modified."""

from pathlib import Path
import sys, json, ssl
from capacity_adapter import (
    profiles,
    destination,
    compare_references,
    aggregate_pairs,
    session_proof,
)
from run_small import small_session
from runner_core import run_frames, sha256
from study_runner import StudyEvaluation, serializable
from study_runtime import GroundTruth, load_image, blocked_network
from study_summary import aggregate_records
import cv2, numpy as np

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "build/reports/validation/m1-yoloe-capacity"
OLD = ROOT / "build/reports/validation/m1-yoloe-quality"
SMALL = ROOT / "build/reports/validation/m1-yoloe-small"
FROZEN = ROOT / "experiments/m1-yoloe-quality"


def read_rows(path):
    with path.open(encoding="utf-8") as stream:
        return [json.loads(line) for line in stream]


def compose(output, keys):
    for key in keys:
        old = OLD / "results-v1/panels"
        small = SMALL / "results-v1/panels"
        paths = [
            old / f"{key}-image.png",
            old / f"{key}-gt.png",
            old / f"{key}-baseline-aware.png",
            old / f"{key}-yoloe-agnostic.png",
            small / f"{key}-yoloe-agnostic.png",
            output / "panels" / f"{key}-yoloe-agnostic.png",
        ]
        images = [cv2.imread(str(path)) for path in paths]
        if any(value is None for value in images):
            raise ValueError("Missing immutable reference panel")
        height = max(value.shape[0] for value in images)
        canvas = np.hstack(
            [
                cv2.copyMakeBorder(
                    value,
                    0,
                    height - value.shape[0],
                    0,
                    0,
                    cv2.BORDER_CONSTANT,
                    value=(255, 255, 255),
                )
                for value in images
            ]
        )
        if not cv2.imwrite(str(output / "panels" / f"{key}-comparison.png"), canvas):
            raise ValueError("Cannot write capacity comparison")


def execute(size, seal_sha):
    output = destination(ROOT, size)
    proof = json.loads((BASE / size / "export-parity.json").read_text(encoding="utf-8"))
    protocol = json.loads((FROZEN / "protocol.json").read_text(encoding="utf-8"))
    mapping = json.loads((FROZEN / "mapping-proposal.json").read_text(encoding="utf-8"))
    selection = json.loads(
        (FROZEN / "selection-proposal.json").read_text(encoding="utf-8")
    )
    inputs = json.loads((OLD / "inputs-manifest.json").read_text(encoding="utf-8"))
    old = read_rows(OLD / "results-v1/per-image.jsonl")
    references = {
        "baseline": [r for r in old if r["profile"].startswith("baseline-")],
        "nano": [r for r in old if r["profile"].startswith("yoloe-")],
        "small": read_rows(SMALL / "results-v1/per-image.jsonl"),
    }
    output.mkdir(parents=True)
    evaluator = StudyEvaluation(
        ROOT,
        selection,
        mapping,
        GroundTruth(ROOT, selection, mapping),
        output / "panels",
    )
    records = []
    with (output / "per-image.jsonl").open("w", encoding="utf-8") as stream:

        def emit(row):
            row = {**row, "evaluated_model": proof["model"]}
            stream.write(json.dumps(row, default=serializable, allow_nan=False) + "\n")
            stream.flush()
            records.append(row)

        with blocked_network():
            forwards = run_frames(
                inputs["images"],
                profiles(protocol, size),
                lambda _: small_session(ROOT, session_proof(proof), protocol, mapping),
                lambda row: load_image(ROOT, row),
                evaluator,
                emit,
            )
    if forwards != 144 or len(records) != 1728:
        raise ValueError("Incomplete capacity pass")
    summary = aggregate_records(records, mapping)
    pairs = compare_references(records, references, mapping)
    summary.update(same_gt_pairs=pairs, paired_strata=aggregate_pairs(pairs))
    compose(
        output,
        [
            row["key"].replace(":", "-")
            for row in inputs["images"]
            if str(row["id"]) in evaluator.visual
        ],
    )
    (output / "summary.json").write_text(
        json.dumps(
            {
                "status": "complete",
                "size": size,
                "capacity_seal_sha256": seal_sha,
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
        newline="\n",
    )


def main():
    import argparse
    from freeze_capacity import verify

    parser = argparse.ArgumentParser(
        description="No corpus inference until review/GO; run X then M then L sequentially"
    )
    parser.add_argument("--size", required=True, choices=["x", "m", "l"])
    parser.add_argument("--seal-sha256", required=True)
    parser.add_argument("--execute", action="store_true")
    args = parser.parse_args()
    sys.path.insert(0, str(ROOT / ".tools/m1-yoloe-quality/pydeps"))
    verify(args.seal_sha256)
    if not args.execute:
        print(
            "Capacity sources/models/inputs/references verified; no144-image prediction"
        )
        return
    execute(args.size, args.seal_sha256)


if __name__ == "__main__":
    main()
