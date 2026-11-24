"""Freeze GT-only proposal. Reads local annotations; never downloads images or runs models."""

from pathlib import Path
import hashlib
import json
from collections import defaultdict
import cv2
import numpy as np
from protocol_core import select_metadata, rank, validate_aliases, previous_ade_samples

ROOT = Path(__file__).resolve().parents[2]
FOLDER = Path(__file__).resolve().parent
SOURCE = ROOT / "build/reports/validation/m1-yoloe-quality/source"
config = json.loads((FOLDER / "protocol.json").read_text(encoding="utf-8"))
mapping = json.loads((FOLDER / "mapping-proposal.json").read_text(encoding="utf-8"))
annotation = SOURCE / "lvis_v1_val.json"
assert (
    hashlib.sha256(annotation.read_bytes()).hexdigest()
    == config["lvis_annotation_sha256"]
)
data = json.loads(annotation.read_text(encoding="utf-8"))
all_val = {row["id"]: row for row in data["images"] if "/val2017/" in row["coco_url"]}
assert len(all_val) == 4809
images = {
    identifier: row
    for identifier, row in all_val.items()
    if row["license"] in config["allowed_lvis_image_license_ids"]
}
annotations = defaultdict(list)
for row in data["annotations"]:
    annotations[row["image_id"]].append(row)
groups = {row["key"]: set(row["lvis_ids"]) for row in mapping["groups"]}
target_ids = set().union(*groups.values())
pool = []
for identifier, image in images.items():
    rows = annotations[identifier]
    pool.append(
        {
            "id": identifier,
            "categories": {row["category_id"] for row in rows},
            "rich": len(rows) >= 20,
            "small": any(
                row["category_id"] in target_ids and row["area"] < 32**2 for row in rows
            ),
        }
    )
selection = select_metadata(
    pool,
    groups,
    config["seed"],
    config["control_images"],
    config["targeted_images"],
    config["target_min_distinct_images"],
    config["rich_target_min"],
    config["small_target_min"],
)
licenses = {row["id"]: row for row in data["licenses"]}
records = []
for stratum in ["control", "targeted"]:
    for identifier in selection[stratum]:
        image = images[identifier]
        rows = annotations[identifier]
        original = image["coco_url"]
        relative = original.split("images.cocodataset.org/", 1)[1]
        records.append(
            {
                "id": identifier,
                "stratum": stratum,
                "width": image["width"],
                "height": image["height"],
                "coco_url": original,
                "fetch_url": "https://s3.amazonaws.com/images.cocodataset.org/"
                + relative,
                "flickr_url": image["flickr_url"],
                "license": licenses[image["license"]],
                "image_sha256": None,
                "image_bytes": None,
                "image_downloaded": False,
                "positive_category_ids": sorted({row["category_id"] for row in rows}),
                "neg_category_ids": image["neg_category_ids"],
                "not_exhaustive_category_ids": image["not_exhaustive_category_ids"],
                "annotated_instances": len(rows),
                "target_instances": sum(
                    row["category_id"] in target_ids for row in rows
                ),
                "target_small_instances": sum(
                    row["category_id"] in target_ids and row["area"] < 32**2
                    for row in rows
                ),
                "occlusion": "not measured; no LVIS annotation field",
            }
        )
support = []
for group in mapping["groups"]:
    ids = set(group["lvis_ids"])
    row = {"key": group["key"], "scope": group["scope"]}
    for stratum in ["control", "targeted"]:
        identifiers = selection[stratum]
        row[stratum + "_images"] = sum(
            any(a["category_id"] in ids for a in annotations[i]) for i in identifiers
        )
        row[stratum + "_instances"] = sum(
            a["category_id"] in ids for i in identifiers for a in annotations[i]
        )
    support.append(row)
# ADE uses semantic mask codes, not LVIS category IDs or instance objects.
ade_pool = []
ade_files = {}
ade_groups = {name: set(ids) for name, ids in mapping["ade_category_groups"].items()}
ade_codes = set().union(*ade_groups.values())
for path in sorted((SOURCE / "ade-validation-masks").glob("*.png")):
    mask = cv2.imread(str(path), cv2.IMREAD_UNCHANGED)
    assert mask is not None and mask.ndim == 2
    pixels = {
        int(value): int(count)
        for value, count in zip(*np.unique(mask, return_counts=True))
    }
    present = set(pixels) & ade_codes
    if present:
        ade_pool.append(
            {"id": path.stem, "categories": present, "rich": False, "small": False}
        )
        ade_files[path.stem] = {
            "mask_file": path.relative_to(ROOT).as_posix(),
            "mask_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "width": mask.shape[1],
            "height": mask.shape[0],
            "pixels": pixels,
        }
