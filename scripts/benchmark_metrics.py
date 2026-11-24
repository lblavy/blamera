"""Validate the bounded app-owned diagnostic; no device access or model execution."""

import base64
import binascii
import hashlib
import math
import statistics
import zlib

from benchmark_report import ReportError

MODELS = ["baseline", "small", "large", "xlarge"]
PERFORMANCE = ["nasa", "lvis_371699"]
SUPPORTED_APP_IDENTITIES = (("0.6.0", 10), ("0.7.0", 11), ("0.8.0", 12), ("0.9.0", 13), ("0.9.1", 14), ("0.9.2", 15), ("0.9.3", 16), ("0.9.4", 17), ("0.10.0", 18), ("0.10.1", 19), ("0.10.2", 20), ("0.10.3", 21), ("0.10.4", 22), ("0.11.0", 23), ("0.12.0", 24), ("0.13.0", 25), ("0.14.0", 26), ("0.14.1", 27), ("0.15.0", 28), ("0.15.1", 29))


def require(condition, message):
    if not condition:
        raise ReportError(message)


def finite_number(value):
    require(
        isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value),
        "Invalid finite number",
    )
    return value


def nonnegative(value):
    require(finite_number(value) >= 0, "Negative measurement")
    return value


def sha(value):
    require(
        isinstance(value, str) and len(value) == 64 and all(c in "0123456789abcdef" for c in value),
        "Invalid SHA256",
    )
    return value


def unpack_mask(mask):
    require(
        (mask["codec"], mask["width"], mask["height"]) == ("zlib-bitpack-msb", 160, 160),
        "Unknown mask codec",
    )
    try:
        compressed = base64.b64decode(mask["data"], validate=True)
        require(len(compressed) <= 6400, "Oversized compressed mask")
        decoder = zlib.decompressobj()
        raw = decoder.decompress(compressed, 3201)
        require(
            len(raw) == 3200
            and decoder.eof
            and not decoder.unused_data
            and not decoder.unconsumed_tail,
            "Truncated, oversized or trailing mask",
        )
    except (ValueError, binascii.Error, zlib.error) as error:
        raise ReportError("Corrupt mask compression") from error
    require(hashlib.sha256(raw).hexdigest() == sha(mask["sha256"]), "Mask SHA mismatch")
    count = sum(byte.bit_count() for byte in raw)
    require(
        mask["activePixels"] == count and mask["empty"] == (count == 0), "Mask occupancy mismatch"
    )
    return raw


def sample_values(sample):
    values = [nonnegative(sample[key]) for key in ["runNs", "copyNs", "postNs", "totalNs"]]
    require(values[3] == sum(values[:3]), "Unpaired total timing")
    require(0 <= sample["emptyMasks"] <= sample["detections"] <= 20, "Invalid object counts")
    require(0 <= sample["segments"] <= 160000, "Unbounded contours")
    return values


def checked_stats(claim, values):
    actual = dict(
        count=len(values), medianNs=statistics.median(values), minNs=min(values), maxNs=max(values)
    )
    require(claim == actual, "Claimed statistics do not match samples")
    return actual


def check_block(block):
    sample_values(block["first"])
    samples = block["samples"]
    require(len(samples) == 10, "Ten measured samples required")
    rows = [sample_values(sample) for sample in samples]
    stats = [
        checked_stats(block[key], [row[i] for row in rows])
        for i, key in enumerate(["run", "copy", "post", "total"])
    ]
    require(block["sessionFirst"] == (block["fixture"] == "nasa"), "Wrong fresh-session label")
    sha(block["fixtureHash"])
    sha(block["tensorSha256"])
    return dict(
        model=block["model"],
        fixture=block["fixture"],
        runMedianNs=stats[0]["medianNs"],
        copyMedianNs=stats[1]["medianNs"],
        postMedianNs=stats[2]["medianNs"],
        totalMedianNs=stats[3]["medianNs"],
    )


def check_quality(row, model):
    sample_values(row["sample"])
    require(0 < row["width"] <= 4096 and 0 < row["height"] <= 4096, "Invalid source dimensions")
    objects = row["objects"]
    require(len(objects) == row["sample"]["detections"], "Object count mismatch")
    empty = 0
    for obj in objects:
        require(
            isinstance(obj["classId"], int) and 0 <= obj["classId"] < model["classes"],
            "Invalid class",
        )
        require(
            isinstance(obj["nameEnglish"], str) and 0 < len(obj["nameEnglish"]) <= 200,
            "Invalid category name",
        )
        require(0.35 <= finite_number(obj["score"]) <= 1, "Invalid score")
        for key in ["box", "networkBox"]:
            box = obj[key]
            require(
                len(box) == 4 and all(math.isfinite(finite_number(v)) for v in box), "Invalid box"
            )
            require(box[2] >= box[0] and box[3] >= box[1], "Reversed box")
        unpack_mask(obj["mask"])
        empty += obj["mask"]["empty"]
        require(0 <= obj["segments"] <= 8000, "Unbounded contour")
    require(empty == row["sample"]["emptyMasks"], "Empty mask count mismatch")


