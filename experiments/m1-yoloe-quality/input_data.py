"""Explicit image acquisition and sealing; no model imports or inference."""

from collections import defaultdict
import hashlib
import io
from PIL import Image, UnidentifiedImageError

MAX_IMAGE_BYTES = 20 * 1024 * 1024
MAX_IMAGE_PIXELS = 4_000_000


def validate_image(data, expected, mime="image/jpeg"):
    if (
        mime.split(";", 1)[0].lower() != "image/jpeg"
        or not 0 < len(data) <= MAX_IMAGE_BYTES
    ):
        raise ValueError("Unexpected MIME or image length")
    try:
        with Image.open(io.BytesIO(data)) as image:
            if image.format != "JPEG" or image.width * image.height > MAX_IMAGE_PIXELS:
                raise ValueError("Invalid JPEG geometry")
            if (image.width, image.height) != (expected["width"], expected["height"]):
                raise ValueError("Image dimensions differ from GT")
            if image.getexif().get(274, 1) != 1:
                raise ValueError(
                    "Unexpected EXIF orientation; do not silently rotate GT"
                )
            image.verify()
        with Image.open(io.BytesIO(data)) as image:
            grey = image.convert("L").resize((9, 8), Image.Resampling.BILINEAR)
            values = list(grey.getdata())
            bits = 0
            for y in range(8):
                for x in range(8):
                    bits = (bits << 1) | int(values[y * 9 + x] > values[y * 9 + x + 1])
    except (UnidentifiedImageError, OSError) as error:
        raise ValueError("Invalid JPEG content") from error
    return {
        "sha256": hashlib.sha256(data).hexdigest(),
        "bytes": len(data),
        "width": expected["width"],
        "height": expected["height"],
        "dhash64": f"{bits:016x}",
    }


def duplicate_groups(records, distance=4):
    groups = defaultdict(list)
    for row in records:
        groups[row["sha256"]].append(row["key"])
    exact = [keys for keys in groups.values() if len(keys) > 1]
    near = []
    for i, a in enumerate(records):
        for b in records[i + 1 :]:
            if a["sha256"] == b["sha256"]:
                continue
            difference = (int(a["dhash64"], 16) ^ int(b["dhash64"], 16)).bit_count()
            if difference <= distance:
                near.append([a["key"], b["key"], difference])
    return {
        "exact": exact,
        "near": near,
        "near_rule": "dHash64 Hamming<=4; candidates only, no auto substitution",
    }
