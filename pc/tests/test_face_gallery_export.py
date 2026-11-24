"""Paquet mobile issu des seuls prototypes retenus et d'un test final courant."""

import hashlib
import json
import math
import tempfile
import unittest
from dataclasses import replace
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

from src.blamera_pc.evaluation_data import Policy
from src.blamera_pc.evaluation_final import freeze_final_test, run_final_test
from src.blamera_pc.evaluation_service import run_evaluation
from src.blamera_pc.evaluation_store import EvaluationStore
from src.blamera_pc.face_data import FaceProfile, FaceSample
from src.blamera_pc.face_gallery_export import build_export
from src.blamera_pc.face_models import load_configuration
from src.blamera_pc.face_store import FaceReferences
from src.blamera_pc.gallery import GalleryError
from src.blamera_pc.gallery_store import GalleryStore
from src.blamera_pc.image_import import normalize_image
from test_faces import CANDIDATE, SAMPLE, save_detections, save_reference
from test_gallery import image_bytes

NOW = datetime(2026, 11, 1, 12, 34, 56, tzinfo=timezone.utc)
FIXTURE = Path(__file__).resolve().parents[2] / "config/fixtures/face-gallery-v1.json"


def unit(index):
    return tuple(1.0 if i == index else 0.0 for i in range(512))


class FaceGalleryExportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.configuration = load_configuration()
        self.profile = self.configuration.profile
        self.gallery = GalleryStore(self.root)
        self.store = EvaluationStore(self.root)
        self.person = self.gallery.create("Personne synthétique A")
        self.other = self.gallery.create("Personne synthétique B")
        self.refs = []
        for owner, color, vector in (
            (self.person, "red", unit(0)),
            (self.person, "green", unit(1)),
            (self.other, "yellow", unit(2)),
        ):
            self.gallery.add_photo(owner, normalize_image(image_bytes(color)))
            photo = self.gallery.photos(owner)[-1]
            self.confirm(self.gallery, photo, vector)
            self.refs.append(photo)
        self.store.assign_sessions(self.refs, "SYNTHETIC référence privée")
        self.store.set_reference_selection([self.refs[0].id, self.refs[2].id])
        self.store.set_policy(Policy(0.3, 0.5, 0.05))
        for color, split in (("blue", "calibration"), ("pink", "test")):
            self.store.add_case(
                normalize_image(image_bytes(color)),
                self.person,
                split,
                "SYNTHETIC séance " + split,
                set(),
                set(),
            )
            self.confirm(self.store, self.store.cases()[-1].photo, unit(0))

    def confirm(self, store, photo, vector):
        faces = FaceReferences(store)
        save_detections(faces, photo, self.profile, (CANDIDATE,))
        save_reference(faces, photo, self.profile, 0, FaceSample(SAMPLE.crop, vector))

    def qualify(self):
        run_evaluation(self.gallery, self.store, self.profile)
        freeze_final_test(self.gallery, self.store, self.profile)
        return run_final_test(self.gallery, self.store, self.profile)

    def export(self, **kwargs):
        return build_export(self.gallery, self.store, self.configuration, exported_at=NOW, **kwargs)

    def test_export_requires_current_final_report_and_frozen_context(self):
        with self.assertRaises(GalleryError):
            self.export()
        run_evaluation(self.gallery, self.store, self.profile)
        freeze_final_test(self.gallery, self.store, self.profile)
        with self.assertRaises(GalleryError):
            self.export()
        self.qualify()
        self.store.set_policy(Policy(0.3, 0.55, 0.05))
        with self.assertRaises(GalleryError):
            self.export()

    def test_selected_prototypes_policy_metadata_and_aggregate_only_are_exported(self):
        report = self.qualify()
        before_main, before_eval = self.gallery.path.read_bytes(), self.store.path.read_bytes()
        with patch(
            "src.blamera_pc.face_models.provision", side_effect=AssertionError("network forbidden")
        ):
            payload = self.export()
        self.assertGreater(len(payload), 0)
        data = json.loads(payload)
        self.assertEqual(data["format"], "blamera-face-gallery")
        self.assertEqual(data["schema"], 1)
        self.assertEqual(data["exported_utc"], "2026-11-01T12:34:56Z")
        self.assertEqual(data["profile"], {"key": self.profile.key, "dimension": 512})
        self.assertEqual(data["policy"], {"reject": 0.3, "accept": 0.5, "margin": 0.05})
        identities = {entry["id"]: entry for entry in data["identities"]}
        self.assertEqual(identities[self.person]["prototype"], list(unit(0)))
        self.assertEqual(identities[self.person]["reference_count"], 1)
        self.assertEqual(identities[self.other]["prototype"], list(unit(2)))
        self.assertEqual(data["qualification"]["counts"], report["counts"])
        self.assertEqual(data["qualification"]["frozen_context"], report["freeze"]["fingerprint"])
        canonical = json.dumps(
            report, sort_keys=True, separators=(",", ":"), allow_nan=False
        ).encode()
        self.assertEqual(
            data["qualification"]["test_report"], hashlib.sha256(canonical).hexdigest()
        )
        fixture = json.loads(FIXTURE.read_text(encoding="utf-8"))
        self.assertEqual(data["pipeline"], fixture["pipeline"])
        self.assertEqual(set(data), set(fixture))
        text = payload.decode("utf-8")
        for private in (
            "SYNTHETIC séance",
            "SYNTHETIC référence",
            "cases",
            "source",
            "crop",
            "session",
            "vector",
            "photos",
        ):
            self.assertNotIn('"' + private, text)
        self.assertNotIn("SYNTHETIC", text)
        self.assertEqual(self.gallery.path.read_bytes(), before_main)
        self.assertEqual(self.store.path.read_bytes(), before_eval)
        self.assertEqual(self.export(), payload)

    def test_mean_of_selected_unit_vectors_is_renormalized(self):
        self.store.set_reference_selection([photo.id for photo in self.refs])
        self.qualify()
        data = json.loads(self.export())
        entry = next(item for item in data["identities"] if item["id"] == self.person)
        self.assertEqual(entry["reference_count"], 2)
        self.assertAlmostEqual(entry["prototype"][0], math.sqrt(0.5))
        self.assertAlmostEqual(entry["prototype"][1], math.sqrt(0.5))
        self.assertAlmostEqual(math.hypot(*entry["prototype"]), 1.0)

    def test_reference_selection_name_preparation_or_profile_changes_refuse_old_qualification(self):
        self.qualify()
        self.gallery.rename(self.person, "Nouveau nom synthétique")
        with self.assertRaises(GalleryError):
            self.export()
        self.gallery.rename(self.person, "Personne synthétique A")
        self.store.set_reference_selection([photo.id for photo in self.refs])
        with self.assertRaises(GalleryError):
            self.export()
        self.store.set_reference_selection([self.refs[0].id, self.refs[2].id])
        wrong = replace(self.configuration, profile=FaceProfile("other", 512))
        with self.assertRaises(GalleryError):
            build_export(self.gallery, self.store, wrong, exported_at=NOW)

    def test_unknown_pipeline_and_naive_export_clock_are_refused(self):
        self.qualify()
        wrong_file = replace(self.configuration.files[0], sha256="f" * 64)
        config = replace(self.configuration, files=(wrong_file, *self.configuration.files[1:]))
        with self.assertRaises(GalleryError):
            build_export(self.gallery, self.store, config, exported_at=NOW)
        with self.assertRaises(GalleryError):
            build_export(
                self.gallery, self.store, self.configuration, exported_at=NOW.replace(tzinfo=None)
            )
