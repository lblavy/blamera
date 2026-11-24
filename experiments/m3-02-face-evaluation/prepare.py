"""Bounded public-model spike setup; never reads the personal gallery."""

import json
import re
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor
from hashlib import sha256
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "models/m3-02"
REPORT = ROOT / "build/reports/validation/m3-02-face-evaluation"
ZOO = "48b10fa505aeb51fbd127fc60a5c660040e823b7"
DATA.mkdir(parents=True, exist_ok=True)
REPORT.mkdir(parents=True, exist_ok=True)


def fetch(name, url):
    destination = DATA / name
    if not destination.exists():
        request = urllib.request.Request(
            url, headers={"User-Agent": "Blamera research spike"}
        )
        with (
            urllib.request.urlopen(request, timeout=120) as source,
            destination.open("wb") as target,
        ):
            while chunk := source.read(1024 * 1024):
                target.write(chunk)
    digest = sha256(destination.read_bytes()).hexdigest()
    return {
        "name": name,
        "source": url,
        "bytes": destination.stat().st_size,
        "sha256": digest,
    }


def main():
    base = f"https://media.githubusercontent.com/media/opencv/opencv_zoo/{ZOO}/models"
    downloads = [
        (
            "yunet.onnx",
            f"{base}/face_detection_yunet/face_detection_yunet_2023mar.onnx",
        ),
        (
            "sface.onnx",
            f"{base}/face_recognition_sface/face_recognition_sface_2021dec.onnx",
        ),
        (
            "buffalo_sc.zip",
            "https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_sc.zip",
        ),
        (
            "buffalo_l.zip",
            "https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_l.zip",
        ),
        (
            "astronaut.png",
            "https://raw.githubusercontent.com/scikit-image/scikit-image/v0.25.2/skimage/data/astronaut.png",
        ),
        (
            "YuNet.LICENSE",
            f"https://raw.githubusercontent.com/opencv/opencv_zoo/{ZOO}/models/face_detection_yunet/LICENSE",
        ),
        (
            "SFace.LICENSE",
            f"https://raw.githubusercontent.com/opencv/opencv_zoo/{ZOO}/models/face_recognition_sface/LICENSE",
        ),
    ]
    records = []
    with ThreadPoolExecutor(max_workers=4) as pool:
        futures = [(name, pool.submit(fetch, name, url)) for name, url in downloads]
        for name, future in futures:
            try:
                record = future.result()
                records.append(record)
                print(json.dumps(record), flush=True)
            except (OSError, ValueError, TimeoutError) as error:
                records.append(
                    {"name": name, "status": "unavailable", "error": str(error)}
                )
                print(name, str(error), flush=True)
    for archive, member in [
        ("buffalo_sc.zip", "w600k_mbf.onnx"),
        ("buffalo_l.zip", "w600k_r50.onnx"),
    ]:
        path = DATA / archive
        if path.exists():
            with zipfile.ZipFile(path) as bundle:
                matches = [
                    entry for entry in bundle.namelist() if Path(entry).name == member
                ]
                if len(matches) == 1:
                    content = bundle.read(matches[0])
                    (DATA / member).write_bytes(content)
                    records.append(
                        {
                            "name": member,
                            "source_archive": archive,
                            "member": matches[0],
                            "bytes": len(content),
                            "sha256": sha256(content).hexdigest(),
                        }
                    )
    try:
        nasa_page = "https://www.nasa.gov/people/john-glenn/"
        request = urllib.request.Request(
            nasa_page, headers={"User-Agent": "Blamera research spike"}
        )
        html = urllib.request.urlopen(request, timeout=30).read().decode()
        (REPORT / "nasa-john-glenn-source.html").write_text(html, encoding="utf-8")
        urls = re.findall(r'<meta[^>]+property="og:image"[^>]+content="([^"]+)"', html)
        if not urls:
            urls = re.findall(
                r'<meta[^>]+content="([^"]+)"[^>]+property="og:image"', html
            )
        if urls:
            record = fetch("john-glenn.jpg", urls[0].replace("&amp;", "&"))
            record.update({"source_page": nasa_page, "credit": "NASA"})
            records.append(record)
    except (OSError, ValueError, TimeoutError) as error:
        records.append(
            {"name": "john-glenn.jpg", "status": "unavailable", "error": str(error)}
        )
    (REPORT / "downloads.json").write_text(
        json.dumps({"opencv_zoo_commit": ZOO, "files": records}, indent=2) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
