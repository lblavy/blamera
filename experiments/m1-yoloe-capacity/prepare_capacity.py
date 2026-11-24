"""Reuse the frozen Small exporter in one isolated process per new checkpoint."""

from pathlib import Path
import json, argparse
from capacity_adapter import require_size
import prepare_export as inherited

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "build/reports/validation/m1-yoloe-capacity"


def export(size):
    require_size(size)
    config = json.loads(
        (Path(__file__).with_name("protocol.json")).read_text(encoding="utf-8")
    )
    item = config["models"][size]
    inherited.CHECKPOINT = ROOT / item["path"]
    inherited.EXPECTED = item["sha256"]
    inherited.OUT = BASE / size
    inherited.OUT.mkdir(parents=True, exist_ok=True)
    inherited.main()
    path = inherited.OUT / "export-parity.json"
    proof = json.loads(path.read_text(encoding="utf-8"))
    proof["model"] = f"YOLOE-26{size}-seg-pf"
    proof["candidate_names"] = proof.pop("small_names")
    proof["canonical_from_candidate_indices"] = proof.pop(
        "canonical_from_small_indices"
    )
    proof["vocabulary_identity"] = proof["canonical_from_candidate_indices"] == list(
        range(4585)
    )
    proof["inherited_exporter"] = (
        "experiments/m1-yoloe-small/prepare_export.py (unchanged; module paths configured only in this process)"
    )
    proof["cache_configuration"] = (
        "Inherited .tools/m1-yoloe-small/{yolo-config,mpl,torch-cache}; workspace-only, no Python environment mutation"
    )
    import onnx

    graph = onnx.load(ROOT / proof["onnx"]["path"])
    shape = lambda value: [
        d.dim_value or d.dim_param for d in value.type.tensor_type.shape.dim
    ]
    proof["input_shapes"] = [shape(value) for value in graph.graph.input]
    proof["output_shapes"] = [shape(value) for value in graph.graph.output]
    proof["vocabulary_comparison"] = {
        "checked_entries": 4585,
        "all_names_equal": True,
        "order_equal": proof["vocabulary_identity"],
        "new_semantic_aliases": 0,
    }
    path.write_text(json.dumps(proof, indent=2), encoding="utf-8", newline="\n")
    print(
        json.dumps(
            {
                key: proof[key]
                for key in [
                    "model",
                    "onnx",
                    "vocabulary_identity",
                    "input_shapes",
                    "output_shapes",
                    "parity",
                ]
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--size", required=True, choices=["x", "m", "l"])
    export(parser.parse_args().size)
