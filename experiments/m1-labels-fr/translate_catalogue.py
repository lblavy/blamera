"""Offline, resumable CPU preparation; this program is not shipped on Android."""

import argparse
import importlib.metadata
import json
import os
import sys
import time
import unicodedata
from pathlib import Path

from catalog_core import checked_source, make_catalogue, read_checkpoint, verify_model_files

ROOT = Path(__file__).resolve().parents[2]
ENGLISH_SHA = "d8973978ecc1d94436964d44124e9893276d94a317de2868c440cb5ea2e2af35"
SOURCE = ROOT / "models/m1-yoloe-phone-assets/labels4585.json"
REVISIONS = {
    "small": "dd7f6540a7a48a7f4db59e5c0b9c42c8eea67f18",
    "big": "6e062862ced6f5622a589ab2aadc2a1d4978db78",
}


def atomic_json(path, document):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".pending")
    temporary.write_text(
        json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    os.replace(temporary, path)


def parse_arguments(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--beams", type=int, default=4)
    parser.add_argument("--variant", choices=REVISIONS, default="big")
    args = parser.parse_args(argv)
    if args.batch_size != 32 or args.threads != 4:
        raise ValueError("This pinned recipe requires batch-size=32 and threads=4")
    return args


def main():
    args = parse_arguments()
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    os.environ["TOKENIZERS_PARALLELISM"] = "false"
    sys.dont_write_bytecode = True
    # Reuse only installed packages through an appended search path, never mutate that venv.
    sys.path.append(str(ROOT / ".tools/m1-model-spike/.venv/Lib/site-packages"))
    import torch
    from transformers import MarianMTModel, MarianTokenizer

    torch.set_num_threads(args.threads)
    torch.set_num_interop_threads(1)
    torch.manual_seed(0)
    torch.use_deterministic_algorithms(True)
    source = checked_source(SOURCE.read_bytes(), ENGLISH_SHA, 4585)
    identity = {
        "englishSha256": ENGLISH_SHA,
        "revision": REVISIONS[args.variant],
        "beams": args.beams,
        "maxNewTokens": 48,
        "torch": torch.__version__,
        "transformers": importlib.metadata.version("transformers"),
    }
    output = ROOT / "models/m1-labels-fr"
    if args.variant == "big":
        output /= "big"
    output.mkdir(parents=True, exist_ok=True)
    checkpoint = output / "checkpoint.json"
    translated = (
        read_checkpoint(checkpoint.read_text(encoding="utf-8"), identity, source)
        if checkpoint.exists()
        else []
    )
    model_path = ROOT / ".tools/m1-labels-fr" / ("model-big" if args.variant == "big" else "model")
    if args.variant == "big":
        manifest = json.loads(
            Path(__file__).with_name("model-files.lock.json").read_text(encoding="utf-8")
        )
        verify_model_files(model_path, manifest["files"])
    tokenizer = MarianTokenizer.from_pretrained(
        model_path, local_files_only=True, trust_remote_code=False
    )
    model = MarianMTModel.from_pretrained(
        model_path,
        local_files_only=True,
        trust_remote_code=False,
        use_safetensors=args.variant == "big",
        attn_implementation="eager",
    ).eval()
    started = time.monotonic()
    print(
        json.dumps(
            {
                "identity": identity,
                "resumingAt": len(translated),
                "threads": args.threads,
                "batchSize": args.batch_size,
            }
        ),
        flush=True,
    )
    with torch.inference_mode():
        for offset in range(len(translated), len(source), args.batch_size):
            batch = source[offset : offset + args.batch_size]
            inputs = tokenizer(batch, return_tensors="pt", padding=True, truncation=False)
            generated = model.generate(
                **inputs, num_beams=args.beams, do_sample=False, max_new_tokens=48
            )
            names = [
                unicodedata.normalize("NFC", value.strip().removesuffix("."))
                for value in tokenizer.batch_decode(generated, skip_special_tokens=True)
            ]
            translated.extend(names)
            # Validate all completed rows before making the durable prefix visible.
            document = {**identity, "translations": translated}
            read_checkpoint(json.dumps(document), identity, source)
            atomic_json(checkpoint, document)
            print(
                f"Translated {len(translated)}/{len(source)} in {time.monotonic() - started:.1f}s",
                flush=True,
            )
    raw = make_catalogue(source, translated, {}, ENGLISH_SHA)
    atomic_json(output / "machine-catalogue.json", raw)
    atomic_json(
        output / "pairs.json",
        [
            {"id": i, "english": en, "french": fr}
            for i, (en, fr) in enumerate(zip(source, translated))
        ],
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
