"""Acquire exactly the reviewed image IDs, never imports or loads a model."""

from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib, json, urllib.request, urllib.parse, zipfile
from input_data import validate_image, duplicate_groups, MAX_IMAGE_BYTES

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "build/reports/validation/m1-yoloe-quality"
SELECTION = ROOT / "experiments/m1-yoloe-quality/selection-proposal.json"
EXPECTED_SELECTION = "15d3afbb6b597625fbbb6604b7ca93a81f0921a36d0afdc2ebde6b6bb5061feb"
OUT = BASE / "inputs"


def destination(dataset, identifier):
    path = (OUT / dataset / (str(identifier) + ".jpg")).resolve()
    if not path.is_relative_to(OUT.resolve()):
        raise ValueError("Output outside workspace inputs")
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def acquire_lvis(row):
    if row["license"]["id"] not in {1, 2, 4, 5}:
        raise ValueError("Image licence outside reviewed scope")
    path = destination("lvis", row["id"])
    uri = row["fetch_url"]
    parsed = urllib.parse.urlparse(uri)
    if (
        parsed.scheme != "https"
        or parsed.netloc != "s3.amazonaws.com"
        or not parsed.path.startswith("/images.cocodataset.org/val2017/")
    ):
        raise ValueError("Image source differs from reviewed official storage")
    if path.exists():
        payload = path.read_bytes()
        mime = "image/jpeg"
        final_uri = uri
        cached = True
    else:
        request = urllib.request.Request(
            uri, headers={"User-Agent": "Blamera-local-quality-research/1.0"}
        )
        with urllib.request.urlopen(request, timeout=45) as response:
            final_uri = response.geturl()
            if final_uri != uri:
                raise ValueError("Unexpected source redirect")
            mime = response.headers.get_content_type()
            payload = response.read(MAX_IMAGE_BYTES + 1)
        cached = False
    checked = validate_image(payload, row, mime)
    if not cached:
        path.write_bytes(payload)
    return {
        "key": "lvis:" + str(row["id"]),
        "dataset": "lvis",
        "id": row["id"],
        "stratum": row["stratum"],
        "path": path.relative_to(ROOT).as_posix(),
        "source_url": row["coco_url"],
        "fetch_url": final_uri,
        "flickr_url": row["flickr_url"],
        "licence": row["license"],
        "mime": mime,
        "cached": cached,
        **checked,
    }


def acquire_ade(rows):
    archive = BASE / "source/ADEChallengeData2016.zip"
    if (
        hashlib.sha256(archive.read_bytes()).hexdigest()
        != "7ff1be44964418441f542a7cc1e1a650e7dc0fc275f5d23252bc9bbdbc977b29"
    ):
        raise ValueError("ADE archive checksum mismatch")
    records = []
    with zipfile.ZipFile(archive) as source:
        for row in rows:
            member = row["image_zip_member"]
            info = source.getinfo(member)
            if info.file_size > MAX_IMAGE_BYTES:
                raise ValueError("Oversized ADE JPEG")
            payload = source.read(member)
            checked = validate_image(payload, row)
            path = destination("ade", row["id"])
            path.write_bytes(payload)
            records.append(
                {
                    "key": "ade:" + row["id"],
                    "dataset": "ade",
                    "id": row["id"],
                    "stratum": "ade_semantic",
                    "path": path.relative_to(ROOT).as_posix(),
                    "zip_member": member,
                    "licence": "ADE local noncommercial research; photos never redistributed",
                    **checked,
                }
            )
    return records


def main():
    if hashlib.sha256(SELECTION.read_bytes()).hexdigest() != EXPECTED_SELECTION:
        raise ValueError("Selection not the reviewed 144 IDs")
    selection = json.loads(SELECTION.read_text(encoding="utf-8"))
    records = []
    with ThreadPoolExecutor(max_workers=4) as pool:
        for index, record in enumerate(pool.map(acquire_lvis, selection["lvis"]), 1):
            records.append(record)
            if index % 16 == 0:
                print(f"LVIS validated {index}/128", flush=True)
    records += acquire_ade(selection["ade"])
    if len(records) != 144:
        raise ValueError("Missing input images")
    duplicates = duplicate_groups(records)
    result = {
        "status": "INPUTS_ACQUIRED_PENDING_RUNNER_REVIEW_NO_INFERENCE",
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "selection_sha256": EXPECTED_SELECTION,
        "images": records,
        "duplicates": duplicates,
        "checksum_scope": "first acquisition hashes; no publisher JPEG checksum claimed",
        "inference_executed": False,
    }
    (BASE / "inputs-manifest.json").write_text(
        json.dumps(result, indent=2), encoding="utf-8"
    )
    print(
        json.dumps(
            {
                "images": len(records),
                "duplicates": duplicates,
                "inference_executed": False,
            }
        ),
        flush=True,
    )


if __name__ == "__main__":
    main()
