"""Behavioral tests using synthetic RGB fixtures, never the personal gallery."""

import hashlib
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "pc/src"))
import generate_references as references  # noqa: E402
from blamera_pc.face_data import FaceCandidate, FaceSample  # noqa: E402
from generate_references import generate, reference_artifacts  # noqa: E402


def png(width=20, height=24, color="blue"):
    output = io.BytesIO()
    Image.new("RGB", (width, height), color).save(output, "PNG")
    return output.getvalue()


class FakeEngine:
    def __init__(self):
        self.profile = SimpleNamespace(key="a" * 64, dimension=512)
        self.vector = (1.0, *(0.0 for _ in range(511)))
        self.invalid_count = False
        self.calls = 0
        self.encoded = []

    def detect(self, image):
        self.calls += 1
        with Image.open(io.BytesIO(image)) as picture:
            width = picture.width
        candidate = FaceCandidate(
            (
                2.0,
                3.0,
                8.0,
                9.0,
                3.0,
                5.0,
                8.0,
                5.0,
                5.0,
                7.0,
                3.0,
                10.0,
                8.0,
                10.0,
                0.99,
            )
        )
        if width == 320:
            return [candidate] if self.invalid_count else []
        second = FaceCandidate(
            tuple(
                value + 20.0 if index in (0, 4, 6, 8, 10, 12) else value
                for index, value in enumerate(candidate.values)
            )
        )
        return [candidate, second] if width == 40 else [candidate]

    def encode(self, image, candidate):
        self.encoded.append(candidate)
        return FaceSample(png(112, 112), self.vector)


