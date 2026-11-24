"""Declared post-result amendment: publisher's agnostic NMS, one pass, no retiming."""

from run_evaluation import (
    ROOT,
    OUT,
    SPEC,
    PF,
    PFNAMES,
    REVERSE,
    create,
    prepare,
    decode,
    ground_truth,
    render,
    sha,
)
from metrics import match_instances, mask_iou
import json, datetime
import cv2, numpy as np

amendment = {
    "declared_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    "protocol_unchanged_sha256": sha(ROOT / "experiments/m1-yoloe/corpus.json"),
    "prior_results_sha256": sha(OUT / "results.json"),
    "reason": "Official YOLOE8.4.190 predict defaults agnostic_nms=True; initial comparison explicitly class-aware to match app. Supplement not best-of tuning.",
    "settings": {
        "conf": 0.35,
        "iou": 0.5,
        "max_det": 20,
        "max_candidates": 300,
        "agnostic_nms": True,
    },
    "source": "ultralytics/models/yolo/model.py YOLOE.predict kwargs.setdefault(agnostic_nms,True)",
    "no_new_timing_benchmark": True,
}
(OUT / "agnostic-amendment.json").write_text(
    json.dumps(amendment, indent=2), encoding="utf-8"
)
session = create(PF)
records = []
totals = {"tp": 0, "fp": 0, "fn": 0}
ious = []
(OUT / "agnostic-visuals").mkdir(exist_ok=True)
for item in SPEC["images"]:
    image = cv2.imread(str(ROOT / item["image"]))
    tensor, p = prepare(image)
    raw = session.run(None, {"images": tensor})
    pred, phase = decode(*raw, p, PFNAMES, agnostic=True)
    gt = ground_truth(item, image)
    quality = {}
    if gt is not None:
        common = [
            {**d, "class": REVERSE[d["class"]]} for d in pred if d["class"] in REVERSE
        ]
        quality = match_instances(common, gt)
        for key in totals:
            totals[key] += quality[key]
        ious.extend(x for x in quality["matched_mask_iou"] if x is not None)
    if item["id"].startswith("ADE"):
        annotation = cv2.imread(str(ROOT / item["annotation"]), cv2.IMREAD_UNCHANGED)
        valid = annotation != 0
        for name, cid in SPEC["semantic_extension"].items():
            union = np.zeros(annotation.shape, bool)
            for d in pred:
                if PFNAMES[d["class"]] == name:
                    union |= d["mask"]
            quality[name] = {
                "iou": mask_iou(union, annotation == cid, valid),
                "gt_pixels": int(np.count_nonzero((annotation == cid) & valid)),
            }
    serial = [
        {k: v for k, v in d.items() if k not in ("mask", "segments")}
        | {"name": PFNAMES[d["class"]], "mask_pixels": int(d["mask"].sum())}
        for d in pred
    ]
    records.append(
        {
            "id": item["id"],
            "count": len(pred),
            "predictions": serial,
            "quality": quality,
        }
    )
    destination = OUT / "agnostic-visuals" / f"{item['id']}.png"
    render(
        image,
        None,
        {"pf-full-agnostic": pred},
        {"pf-full-agnostic": PFNAMES},
        destination,
    )
    comparison = cv2.imread(str(destination))
    original = cv2.imread(str(OUT / "visuals" / f"{item['id']}.png"))
    comparison[:, :400] = original[:, 1200:1600]
    cv2.imwrite(str(destination), comparison)
    print(item["id"], len(pred), flush=True)
    del raw
n = totals["tp"]
totals.update(
    precision=n / (n + totals["fp"]),
    recall=n / (n + totals["fn"]),
    matched_mask_iou_mean=float(np.mean(ious)),
)
(OUT / "agnostic-results.json").write_text(
    json.dumps(
        {"amendment": amendment, "common80_metrics": totals, "records": records},
        indent=2,
    ),
    encoding="utf-8",
)
print("TOTAL", totals)
