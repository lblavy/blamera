"""Regenerate small breakdown tables and correct GT-only visual panels without inference."""

from pathlib import Path
import json
import cv2, numpy as np

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/reports/validation/m1-yoloe"
spec = json.loads(
    (ROOT / "experiments/m1-yoloe/corpus.json").read_text(encoding="utf-8")
)
res = json.loads((OUT / "results.json").read_text())
reverse = {
    i: int(c) for c, row in spec["common_mapping"].items() for i in row["pf_ids"]
}
perclass = {
    k: {str(c): {"gt": 0, "tp": 0, "fp": 0, "fn": 0} for c in [0, 15, 16, 39, 79]}
    for k in ["baseline", "pf-common", "pf-full"]
}
matched = {k: {} for k in perclass}
for rec in res["records"]:
    item = next(i for i in spec["images"] if i["id"] == rec["id"])
    if rec["id"].startswith("coco"):
        gt = item["class_ids"]
        for conf in perclass:
            preds = rec["predictions"][conf]
            cls = (
                [reverse[p["class"]] for p in preds if p["class"] in reverse]
                if conf == "pf-full"
                else [p["class"] for p in preds]
            )
            quality = rec["quality"][conf]
            pairs = quality["matches"]
            usedp = {p for p, g in pairs}
            usedg = {g for p, g in pairs}
            for c, row in perclass[conf].items():
                c = int(c)
                row["gt"] += gt.count(c)
                row["tp"] += sum(gt[g] == c for _, g in pairs)
                row["fp"] += sum(v == c and p not in usedp for p, v in enumerate(cls))
                row["fn"] += sum(v == c and g not in usedg for g, v in enumerate(gt))
            for (_, g), iou in zip(pairs, quality["matched_mask_iou"]):
                matched[conf][(rec["id"], g)] = iou
    else:
        # Main experiment rendered no instance GT for ADE/NASA. Replace ONLY first panel;
        # preserve every pixel of all prediction panels. ADE truth is semantic, not instances.
        target = OUT / "visuals" / f"{rec['id']}.png"
        mosaic = cv2.imread(str(target))
        other = mosaic[:, 400:].copy()
        image = cv2.imread(str(ROOT / item["image"]))
        title = "No annotated GT"
        if rec["id"].startswith("ADE"):
            label = cv2.imread(str(ROOT / item["annotation"]), cv2.IMREAD_UNCHANGED)
            title = "Semantic GT:building/tree/road"
            for name, c in spec["semantic_extension"].items():
                mask = (label == c).astype(np.uint8)
                edges, _ = cv2.findContours(
                    mask, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE
                )
                cv2.drawContours(image, edges, -1, (0, 220, 0), 1)
                yy, xx = np.where(mask)
                if len(xx):
                    cv2.putText(
                        image,
                        name,
                        (int(xx.min()), max(12, int(yy.min()))),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.35,
                        (0, 220, 0),
                        1,
                    )
        elif rec["id"] == "synthetic-negative":
            title = "GT: no real object"
        h = mosaic.shape[0] - 28
        mosaic[:, :400] = 30
        mosaic[28:, :400] = cv2.resize(image, (400, h))
        cv2.putText(
            mosaic, title, (5, 18), cv2.FONT_HERSHEY_SIMPLEX, 0.4, (255, 255, 255), 1
        )
        assert np.array_equal(mosaic[:, 400:], other)
        assert cv2.imwrite(str(target), mosaic)
paired = {}
for conf in ["pf-common", "pf-full"]:
    keys = matched["baseline"].keys() & matched[conf].keys()
    paired[conf] = {
        "same_gt_n": len(keys),
        "baseline_mean": sum(matched["baseline"][k] for k in keys) / len(keys),
        "pf_mean": sum(matched[conf][k] for k in keys) / len(keys),
    }
out = {"per_class": perclass, "paired_mask_iou": paired}
(OUT / "quality-breakdown.json").write_text(json.dumps(out, indent=2), encoding="utf-8")
print(json.dumps(out, indent=2))
