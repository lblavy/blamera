"""Read-only reuse of the previously validated geometry, never imports its runner."""

from pathlib import Path
import importlib.util
import sys

source = Path(__file__).resolve().parents[1] / "m1-yoloe"
sys.path.insert(0, str(source))
try:
    spec = importlib.util.spec_from_file_location(
        "_blamera_legacy_geometry", source / "postprocess.py"
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
finally:
    sys.path.remove(str(source))
prepare = module.prepare
source_box = module.source_box
source_mask = module.source_mask
contours = module.contours
