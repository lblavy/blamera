"""Explicit download/verification phase; never called from export or inference."""

from pathlib import Path
import argparse, hashlib, json, urllib.request, zipfile
import cv2, numpy as np

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/reports/validation/m1-yoloe"
SPEC = json.loads((Path(__file__).parent / "corpus.json").read_text(encoding="utf-8"))
parser = argparse.ArgumentParser()
parser.add_argument("--offline", action="store_true")
args = parser.parse_args()


def fetch(url, path, expected):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        assert not args.offline, f"Missing input: {path}"
        urllib.request.urlretrieve(url, path)
    assert hashlib.sha256(path.read_bytes()).hexdigest() == expected, (
        f"Hash mismatch: {path}"
    )


archive = OUT / "coco128-seg.zip"
fetch(
    "https://github.com/ultralytics/assets/releases/download/v0.0.0/coco128-seg.zip",
    archive,
    "8219f78b7d5ec2a164b2f8bca873918c34d275bc94e3bd87cf01b15d97fe1cad",
)
with zipfile.ZipFile(archive) as z:
    dest = (OUT / "corpus").resolve()
    for name in z.namelist():
        assert (dest / name).resolve().is_relative_to(dest)
    if not (dest / "coco128-seg/images/train2017").exists():
        z.extractall(dest)
for item in SPEC["images"]:
    path = ROOT / item["image"]
    path.parent.mkdir(parents=True, exist_ok=True)
    if item["id"] == "synthetic-negative" and not path.exists():
        cv2.imwrite(str(path), np.full((480, 640, 3), 114, np.uint8))
    fetch(item["source_url"], path, item["sha256"])
    if item.get("annotation"):
        annotation = ROOT / item["annotation"]
        url = (
            item["source_url"]
            .replace("/images/", "/annotations/")
            .replace(".jpg", ".png")
        )
        fetch(url, annotation, item["annotation_sha256"])
fetch(
    "https://github.com/ultralytics/assets/releases/download/v8.4.0/yoloe-26n-seg-pf.pt",
    ROOT / "models/m1-yoloe/yoloe-26n-seg-pf.pt",
    "ae805a9ac11c85214588b32da8248d68a516e1d071465e1e86a03cbfadb66ba7",
)
for notice in json.loads(
    (Path(__file__).parent / "manifest.json").read_text(encoding="utf-8")
)["notices"]:
    fetch(notice["url"], ROOT / notice["path"], notice["sha256"])
print("Verified16 corpus images + annotations + official PF checkpoint + notices")
