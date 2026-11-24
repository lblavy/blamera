"""Assemble reviewed, position-preserving display data without loading any ML runtime."""

import hashlib
import json
from pathlib import Path

from catalog_core import checked_source, make_catalogue, read_checkpoint, validate_retained

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
ENGLISH_SHA = "d8973978ecc1d94436964d44124e9893276d94a317de2868c440cb5ea2e2af35"
REVISION = "6e062862ced6f5622a589ab2aadc2a1d4978db78"


def load_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path, document):
    path.write_text(
        json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n"
    )


def main():
    source_path = ROOT / "models/m1-yoloe-phone-assets/labels4585.json"
    source = checked_source(source_path.read_bytes(), ENGLISH_SHA, 4585)
    checkpoint_path = ROOT / "models/m1-labels-fr/big/checkpoint.json"
    identity = {
        "englishSha256": ENGLISH_SHA,
        "revision": REVISION,
        "beams": 4,
        "maxNewTokens": 48,
        "torch": "2.14.1+cpu",
        "transformers": "4.57.6",
    }
    machine = read_checkpoint(checkpoint_path.read_text(encoding="utf-8"), identity, source)
    overrides_path = HERE / "overrides.json"
    retained_path = HERE / "retained-names.json"
    overrides = load_json(overrides_path)
    catalogue = make_catalogue(source, machine, overrides, ENGLISH_SHA)
    retained = load_json(retained_path)
    count_retained = validate_retained(source, catalogue["labels"], retained)
    target = ROOT / "app/src/main/assets/photo/labels-fr.json"
    write_json(target, catalogue)
    provenance = {
        "schema": 1,
        "locale": "fr",
        "count": len(source),
        "english": {
            "file": "labels4585.json",
            "size": source_path.stat().st_size,
            "sha256": ENGLISH_SHA,
            "license": "AGPL-3.0",
            "ordering": "Unmodified canonical YOLOE prompt-free 4585 vocabulary",
        },
        "catalogue": {"file": target.name, "size": target.stat().st_size, "sha256": sha256(target)},
        "preparation": {
            "offlineCpuOnly": True,
            "model": "Helsinki-NLP/opus-mt-tc-big-en-fr",
            "revision": REVISION,
            "license": "CC-BY-4.0",
            "url": "https://huggingface.co/Helsinki-NLP/opus-mt-tc-big-en-fr/tree/" + REVISION,
            "modelFiles": load_json(HERE / "model-files.lock.json")["files"],
            "generation": {
                **identity,
                "numThreads": 4,
                "interopThreads": 1,
                "batchSize": 32,
                "seed": 0,
                "sampling": False,
                "deterministicAlgorithms": True,
                "input": "raw isolated labels",
                "normalization": "NFC, trim whitespace, remove one final period",
            },
            "checkpointSha256": sha256(checkpoint_path),
            "environmentLockSha256": sha256(HERE / "requirements.lock.txt"),
            "overrides": {
                "file": "experiments/m1-labels-fr/overrides.json",
                "count": len(overrides),
                "sha256": sha256(overrides_path),
            },
            "retainedNames": {
                "file": "experiments/m1-labels-fr/retained-names.json",
                "count": count_retained,
                "sha256": sha256(retained_path),
            },
            "review": "All 4585 pairs read; targeted editorial corrections by coding agents; "
            "1076 pairs independently read by reviewer; not expert linguistic certification",
            "changes": "Machine translation plus explicit French lexical corrections, alternatives "
            "for ambiguous source senses and reuse of existing project COCO French names",
            "rejectedTrials": [
                "opus-mt-en-fr: noun errors and long hallucinated phrases",
                "Big photo-context prefix: weaker probe results than raw labels",
            ],
        },
        "limits": [
            "Automatic model names are predictions, not verified facts",
            "French duplicates may map distinct synonymous English categories; IDs stay distinct",
            "Isolated English labels can remain polysemous; slash alternatives preserve context",
            "Opaque canonical power see, jackcrab and truncated bonasa umbellu are explicit "
            "retained-source exceptions pending upstream clarification",
            "Regional botanical names and scientific genus names may remain unchanged "
            "with an individual reason in retained-names.json",
        ],
        "device": "Static catalogue only; no translation model, SDK, network or paid service on Android",
    }
    write_json(target.with_name("labels-fr-provenance.json"), provenance)
    print(json.dumps(provenance["catalogue"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