class ReferenceTests(unittest.TestCase):
    def setUp(self):
        (ROOT / "build").mkdir(exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(prefix="face-ref-test-", dir=ROOT / "build")
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.engine = FakeEngine()

    def test_manifest_freezes_pipeline_and_tolerances_with_three_outcomes(self):
        files = reference_artifacts(png(), self.engine)
        self.assertIn("manifest.json", files)
        manifest = json.loads(files["manifest.json"])
        self.assertEqual(manifest["profile"], {"key": "a" * 64, "dimension": 512})
        self.assertEqual(manifest["tolerances"]["embedding_cosine_min"], 0.9999)
        self.assertEqual(
            [case["expected_kind"] for case in manifest["cases"]],
            ["single", "none", "multiple"],
        )
        single = manifest["cases"][0]
        self.assertEqual(single["bounds"], [2.0, 3.0, 10.0, 12.0])
        self.assertEqual(len(single["landmarks"]), 10)
        self.assertEqual(single["embedding"], list(self.engine.vector))
        self.assertEqual(single["image_sha256"], hashlib.sha256(files[single["image"]]).hexdigest())
        self.assertIn(single["aligned"], files)
        for case in manifest["cases"][1:]:
            self.assertNotIn("embedding", case)
            self.assertNotIn("aligned", case)

    def test_unexpected_face_count_is_not_silently_relabelled(self):
        self.engine.invalid_count = True
        with self.assertRaises(ValueError):
            reference_artifacts(png(), self.engine)

    def test_invalid_embeddings_never_become_reference_data(self):
        for vector in [
            (0.0,) * 512,
            (float("nan"),) * 512,
            (1.0,) * 511,
            (2.0, *(0.0 for _ in range(511))),
        ]:
            with self.subTest(size=len(vector)):
                self.engine.vector = vector
                with self.assertRaises(ValueError):
                    reference_artifacts(png(), self.engine)

    def test_other_profile_dimension_is_refused(self):
        self.engine.profile.dimension = 128
        with self.assertRaises(ValueError):
            reference_artifacts(png(), self.engine)

    def test_corrupted_public_source_is_refused_before_model_calls(self):
        source = self.directory / "public.png"
        source.write_bytes(png())
        with self.assertRaises(ValueError):
            generate(source, self.directory / "references", self.engine, "0" * 64)
        self.assertEqual(self.engine.calls, 0)
        self.assertFalse((self.directory / "references").exists())

    def test_generation_writes_reference_files_and_keeps_source_unchanged(self):
        source = self.directory / "public.png"
        original = png()
        source.write_bytes(original)
        output = self.directory / "references"
        report = generate(source, output, self.engine, hashlib.sha256(original).hexdigest())
        self.assertTrue((output / "manifest.json").exists())
        self.assertEqual(source.read_bytes(), original)
        self.assertEqual(report["cases"], 3)
        self.assertEqual(json.loads((output / "manifest.json").read_text())["schema"], 2)

    def test_multiple_encodes_each_detected_face_with_its_own_geometry_and_crop(self):
        files = reference_artifacts(png(), self.engine)
        manifest = json.loads(files["manifest.json"])
        self.assertEqual(manifest["schema"], 2)
        faces = manifest["cases"][2]["faces"]
        self.assertEqual(len(faces), 2)
        self.assertEqual(len(self.engine.encoded), 3)
        self.assertEqual(faces[0]["bounds"], [2.0, 3.0, 10.0, 12.0])
        self.assertEqual(faces[1]["bounds"], [22.0, 3.0, 30.0, 12.0])
        for index, face in enumerate(faces):
            self.assertEqual(face["aligned"], f"expected/multiple-{index}-aligned.png")
            self.assertIn(face["aligned"], files)
            self.assertEqual(len(face["embedding"]), 512)
        self.assertEqual(len(files), 7)

    def test_invalid_second_face_cannot_be_omitted_from_multiple_reference(self):
        good = FaceSample(png(112, 112), self.engine.vector)
        bad = FaceSample(png(112, 112), (0.0,) * 512)
        with patch.object(self.engine, "encode", side_effect=[good, good, bad]):
            with self.assertRaises(ValueError):
                reference_artifacts(png(), self.engine)

    def test_preexisting_reference_files_cannot_be_silently_changed(self):
        source = self.directory / "public.png"
        source.write_bytes(png())
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        output = self.directory / "references"
        generate(source, output, self.engine, digest)
        manifest = output / "manifest.json"
        manifest.write_bytes(b"existing reference belongs to another generation")
        with self.assertRaises(ValueError):
            generate(source, output, self.engine, digest)
        self.assertEqual(manifest.read_bytes(), b"existing reference belongs to another generation")

    def test_extra_files_cannot_be_accidentally_packaged_as_public_references(self):
        source = self.directory / "public.png"
        source.write_bytes(png())
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        output = self.directory / "references"
        generate(source, output, self.engine, digest)
        (output / "unexpected.txt").write_bytes(b"unrelated file")
        with self.assertRaises(ValueError):
            generate(source, output, self.engine, digest)

    def test_unexpected_file_contents_are_not_read_during_reuse_validation(self):
        source = self.directory / "public.png"
        source.write_bytes(png())
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        output = self.directory / "references"
        generate(source, output, self.engine, digest)
        (output / "unrelated.bin").write_bytes(b"unrelated content")
        original_read = Path.read_bytes

        def read(path):
            if path.name == "unrelated.bin":
                self.fail("Unknown file content must not be read.")
            return original_read(path)

        with patch.object(Path, "read_bytes", autospec=True, side_effect=read):
            with self.assertRaises(ValueError):
                generate(source, output, self.engine, digest)

    def test_wrong_sized_reference_is_rejected_before_loading_its_contents(self):
        source = self.directory / "public.png"
        source.write_bytes(png())
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        output = self.directory / "references"
        generate(source, output, self.engine, digest)
        (output / "manifest.json").write_bytes(b"oversized" * 10000)
        original_read = Path.read_bytes

        def read(path):
            if path == output / "manifest.json":
                self.fail("Wrong-sized artifact must not be loaded.")
            return original_read(path)

        with patch.object(Path, "read_bytes", autospec=True, side_effect=read):
            with self.assertRaises(ValueError):
                generate(source, output, self.engine, digest)

    def test_write_failure_does_not_publish_a_partial_directory(self):
        source = self.directory / "public.png"
        source.write_bytes(png())
        output = self.directory / "references"
        original_write = Path.write_bytes

        def write(path, content):
            if path.name == "portrait-aligned.png":
                raise OSError("synthetic disk failure")
            return original_write(path, content)

        with patch.object(Path, "write_bytes", autospec=True, side_effect=write):
            with self.assertRaises(OSError):
                generate(
                    source,
                    output,
                    self.engine,
                    hashlib.sha256(source.read_bytes()).hexdigest(),
                )
        self.assertFalse(output.exists())

    def test_identical_generation_reuses_files_without_writing(self):
        source = self.directory / "public.png"
        source.write_bytes(png())
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        output = self.directory / "references"
        first = generate(source, output, self.engine, digest)
        with patch.object(Path, "write_bytes", side_effect=AssertionError("Unexpected rewrite")):
            second = generate(source, output, self.engine, digest)
        self.assertEqual(first, second)

    def test_cli_default_publishes_v2_and_refuses_outputs_outside_build(self):
        source = self.directory / "models/m3-02/astronaut.png"
        source.parent.mkdir(parents=True)
        content = png()
        source.write_bytes(content)
        output = io.StringIO()
        with (
            patch.object(references, "ROOT", self.directory),
            patch.object(references, "SOURCE_SHA256", hashlib.sha256(content).hexdigest()),
            patch.object(references, "OpenCvFaceEngine", return_value=self.engine),
            patch("sys.argv", ["generate_references"]),
            redirect_stdout(output),
        ):
            references.main()
        report = json.loads(output.getvalue())
        self.assertEqual(report["files"], 7)
        self.assertTrue((self.directory / "build/fixtures/m3-05/manifest.json").is_file())
        invalid = self.directory / "outside-build"
        with (
            patch.object(references, "ROOT", self.directory),
            patch("sys.argv", ["generate_references", "--output", str(invalid)]),
            redirect_stderr(io.StringIO()),
        ):
            with self.assertRaises(SystemExit):
                references.main()
            with self.assertRaises(ValueError):
                generate(source, invalid, self.engine, hashlib.sha256(content).hexdigest())
        self.assertFalse(invalid.exists())


if __name__ == "__main__":
    unittest.main()
