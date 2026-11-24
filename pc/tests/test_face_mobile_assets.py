"""Le provisionnement ne publie jamais un mélange ou un ancien seed personnel."""

import hashlib
import io
import json
import os
import shutil
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch

from src.blamera_pc import face_mobile_assets as assets
from src.blamera_pc.face_data import FaceProfile
from src.blamera_pc.face_mobile_assets import (
    FaceAssetError,
    FaceAssetSource,
    parity_asset_sources,
    prepare_face_assets,
    validate_bootstrap,
)
from src.blamera_pc.face_models import load_configuration

ROOT = Path(__file__).resolve().parents[2]
PROFILE = FaceProfile("4a6472a476b37ec36626f41542d7fd41f13da63dfbeb09cd1ffbc480993aded1", 512)


class FaceMobileAssetTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=ROOT / "build")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.output = self.root / "app/build/generated/faceAssets"
        self.output.mkdir(parents=True)
        (self.output / "last-good").write_bytes(b"complete old bundle")

    def source(self, relative, value=b"model", **overrides):
        path = self.root / ("input-" + str(len(list(self.root.glob("input-*")))))
        path.write_bytes(value)
        return FaceAssetSource(
            path,
            relative,
            **{"size": len(value), "sha256": hashlib.sha256(value).hexdigest(), **overrides},
        )

    def test_explicit_seed_then_generic_build_has_no_stale_personal_file(self):
        model = self.source("faces/models/model.onnx")
        seed = self.source("faces/bootstrap.json", b"synthetic private fixture")
        prepare_face_assets(self.root, self.output, [model, seed])
        self.assertEqual((self.output / model.relative).read_bytes(), b"model")
        self.assertTrue((self.output / seed.relative).is_file())
        self.assertFalse((self.output / "last-good").exists())
        prepare_face_assets(self.root, self.output, [model])
        self.assertFalse((self.output / seed.relative).exists())
        self.assertEqual(seed.path.read_bytes(), b"synthetic private fixture")

    def test_wrong_size_or_digest_does_not_replace_previous_complete_output(self):
        for override in ({"size": 2}, {"sha256": "0" * 64}):
            with self.subTest(override=override):
                good = self.source("faces/models/first.onnx")
                bad = self.source("faces/models/second.onnx", **override)
                with self.assertRaises(FaceAssetError):
                    prepare_face_assets(self.root, self.output, [good, bad])
                self.assertEqual((self.output / "last-good").read_bytes(), b"complete old bundle")
                self.assertFalse((self.output / good.relative).exists())

    def test_commit_failure_restores_the_previous_complete_bundle(self):
        original = os.replace

        def replace(source, target):
            if Path(target) == self.output and "pending" in Path(source).name:
                raise OSError("synthetic commit failure")
            return original(source, target)

        with patch("src.blamera_pc.face_mobile_assets.os.replace", side_effect=replace):
            with self.assertRaises(FaceAssetError):
                prepare_face_assets(self.root, self.output, [self.source("faces/model.onnx")])
        self.assertEqual((self.output / "last-good").read_bytes(), b"complete old bundle")

    def test_destination_paths_and_duplicate_asset_names_are_not_arbitrary(self):
        source = self.source("faces/model.onnx")
        for output in (self.root, self.root / "app/build", self.root / "source-folder"):
            with self.subTest(output=output):
                with self.assertRaises(FaceAssetError):
                    prepare_face_assets(self.root, output, [source])
        for relative in ("../escape", "faces/../../escape", "faces\\..\\escape", "/absolute"):
            with self.subTest(relative=relative):
                with self.assertRaises(FaceAssetError):
                    prepare_face_assets(self.root, self.output, [self.source(relative)])
        with self.assertRaises(FaceAssetError):
            prepare_face_assets(self.root, self.output, [source, source])

    def test_bootstrap_uses_the_closed_bounded_approved_package_contract(self):
        raw = (ROOT / "config/fixtures/face-gallery-v1.json").read_bytes()
        self.assertEqual(validate_bootstrap(raw), raw)
        malformed = [b"{}", raw + b"x", b"x" * 122881, b"\xef\xbb\xbf" + raw]
        for mutate in (
            lambda x: x.update(schema=True),
            lambda x: x["profile"].update(key="0" * 64),
            lambda x: x.update(extra="forbidden"),
            lambda x: x["policy"].update(accept=float("nan")),
            lambda x: x["identities"][0].update(prototype=[0.0] * 512),
        ):
            value = json.loads(raw)
            mutate(value)
            malformed.append(json.dumps(value).encode())
        malformed.append(raw.replace(b'"schema": 1', b'"schema":1,"schema":1', 1))
        for value in malformed:
            with self.subTest(length=len(value)):
                with self.assertRaises(FaceAssetError):
                    validate_bootstrap(value)

    def test_parity_manifest_profile_thresholds_and_every_image_hash_are_checked(self):
        fixture = ROOT / "build/fixtures/m3-05"
        self.assertEqual(len(parity_asset_sources(fixture, PROFILE)), 7)
        manifest = json.loads((fixture / "manifest.json").read_text())
        for update in (
            lambda x: x["profile"].update(key="0" * 64),
            lambda x: x["tolerances"].update(embedding_cosine_min=0.9),
            lambda x: x["cases"][0].update(image_sha256="0" * 64),
            lambda x: x["cases"][0].update(image="../escape.png"),
            lambda x: x["tolerances"].update(bounds_abs_px=True),
        ):
            local = self.root / "parity"
            shutil.copytree(fixture, local, dirs_exist_ok=True)
            value = json.loads(json.dumps(manifest))
            update(value)
            (local / "manifest.json").write_text(json.dumps(value))
            with self.assertRaises(FaceAssetError):
                parity_asset_sources(local, PROFILE)

    def test_bootstrap_integer_metadata_rejects_decimal_and_exponent_json_numbers(self):
        raw = (ROOT / "config/fixtures/face-gallery-v1.json").read_bytes()
        replacements = (
            (b'"dimension": 512', b'"dimension": 512.0'),
            (b'"dimension": 512', b'"dimension": 5.12e2'),
            (b'"size": 640', b'"size": 640.0'),
            (b'"size": 640', b'"size": 6.4e2'),
            (b'"size": 232589', b'"size": 232589.0'),
            (b'"size": 232589', b'"size": 2.32589e5'),
        )
        for original, replacement in replacements:
            with self.subTest(replacement=replacement):
                self.assertIn(original, raw)
                with self.assertRaises(FaceAssetError):
                    validate_bootstrap(raw.replace(original, replacement, 1))
        for collection in ("models", "notices"):
            value = json.loads(raw)
            for index in range(len(value["pipeline"][collection])):
                malformed = json.loads(raw)
                item = malformed["pipeline"][collection][index]
                item["size"] = float(item["size"])
                with self.subTest(collection=collection, index=index):
                    with self.assertRaises(FaceAssetError):
                        validate_bootstrap(json.dumps(malformed).encode())

    def test_parity_dimension_rejects_decimal_and_exponent_json_numbers(self):
        fixture = ROOT / "build/fixtures/m3-05"
        local = self.root / "parity-profile"
        shutil.copytree(fixture, local)
        raw = json.dumps(json.loads((fixture / "manifest.json").read_bytes())).encode()
        for number in (b"512.0", b"5.12e2"):
            with self.subTest(number=number):
                self.assertIn(b'"dimension": 512', raw)
                (local / "manifest.json").write_bytes(
                    raw.replace(b'"dimension": 512', b'"dimension": ' + number, 1)
                )
                with self.assertRaises(FaceAssetError):
                    parity_asset_sources(local, PROFILE)

    def test_approved_metadata_requires_exact_keys_lists_and_primitive_types(self):
        raw = (ROOT / "config/fixtures/face-gallery-v1.json").read_bytes()
        for update in (
            lambda x: x["profile"].update(dimension=True),
            lambda x: x["profile"].pop("dimension"),
            lambda x: x["pipeline"]["detector"].update(extra=1),
            lambda x: x["pipeline"]["detector"].update(score=True),
            lambda x: x["pipeline"].update(models={}),
            lambda x: x["pipeline"]["models"].pop(),
            lambda x: x["pipeline"]["models"][0].update(size=True),
            lambda x: x["pipeline"]["notices"][0].pop("url"),
        ):
            malformed = json.loads(raw)
            update(malformed)
            with self.subTest(malformed=update):
                with self.assertRaises(FaceAssetError):
                    validate_bootstrap(json.dumps(malformed).encode())

    def test_numeric_prototypes_and_parity_tolerances_keep_accepting_integers(self):
        value = json.loads((ROOT / "config/fixtures/face-gallery-v1.json").read_bytes())
        value["identities"][0]["prototype"] = [1] + [0.0] * 511
        raw = json.dumps(value).encode()
        self.assertEqual(validate_bootstrap(raw), raw)
        fixture = ROOT / "build/fixtures/m3-05"
        local = self.root / "parity-numeric"
        shutil.copytree(fixture, local)
        manifest = json.loads((local / "manifest.json").read_bytes())
        for key in ("bounds_abs_px", "landmarks_abs_px", "aligned_rgb_mae"):
            manifest["tolerances"][key] = 1
        (local / "manifest.json").write_text(json.dumps(manifest))
        self.assertEqual(len(parity_asset_sources(local, PROFILE)), 7)

    def test_closed_seed_header_and_structural_parser_errors_are_rejected(self):
        raw = (ROOT / "config/fixtures/face-gallery-v1.json").read_bytes()
        for update in (
            lambda x: x.update(exported_utc="0000-01-01T00:00:00Z"),
            lambda x: x.update(exported_utc="2026-02-30T00:00:00Z"),
            lambda x: x.update(exported_utc="2026-01-01"),
            lambda x: x.update(policy=[]),
            lambda x: x.update(identities=[True]),
            lambda x: x.update(qualification=[]),
            lambda x: x["identities"][0].update(name="\ud800"),
        ):
            value = json.loads(raw)
            update(value)
            with self.assertRaises(FaceAssetError):
                validate_bootstrap(json.dumps(value).encode())
        for content in (b"\xff", b"[]", b"", b'{"x":' + b"[" * 13 + b"0" + b"]" * 13 + b"}"):
            with self.assertRaises(FaceAssetError):
                validate_bootstrap(content)

    def test_parity_cases_validate_geometry_norms_types_and_unique_ids(self):
        fixture = ROOT / "build/fixtures/m3-05"
        original = json.loads((fixture / "manifest.json").read_text())
        mutations = [
            lambda x: x.update(schema=True),
            lambda x: x.update(cases=[]),
            lambda x: x.update(cases=[True]),
            lambda x: x["cases"].append(x["cases"][0]),
            lambda x: x["cases"][0].update(expected_kind="not-a-kind"),
            lambda x: x["cases"][0].update(width=True),
            lambda x: x["cases"][0].update(width=100),
            lambda x: x["cases"][0].update(bounds=[0, 0, -1, 5]),
            lambda x: x["cases"][0].update(landmarks=[-1] * 10),
            lambda x: x["cases"][0].update(embedding=[0] * 512),
            lambda x: x["cases"][0].update(embedding=[True] * 512),
            lambda x: x["cases"][0].update(image=True),
            lambda x: x.update(cases=[x["cases"][0]]),
        ]
        local = self.root / "parity-invalid"
        for mutate in mutations:
            shutil.copytree(fixture, local, dirs_exist_ok=True)
            value = json.loads(json.dumps(original))
            mutate(value)
            (local / "manifest.json").write_text(json.dumps(value))
            with self.assertRaises(FaceAssetError):
                parity_asset_sources(local, PROFILE)
        self.assertEqual(len(parity_asset_sources(fixture, PROFILE)), 7)

    def test_owned_output_never_consumes_itself_or_deletes_unverified_folders(self):
        inside = self.output / "source"
        inside.write_bytes(b"private content")
        source = FaceAssetSource(
            inside,
            "faces/source",
            inside.stat().st_size,
            hashlib.sha256(inside.read_bytes()).hexdigest(),
        )
        with self.assertRaises(FaceAssetError):
            prepare_face_assets(self.root, self.output, [source])
        with self.assertRaises(FaceAssetError):
            prepare_face_assets(self.root, self.output, [])
        for override in ({"size": 0}, {"size": True}, {"sha256": "x" * 64}):
            with self.assertRaises(FaceAssetError):
                prepare_face_assets(
                    self.root, self.output, [self.source("faces/model", **override)]
                )
        with patch.object(assets.shutil, "rmtree") as remove:
            with self.assertRaises(ValueError):
                assets._remove_generated(self.root, self.root)
            remove.assert_not_called()
        self.assertEqual(inside.read_bytes(), b"private content")

    def test_source_catalog_is_public_by_default_and_seed_requires_an_explicit_path(self):
        model_dir = self.root / "models"
        parity = ROOT / "build/fixtures/m3-05"
        standard = assets.asset_sources(load_configuration(), model_dir, parity, None)
        self.assertEqual(len(standard), 14)
        self.assertFalse(any(source.relative.endswith("bootstrap.json") for source in standard))
        with_seed = assets.asset_sources(
            load_configuration(), model_dir, parity, ROOT / "config/fixtures/face-gallery-v1.json"
        )
        self.assertEqual(len(with_seed), 15)
        self.assertEqual(with_seed[-1].relative, "faces/bootstrap.json")

    def test_multiple_references_validate_each_face_before_any_publication(self):
        fixture = ROOT / "build/fixtures/m3-05"
        sources = parity_asset_sources(fixture, PROFILE)
        self.assertEqual(
            [source.relative for source in sources if "/expected/multiple-" in source.relative],
            [
                "faces/parity/expected/multiple-0-aligned.png",
                "faces/parity/expected/multiple-1-aligned.png",
            ],
        )
        original = json.loads((fixture / "manifest.json").read_bytes())
        mutations = [
            lambda x: x.update(schema=1),
            lambda x: x.update(schema=2.0),
            lambda x: x["cases"][2].pop("faces"),
            lambda x: x["cases"][2].update(faces=[]),
            lambda x: x["cases"][2].update(faces=[True, True]),
            lambda x: x["cases"][2].update(faces=x["cases"][2]["faces"][:1]),
            lambda x: x["cases"][2].update(faces=x["cases"][2]["faces"] * 11),
            lambda x: x["cases"][2]["faces"][1].update(bounds=[0, 0, -1, 2]),
            lambda x: x["cases"][2]["faces"][1].update(landmarks=[True] * 10),
            lambda x: x["cases"][2]["faces"][1].update(embedding=[0] * 512),
            lambda x: x["cases"][2]["faces"][1].update(aligned="../private.png"),
            lambda x: x["cases"][2]["faces"][1].update(extra=True),
        ]
        local = self.root / "multiple-invalid"
        for mutate in mutations:
            shutil.copytree(fixture, local, dirs_exist_ok=True)
            value = json.loads(json.dumps(original))
            mutate(value)
            (local / "manifest.json").write_text(json.dumps(value))
            with self.subTest(mutation=mutate):
                with self.assertRaises(FaceAssetError):
                    parity_asset_sources(local, PROFILE)

    def test_twenty_real_public_face_expectations_fit_the_parity_contract_but_twenty_one_do_not(
        self,
    ):
        fixture = ROOT / "build/fixtures/m3-05"
        local = self.root / "twenty-faces"
        shutil.copytree(fixture, local)
        original = json.loads((fixture / "manifest.json").read_bytes())
        public_faces = original["cases"][2]["faces"]
        faces = []
        for index in range(21):
            face = json.loads(json.dumps(public_faces[index % 2]))
            content = (fixture / face["aligned"]).read_bytes()
            face["aligned"] = f"expected/boundary-{index}.png"
            (local / face["aligned"]).write_bytes(content)
            faces.append(face)
        for count in (20, 21):
            original["cases"][2]["faces"] = faces[:count]
            content = json.dumps(original).encode()
            self.assertGreater(len(content), 120 * 1024)
            self.assertLess(len(content), 1024 * 1024)
            (local / "manifest.json").write_bytes(content)
            if count == 20:
                self.assertEqual(len(parity_asset_sources(local, PROFILE)), 25)
            else:
                with self.assertRaises(FaceAssetError):
                    parity_asset_sources(local, PROFILE)

    def test_parity_manifest_byte_limit_is_separate_from_the_private_gallery_limit(self):
        fixture = ROOT / "build/fixtures/m3-05"
        local = self.root / "bounded-manifest"
        shutil.copytree(fixture, local)
        raw = (local / "manifest.json").read_bytes()
        at_limit = raw + b" " * (1024 * 1024 - len(raw))
        (local / "manifest.json").write_bytes(at_limit)
        self.assertEqual(len(parity_asset_sources(local, PROFILE)), 7)
        (local / "manifest.json").write_bytes(at_limit + b" ")
        with patch.object(assets, "_parity_image") as images:
            with self.assertRaises(FaceAssetError):
                parity_asset_sources(local, PROFILE)
            images.assert_not_called()
        gallery = (ROOT / "config/fixtures/face-gallery-v1.json").read_bytes()
        at_gallery_limit = gallery + b" " * (120 * 1024 - len(gallery))
        self.assertEqual(validate_bootstrap(at_gallery_limit), at_gallery_limit)
        with self.assertRaises(FaceAssetError):
            validate_bootstrap(at_gallery_limit + b" ")

    def test_cli_commits_only_validated_sources_and_errors_do_not_print_private_details(self):
        source = self.source("faces/model")
        arguments = [
            "face_mobile_assets",
            "--repository",
            str(self.root),
            "--output",
            str(self.output),
            "--models",
            "public-models",
            "--parity",
            "public-parity",
            "--gallery",
            "private-person-name.json",
        ]
        output = io.StringIO()
        with (
            patch("sys.argv", arguments),
            patch.object(assets, "asset_sources", return_value=[source]) as catalog,
            redirect_stdout(output),
        ):
            assets.main()
        self.assertTrue((self.output / source.relative).is_file())
        self.assertEqual(catalog.call_args.args[-1], Path("private-person-name.json"))
        self.assertNotIn("private-person-name", output.getvalue())
        with (
            patch("sys.argv", arguments),
            patch.object(assets, "asset_sources", side_effect=FaceAssetError("private secret")),
        ):
            with self.assertRaises(SystemExit) as failure:
                assets.main()
        self.assertNotIn("private secret", str(failure.exception))
