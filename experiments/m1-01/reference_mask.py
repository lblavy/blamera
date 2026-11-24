"""Public NASA mask reference; same bounded non-production spike, no export/benchmark."""
from run_spike import MODEL, OUT, YOLO, cv2, np, sha

assert sha(MODEL) == "55ed65c56c91713d23e8402371c6c49a6fd84f257f7dce452e8d70e41dcbe152"
image = cv2.imread(str(OUT / "astronaut.png"))
assert image is not None
result = YOLO(str(MODEL)).predict(image, imgsz=640, conf=0.35, iou=0.5, device="cpu", verbose=False)[0]
assert result.boxes.cls.tolist() == [0.0]
mask = (result.masks.data[0].cpu().numpy() > 0.5).astype(np.uint8)
assert mask.shape == (640, 640)
target = OUT / "astronaut-pytorch-mask.bin"
target.write_bytes(mask.tobytes())
print({"file": str(target), "sha256": sha(target), "active_pixels": int(mask.sum())})
