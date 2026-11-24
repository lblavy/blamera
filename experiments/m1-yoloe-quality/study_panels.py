"""Fixed-ID diagnostic panels; every selected label remains in the legend."""

from pathlib import Path
import cv2
import numpy as np
from protocol_core import boundary


def panel(image, title, items):
    canvas = image.copy()
    legend = []
    for index, item in enumerate(items):
        color = (
            (37 * index + 50) % 220 + 20,
            (73 * index + 30) % 220 + 20,
            (113 * index + 70) % 220 + 20,
        )
        if "mask" in item:
            canvas[boundary(item["mask"])] = color
        for segment in item.get("segments", []):
            cv2.line(
                canvas,
                tuple(np.rint(segment[0]).astype(int)),
                tuple(np.rint(segment[1]).astype(int)),
                color,
                1,
                cv2.LINE_AA,
            )
        text = item["label"]
        legend.append((text, color))
    width = max(460, image.shape[1])
    height = image.shape[0] + 48 + 24 * len(legend)
    result = np.full((height, width, 3), 255, np.uint8)
    result[: image.shape[0], : image.shape[1]] = canvas
    cv2.putText(
        result,
        title,
        (8, image.shape[0] + 22),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.55,
        (0, 0, 0),
        1,
        cv2.LINE_AA,
    )
    for i, (text, color) in enumerate(legend):
        cv2.putText(
            result,
            text,
            (8, image.shape[0] + 48 + i * 24),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.43,
            color,
            1,
            cv2.LINE_AA,
        )
    return result


def save_panel(path, image, title, items):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not cv2.imwrite(str(path), panel(image, title, items)):
        raise ValueError("Cannot save panel")


def compose_panels(directory, keys):
    for key in keys:
        paths = [
            directory / f"{key}-{suffix}.png"
            for suffix in ["image", "gt", "baseline-aware", "yoloe-agnostic"]
        ]
        images = [cv2.imread(str(path)) for path in paths]
        if any(image is None for image in images):
            raise ValueError("Missing fixed comparison panel")
        height = max(image.shape[0] for image in images)
        padded = [
            cv2.copyMakeBorder(
                image,
                0,
                height - image.shape[0],
                0,
                0,
                cv2.BORDER_CONSTANT,
                value=(255, 255, 255),
            )
            for image in images
        ]
        if not cv2.imwrite(str(directory / f"{key}-comparison.png"), np.hstack(padded)):
            raise ValueError("Cannot compose panels")