def check_identity(document):
    require(
        (document["schema"], document["protocol"], document["status"])
        == (2, "capacity-phone-v1", "complete"),
        "Wrong report identity/status",
    )
    require(
        isinstance(document["appVersionCode"], int)
        and (document["appVersion"], document["appVersionCode"]) in SUPPORTED_APP_IDENTITIES,
        "Unsupported application version/code pair",
    )
    models = document["models"]
    require([m["id"] for m in models] == MODELS, "Wrong model order")
    for model in models:
        require(model["classes"] == (80 if model["id"] == "baseline" else 4585), "Wrong vocabulary")
        require(model["agnostic"] == (model["id"] != "baseline"), "Wrong NMS mode")
        sha(model["sha256"])
        sha(model["sourceSha256"])
        nonnegative(model["bytes"])
    expected = dict(
        cpu=True,
        intra=2,
        inter=1,
        edge=640,
        confidence=0.35,
        iou=0.5,
        maxDetections=20,
        preCap=None,
        nmsEmbedded=True,
        warmups=3,
        measured=10,
        performanceInputs=PERFORMANCE,
    )
    require(document["settings"] == expected, "Wrong protocol settings")
    fixtures = {f["id"]: f for f in document["fixtures"]}
    require(
        len(fixtures) == len(document["fixtures"]) == 14 and set(PERFORMANCE) <= fixtures.keys(),
        "Wrong fixtures",
    )
    for fixture in fixtures.values():
        sha(fixture["sha256"])
        sha(fixture["referenceTensorSha256"])
    return models, fixtures


def checked_pairs(rows, fixtures):
    pairs = [(row["model"], row["fixture"]) for row in rows]
    expected = {(m, f) for m in MODELS for f in fixtures}
    require(
        len(pairs) == len(expected) and set(pairs) == expected,
        "Missing or duplicate model/fixture pair",
    )


def validate_report(document):
    try:
        return validate_owned_report(document)
    except (KeyError, TypeError, IndexError, OverflowError) as error:
        raise ReportError("Missing or malformed report fields") from error


def validate_owned_report(document):
    models, fixtures = check_identity(document)
    checked_pairs(document["blocks"], PERFORMANCE)
    checked_pairs(document["quality"], fixtures)
    blocks = [check_block(block) for block in document["blocks"]]
    tensors = {}
    mismatches = []
    for row in document["quality"]:
        check_quality(row, next(m for m in models if m["id"] == row["model"]))
        digest = sha(row["tensorSha256"])
        require(
            tensors.setdefault(row["fixture"], digest) == digest, "Models used different tensors"
        )
        if digest != fixtures[row["fixture"]]["referenceTensorSha256"]:
            mismatches.append(dict(model=row["model"], fixture=row["fixture"]))
    for block in document["blocks"]:
        require(block["tensorSha256"] == tensors[block["fixture"]], "Perf/quality tensor mismatch")
        require(
            block["fixtureHash"] == fixtures[block["fixture"]]["sha256"], "Fixture hash mismatch"
        )
    require([t["model"] for t in document["timings"]] == MODELS, "Missing model load timings")
    for timing in document["timings"]:
        nonnegative(timing["readHashNs"])
        nonnegative(timing["sessionNs"])
        for key in ["pssBeforeModelKiB", "pssAfterSessionCloseKiB"]:
            if timing[key] is not None:
                require(nonnegative(timing[key]) > 0, "Invalid PSS sample")
    return dict(
        runId=document["runId"], nativeCalls=168, blocks=blocks, tensorMismatches=mismatches
    )


def compare_objects(actual, reference):
    remaining = set(range(len(reference)))
    pairs = []
    for app_index, obj in enumerate(actual):
        ranked = sorted(
            ((box_iou(obj["box"], reference[i]["box"]), i) for i in remaining), reverse=True
        )
        if not ranked or ranked[0][0] < 0.5:
            continue
        overlap, index = ranked[0]
        remaining.remove(index)
        expected = reference[index]
        raw = base64.b64decode(expected["maskBase64"], validate=True)
        require(
            len(raw) == 3200 and hashlib.sha256(raw).hexdigest() == expected["maskSha256"],
            "Invalid reference mask",
        )
        mask = unpack_mask(obj["mask"])
        intersection = sum((a & b).bit_count() for a, b in zip(mask, raw, strict=True))
        union = sum((a | b).bit_count() for a, b in zip(mask, raw, strict=True))
        pairs.append(
            dict(
                appIndex=app_index,
                referenceIndex=index,
                boxIoU=overlap,
                sameClass=obj["classId"] == expected["classId"],
                appName=obj["nameEnglish"],
                referenceName=expected["nameEnglish"],
                maskIoU=intersection / union if union else None,
                scoreDifference=obj["score"] - expected["score"],
            )
        )
    return dict(
        pairs=pairs, unmatchedApp=len(actual) - len(pairs), unmatchedReference=len(remaining)
    )


def box_iou(first, second):
    intersection = max(0, min(first[2], second[2]) - max(first[0], second[0])) * max(
        0, min(first[3], second[3]) - max(first[1], second[1])
    )

    def area(b):
        return max(0, b[2] - b[0]) * max(0, b[3] - b[1])

    union = area(first) + area(second) - intersection
    return intersection / union if union else 0


def validate_catalog(document, catalog):
    for model in document["models"]:
        prefix = model["id"]
        require(
            model["sha256"] == catalog[prefix + ".sha256"]
            and model["bytes"] == int(catalog[prefix + ".size"]),
            "Model/catalog mismatch",
        )
    for fixture in document["fixtures"]:
        prefix = fixture["id"]
        require(
            fixture["sha256"] == catalog[prefix + ".sha256"]
            and fixture["referenceTensorSha256"] == catalog[prefix + ".tensor.sha256"],
            "Fixture/catalog mismatch",
        )
