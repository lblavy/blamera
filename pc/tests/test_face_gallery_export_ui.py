"""Téléchargement explicite à partir d'une qualification courante seulement."""

import json
import unittest
from unittest.mock import patch

import test_evaluation_ui as ui
import test_face_gallery_export as export
from src.blamera_pc.evaluation_data import Policy


class FaceGalleryExportUiTests(unittest.TestCase):
    app = ui.EvaluationUiTests.app
    button = staticmethod(ui.EvaluationUiTests.button)
    evaluation_app = ui.EvaluationUiTests.evaluation_app

    def setUp(self):
        ui.EvaluationUiTests.setUp(self)
        export.FaceGalleryExportTests.setUp(self)
        patch.dict("os.environ", {"BLAMERA_DATA_DIR": str(self.root)}).start()

    confirm = export.FaceGalleryExportTests.confirm
    qualify = export.FaceGalleryExportTests.qualify

    def test_download_exists_only_after_current_test_and_disappears_on_changed_policy(self):
        with patch("streamlit.download_button") as download:
            self.evaluation_app()
            download.assert_not_called()
            self.qualify()
            before = self.gallery.path.read_bytes()
            app = self.evaluation_app()
            self.assertFalse(app.exception)
            self.assertTrue(download.called)
            args = download.call_args.kwargs
            self.assertEqual(args["label"], "Exporter la galerie")
            self.assertEqual(args["mime"], "application/json")
            self.assertEqual(args["file_name"], "blamera-face-gallery-v1.json")
            self.assertEqual(json.loads(args["data"])["profile"]["dimension"], 512)
            self.assertEqual(self.gallery.path.read_bytes(), before)
            download.reset_mock()
            self.store.set_policy(Policy(0.3, 0.55, 0.05))
            app.run()
            download.assert_not_called()
