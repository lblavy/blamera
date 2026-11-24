"""Decode a complete manual replay and compare app detections with fixed PC references."""

import argparse
import hashlib
import json
from pathlib import Path

from benchmark_metrics import compare_objects, validate_catalog, validate_report
from benchmark_report import MAX_REPORT_BYTES, decode_transfer

DEFAULT_REFERENCE_POLICY = Path(__file__).resolve().parents[1] / "config/benchmark-references.json"


def checked_reference_bytes(path, expected):
    if path.stat().st_size > MAX_REPORT_BYTES:
        raise ValueError("Reference too large")
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected:
        raise ValueError("Pinned reference checksum mismatch")
    return raw


def reference_index(policy_file, references):
    policy = json.loads(policy_file.read_text(encoding="utf-8"))
    root = policy_file.parent.parent.resolve()
    if references.resolve() != (root / policy["reference.root"]).resolve():
        raise ValueError("Wrong reference directory for pinned index")
    index = json.loads(checked_reference_bytes(root / policy["index.file"], policy["index.sha256"]))
    catalog_raw = checked_reference_bytes(root / policy["catalog.file"], policy["catalog.sha256"])
    if index["catalogSha256"] != policy["catalog.sha256"]:
        raise ValueError("Index/catalog mismatch")
    catalog = dict(
        line.split("=", 1)
        for line in catalog_raw.decode("utf-8").splitlines()
        if line and not line.startswith("#")
    )
    entries = {(entry["model"], entry["input"]): entry for entry in index["references"]}
    if len(entries) != len(index["references"]) or len(entries) != 56:
        raise ValueError("Missing or duplicate pinned reference")
    return root, entries, catalog


def checked_reference(row, references, root, index):
    entry = index[(row["model"], row["fixture"])]
    path = references / row["model"] / (row["fixture"] + ".json")
    if path.resolve() != (root / entry["file"]).resolve():
        raise ValueError("Reference/index path mismatch")
    reference = json.loads(checked_reference_bytes(path, entry["sha256"]))
    if (reference["model"], reference["input"]) != (row["model"], row["fixture"]):
        raise ValueError("Reference identity mismatch")
    return reference


def analyze(log, references, policy_file=None):
    if log.stat().st_size > 20 * 1024 * 1024:
        raise ValueError("Capture too large")
    document = decode_transfer(log.read_text(encoding="utf-8").splitlines())
    summary = validate_report(document)
    root, index, catalog = reference_index(policy_file or DEFAULT_REFERENCE_POLICY, references)
    validate_catalog(document, catalog)
    comparisons = []
    for row in document["quality"]:
        reference = checked_reference(row, references, root, index)
        comparisons.append(
            dict(
                model=row["model"],
                fixture=row["fixture"],
                sameTensor=row["tensorSha256"] == reference["tensorSha256"],
                comparison=compare_objects(row["objects"], reference["detections"]),
            )
        )
    return document, {**summary, "comparisons": comparisons}


def markdown(summary):
    lines = [
        "# Diagnostic CPU — " + summary["runId"],
        "",
        "Durées médianes écoulées en ms ; run inclut NMS. Total : tenseur prêt → masques/contours prêts.",
        "",
        "| Modèle | Image | Run + NMS | Copie | Masques/contours | Total apparié |",
        "| --- | --- | ---: | ---: | ---: | ---: |",
    ]
    for block in summary["blocks"]:
        values = " | ".join(
            f"{block[key] / 1e6:.3f}"
            for key in ["runMedianNs", "copyMedianNs", "postMedianNs", "totalMedianNs"]
        )
        lines.append(f"| {block['model']} | {block['fixture']} | {values} |")
    lines += [
        "",
        "PSS : échantillons du processus, jamais un pic ni une allocation attribuée au modèle.",
        "Les comparaisons PC ne sont pas une vérité terrain. Tensor différent : écart de pipeline à examiner avant toute attribution à ARM.",
        "",
        "| Modèle | Image | Tenseur identique | Paires géométriques | Noms différents | App non appariés | PC non appariés |",
        "| --- | --- | --- | ---: | ---: | ---: | ---: |",
    ]
    for row in summary["comparisons"]:
        comp = row["comparison"]
        names = sum(not p["sameClass"] for p in comp["pairs"])
        lines.append(
            f"| {row['model']} | {row['fixture']} | {row['sameTensor']} | {len(comp['pairs'])} | {names} | {comp['unmatchedApp']} | {comp['unmatchedReference']} |"
        )
    return "\n".join(lines) + "\n"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--references", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--reference-policy", type=Path, default=DEFAULT_REFERENCE_POLICY)
    parser.add_argument(
        "--catalog",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "config/benchmark.properties",
    )
    args = parser.parse_args(argv)
    document, summary = analyze(args.log, args.references, args.reference_policy)
    catalog = dict(
        line.split("=", 1)
        for line in args.catalog.read_text(encoding="utf-8").splitlines()
        if line and not line.startswith("#")
    )
    validate_catalog(document, catalog)
    args.output.mkdir(parents=True, exist_ok=True)
    for name, data in [("report.json", document), ("summary.json", summary)]:
        (args.output / name).write_text(
            json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8"
        )
    (args.output / "summary.md").write_text(markdown(summary), encoding="utf-8")
    print(args.output / "summary.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
