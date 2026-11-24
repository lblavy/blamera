"""Actions de galerie réelles via AppTest, stockage temporaire uniquement."""

import os
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from src.blamera_pc.gallery_store import GalleryStore
from src.blamera_pc.image_import import normalize_image
from streamlit.testing.v1 import AppTest
from test_gallery import image_bytes

APP = Path(__file__).resolve().parents[1] / "src/blamera_pc/app.py"
SOURCE = APP.parents[1]


class PeopleUiTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.addCleanup(patch.stopall)
        patch.dict(os.environ, {"BLAMERA_DATA_DIR": str(self.directory)}).start()
        patch.object(sys, "path", [str(SOURCE), *sys.path]).start()

    def app(self):
        app = AppTest.from_file(str(APP)).run()
        app.radio[0].set_value("Personnes").run()
        self.assertFalse(app.exception)
        return app

    @staticmethod
    def button(app, label):
        return next(button for button in app.button if button.label == label)

    def test_create_rename_persist_and_delete_with_confirmation(self):
        app = self.app()
        app.text_input(key="new_person_name").set_value("Alice")
        self.button(app, "Ajouter").click().run()
        self.assertFalse(app.exception)
        store = GalleryStore(self.directory)
        person = store.people()[0]
        app.text_input(key=f"rename_{person.id}").set_value("Alicia")
        self.button(app, "Renommer").click().run()
        self.assertEqual(store.people()[0].name, "Alicia")
        app = self.app()
        delete = self.button(app, "Supprimer la personne")
        self.assertTrue(delete.disabled)
        app.checkbox(key=f"delete_person_{person.id}").check().run()
        self.button(app, "Supprimer la personne").click().run()
        self.assertEqual(store.people(), [])
        self.assertFalse(app.exception)

    def test_invalid_name_shows_error_without_creating_entry(self):
        app = self.app()
        self.button(app, "Ajouter").click().run()
        self.assertTrue(app.error)
        self.assertEqual(GalleryStore(self.directory).people(), [])

    def test_photo_move_and_delete_require_explicit_actions(self):
        store = GalleryStore(self.directory)
        alice = store.create("Alice")
        bob = store.create("Bob")
        store.add_photo(alice, normalize_image(image_bytes()))
        photo = store.photos(alice)[0]
        app = self.app()
        self.assertIn("12 × 8", [caption.value for caption in app.caption])
        self.assertTrue(self.button(app, "Retirer la photo").disabled)
        self.button(app, "Déplacer").click().run()
        self.assertEqual(store.photos(alice), [])
        self.assertEqual(len(store.photos(bob)), 1)
        app.selectbox(key="person_id").set_value(bob).run()
        app.checkbox(key=f"delete_photo_{photo.id}").check().run()
        self.button(app, "Retirer la photo").click().run()
        self.assertEqual(store.photos(bob), [])
        self.assertFalse(app.exception)

    def test_rerun_never_imports_without_button_and_upload_key_follows_person(self):
        store = GalleryStore(self.directory)
        alice = store.create("Alice")
        bob = store.create("Bob")
        app = self.app()
        first_key = app.get("file_uploader")[0].key
        app.run()
        self.assertEqual(store.photos(alice), [])
        app.selectbox(key="person_id").set_value(bob).run()
        self.assertNotEqual(first_key, app.get("file_uploader")[0].key)
        self.assertEqual(store.photos(bob), [])

    def test_corrupt_storage_displays_safe_error_and_preserves_file(self):
        database = self.directory / "gallery.sqlite3"
        database.write_bytes(b"SYNTHETIC invalid database")
        app = self.app()
        self.assertTrue(app.error)
        self.assertNotIn(str(self.directory), app.error[0].value)
        self.assertEqual(database.read_bytes(), b"SYNTHETIC invalid database")

    def test_import_boundary_runs_only_on_click_then_clears_upload_generation(self):
        store = GalleryStore(self.directory)
        identifier = store.create("Alice")
        upload = SimpleNamespace(name="synthetic.png", getvalue=lambda: image_bytes())
        with patch("blamera_pc.people_ui.st.file_uploader", return_value=[upload]):
            app = self.app()
            self.assertEqual(store.photos(identifier), [])
            self.button(app, "Importer les photos").click().run()
            self.assertEqual(len(store.photos(identifier)), 1)
            self.assertEqual(app.session_state[f"upload_generation_{identifier}"], 1)
            self.assertIn("synthetic.png — Ajoutée", [row.value for row in app.markdown])
            app.run()
            self.assertEqual(len(store.photos(identifier)), 1)
            self.assertFalse(app.exception)

    def test_too_many_selected_files_do_not_read_or_import_any_content(self):
        store = GalleryStore(self.directory)
        identifier = store.create("Alice")
        upload = SimpleNamespace(name="synthetic.png", getvalue=lambda: self.fail("No file read"))
        with patch("blamera_pc.people_ui.st.file_uploader", return_value=[upload] * 21):
            app = self.app()
            self.button(app, "Importer les photos").click().run()
            self.assertTrue(app.error)
            self.assertEqual(store.photos(identifier), [])

    def test_move_conflict_is_displayed_without_removing_the_photo(self):
        store = GalleryStore(self.directory)
        alice = store.create("Alice")
        bob = store.create("Bob")
        prepared = normalize_image(image_bytes())
        store.add_photo(alice, prepared)
        store.add_photo(bob, prepared)
        app = self.app()
        self.button(app, "Déplacer").click().run()
        self.assertTrue(app.error)
        self.assertEqual(len(store.photos(alice)), 1)
        self.assertEqual(len(store.photos(bob)), 1)