ade_selection = select_metadata(
    ade_pool,
    ade_groups,
    config["seed"],
    0,
    config["ade_images"],
    config["ade_min_per_category"],
)["targeted"]
ade_records = [
    {
        "id": identifier,
        **ade_files[identifier],
        "image_zip_member": "ADEChallengeData2016/images/validation/"
        + identifier
        + ".jpg",
        "image_sha256": None,
        "image_bytes": None,
        "image_downloaded": False,
        "usage": "local noncommercial research only; never redistribute",
    }
    for identifier in ade_selection
]
old = json.loads(
    (ROOT / "experiments/m1-yoloe/corpus.json").read_text(encoding="utf-8")
)
old_ade = previous_ade_samples(old["images"])
assert old_ade, "Previous ADE exclusions unexpectedly empty"
assert not ({row["id"] for row in old_ade} & set(ade_selection))
visuals = selection["control"][:2]
for key in ["cat", "dog", "bottle", "toothbrush", "trash_can", "lightbulb"]:
    identifiers = [
        i
        for i in selection["targeted"]
        if any(a["category_id"] in groups[key] for a in annotations[i])
    ]
    candidate = next(
        i
        for i in sorted(identifiers, key=lambda i: rank(i, config["seed"], "visual"))
        if i not in visuals
    )
    visuals.append(candidate)
visuals += ade_selection[:4]
assert len(visuals) == 12 and len(set(visuals)) == 12
pf = {
    int(k): v
    for k, v in json.loads(
        (ROOT / "build/reports/validation/m1-yoloe/export.json").read_text(
            encoding="utf-8"
        )
    )["names"].items()
}
baseline = {int(k): v for k, v in old["names80"].items()}
validate_aliases(mapping["pf_aliases"], pf, groups)
validate_aliases(mapping["baseline_aliases"], baseline, groups)
for row in mapping["ade_pf_aliases"]:
    assert pf[row["id"]] == row["name"]
for row in mapping["ade_baseline_aliases"]:
    assert baseline[row["id"]] == row["name"]
result = {
    "status": "PROPOSED_GT_ONLY_AWAITING_REVIEW_NO_IMAGES_NO_PREDICTIONS",
    "seed": config["seed"],
    "source_annotation_sha256": config["lvis_annotation_sha256"],
    "source_population": len(data["images"]),
    "coco_val2017_population": len(all_val),
    "license_admissible_population": len(images),
    "allowed_license_ids": config["allowed_lvis_image_license_ids"],
    "license_revision": "Before any prediction, reject NoDerivs license IDs3/6 and rerun identical seed/quotas",
    "lvis": records,
    "support": support,
    "ade": ade_records,
    "previous_ade_excluded": old_ade,
    "ade_category_support": {
        name: {
            "codes": sorted(codes),
            "images": sum(bool(set(row["pixels"]) & codes) for row in ade_records),
            "pixels": sum(
                sum(count for code, count in row["pixels"].items() if code in codes)
                for row in ade_records
            ),
        }
        for name, codes in ade_groups.items()
    },
    "visual_ids": visuals,
    "strata": {"targeted": 96, "control": 32, "ade_semantic": 16},
    "targeted_rich_images": sum(
        r["annotated_instances"] >= 20 for r in records if r["stratum"] == "targeted"
    ),
    "targeted_small_images": sum(
        r["target_small_instances"] > 0 for r in records if r["stratum"] == "targeted"
    ),
    "images_pending": "Review first; fetch only selected images; then hash/dimensions/exact and perceptual duplicate checks before first prediction",
    "mapping_sha256": hashlib.sha256(
        (FOLDER / "mapping-proposal.json").read_bytes()
    ).hexdigest(),
    "protocol_sha256": hashlib.sha256(
        (FOLDER / "protocol.json").read_bytes()
    ).hexdigest(),
}
(FOLDER / "selection-proposal.json").write_text(
    json.dumps(result, indent=2), encoding="utf-8"
)
print(
    json.dumps(
        {
            key: result[key]
            for key in [
                "status",
                "strata",
                "targeted_rich_images",
                "targeted_small_images",
                "support",
                "visual_ids",
            ]
        },
        indent=2,
    )
)
