"""Bounded Windows export recipe: restore only the recorded date, require exact bytes.

Exploratory/configuration validation, not imported by the app or quality runner.
The original ONNX is never overwritten; --install-if-missing only creates an absent file.
"""
import argparse
import json
from importlib.metadata import version
from pathlib import Path
import platform
import shutil
import tempfile

# Reuse the pinned spike's pre-import offline/config settings; main() is not executed.
from run_spike import MODEL, ROOT, YOLO, onnx, sha, torch


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--install-if-missing", action="store_true")
    args = parser.parse_args()
    manifest = json.loads((ROOT / "experiments/m1-01/manifest.json").read_text(encoding="utf-8"))
    assert platform.system() == "Windows", "This exact-byte recipe has only been validated on Windows"
    assert platform.python_version() == manifest["python"]
    for package, expected in manifest["versions"].items():
        assert version(package) == expected, f"Pinned version mismatch: {package}"
    assert torch.version.cuda is None and not torch.cuda.is_available()
    assert sha(MODEL) == manifest["checkpoint"]["sha256"], "Checkpoint mismatch"
    original = MODEL.with_suffix(".onnx")
    original_hash = sha(original) if original.exists() else None
    if original_hash is not None:
        assert original_hash == manifest["onnx"]["sha256"], "Existing ONNX mismatch; never overwrite it"

    report_dir = ROOT / "build/reports/validation/m1-03/model-reconstruction"
    report_dir.mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix="reexport-", dir=report_dir))
    assert work.resolve().is_relative_to(report_dir.resolve())
    checkpoint = work / MODEL.name
    shutil.copyfile(MODEL, checkpoint)
    native = YOLO(str(checkpoint))
    native.model.to("cpu").eval()
    native.model.fuse(verbose=False)
    exported = Path(native.export(
        format="onnx", imgsz=640, batch=1, device="cpu", half=False,
        dynamic=False, simplify=False, opset=17, nms=False, verbose=False,
    ))
    assert exported.resolve().is_relative_to(work.resolve()), "Export escaped temporary workspace"
    raw_hash = sha(exported)
    graph = onnx.load(exported)
    before = {item.key: item.value for item in graph.metadata_props}
    dates = [item for item in graph.metadata_props if item.key == "date"]
    assert len(dates) == 1, "Expected exactly one volatile date field"
    dates[0].value = manifest["onnx"]["metadata"]["date"]
    after = {item.key: item.value for item in graph.metadata_props}
    assert {k: v for k, v in before.items() if k != "date"} == {
        k: v for k, v in after.items() if k != "date"
    }, "Metadata other than date changed"
    onnx.checker.check_model(graph)
    normalized = work / "yolo11n-seg-date-restored.onnx"
    onnx.save_model(graph, normalized)
    assert normalized.stat().st_size == manifest["onnx"]["bytes"], "Restored export size mismatch"
    assert sha(normalized) == manifest["onnx"]["sha256"], "Restored export SHA mismatch; stop and investigate"
    assert (sha(original) if original.exists() else None) == original_hash, "Original ONNX changed"

    installed = False
    if args.install_if_missing and original_hash is None:
        with original.open("xb") as target:
            target.write(normalized.read_bytes())
        assert sha(original) == manifest["onnx"]["sha256"]
        installed = True
    result = {
        "status": "passed", "platform": platform.platform(), "python": platform.python_version(),
        "checkpoint_sha256": sha(MODEL), "raw_export_sha256": raw_hash,
        "raw_date": before["date"], "restored_date": after["date"],
        "only_metadata_field_changed": "date", "restored_sha256": sha(normalized),
        "restored_bytes": normalized.stat().st_size, "restored_path": str(normalized.relative_to(ROOT)),
        "original_sha256_before": original_hash,
        "original_sha256_after": sha(original) if original.exists() else None,
        "original_preserved": original_hash is None or sha(original) == original_hash,
        "installed_previously_missing_file": installed,
    }
    (report_dir / "result.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
