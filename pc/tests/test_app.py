import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from streamlit.testing.v1 import AppTest

APP = Path(__file__).resolve().parents[1] / "src/blamera_pc/app.py"


class StartupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.addCleanup(patch.stopall)
        patch.dict(os.environ, {"BLAMERA_DATA_DIR": self.temp.name}).start()
        patch.object(sys, "path", [str(APP.parents[1]), *sys.path]).start()

    def test_startup_opens_local_people_gallery(self):
        app = AppTest.from_file(str(APP)).run()
        self.assertFalse(app.exception)
        self.assertIn("Ajoutez une personne", app.info[0].value)
        self.assertTrue(app.radio, "Le choix du parcours doit être disponible")
        self.assertEqual(app.radio[0].value, "Personnes")
        self.assertIn("Galerie", app.caption[0].value)

    def test_people_selection_can_create_a_person_without_loading_photos(self):
        app = AppTest.from_file(str(APP)).run()
        self.assertTrue(app.radio, "Le choix du parcours doit être disponible")
        app.radio[0].set_value("Personnes").run()
        self.assertFalse(app.exception)
        self.assertEqual(app.text_input[0].label, "Nouvelle personne")
        self.assertFalse(app.get("file_uploader"))

    def test_cat_selection_replaces_people_and_explains_m4(self):
        app = AppTest.from_file(str(APP)).run()
        self.assertTrue(app.radio, "Le choix du parcours doit être disponible")
        app.radio[0].set_value("Personnes").run()
        app.radio[0].set_value("Chats").run()
        self.assertFalse(app.exception)
        self.assertIn("M4", app.caption[0].value)
        self.assertNotIn("M3", app.caption[0].value)
