"""Galerie persistante sur fixtures synthétiques isolées."""

import sqlite3
import tempfile
import unittest
from contextlib import closing
from io import BytesIO
from pathlib import Path
from unittest.mock import patch

from PIL import Image
from src.blamera_pc.gallery import GalleryError
from src.blamera_pc.gallery_store import GalleryStore
from src.blamera_pc.image_import import normalize_image
from src.blamera_pc.photo_batch import PhotoUpload, import_batch


def image_bytes(color="red", size=(12, 8), format="PNG", **options):
    output = BytesIO()
    Image.new("RGB", size, color).save(output, format, **options)
    return output.getvalue()


class GalleryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name) / "gallery"
        self.store = GalleryStore(self.directory)

    def test_create_rename_and_reopen_keep_stable_identity(self):
        identifier = self.store.create("  Alice  ")
        self.store.rename(identifier, "Alicia")
        reopened = GalleryStore(self.directory)
        self.assertEqual(
            [(p.id, p.name, p.count) for p in reopened.people()], [(identifier, "Alicia", 0)]
        )

    def test_invalid_or_duplicate_names_preserve_previous_entry(self):
        identifier = self.store.create("Alice")
        for value in ["", "  ", "a" * 81, "bad\nname", " ALICE ", "Ａｌｉｃｅ"]:
            with self.subTest(value=value), self.assertRaises(GalleryError):
                self.store.create(value)
        with self.assertRaises(GalleryError):
            self.store.rename(identifier, "\x00")
        self.assertEqual(self.store.people()[0].name, "Alice")

    def test_duplicate_photo_is_skipped_per_person_and_persists(self):
        alice = self.store.create("Alice")
        bob = self.store.create("Bob")
        photo = normalize_image(image_bytes())
        self.assertTrue(self.store.add_photo(alice, photo))
        self.assertFalse(self.store.add_photo(alice, photo))
        self.assertTrue(self.store.add_photo(bob, photo))
        reopened = GalleryStore(self.directory)
        self.assertEqual([p.count for p in reopened.people()], [1, 1])
        stored = reopened.photos(alice)[0]
        self.assertEqual((stored.image, stored.width, stored.height), (photo.image, 12, 8))

    def test_move_conflict_never_removes_source(self):
        alice = self.store.create("Alice")
        bob = self.store.create("Bob")
        photo = normalize_image(image_bytes())
        self.store.add_photo(alice, photo)
        self.store.add_photo(bob, photo)
        identifier = self.store.photos(alice)[0].id
        with self.assertRaises(GalleryError):
            self.store.move_photo(identifier, bob)
        self.assertEqual(len(self.store.photos(alice)), 1)
        self.store.delete_photo(self.store.photos(bob)[0].id)
        self.store.move_photo(identifier, bob)
        self.assertEqual(self.store.photos(alice), [])
        self.assertEqual(self.store.photos(bob)[0].id, identifier)

    def test_person_deletion_removes_only_its_managed_photos(self):
        alice = self.store.create("Alice")
        bob = self.store.create("Bob")
        self.store.add_photo(alice, normalize_image(image_bytes()))
        self.store.add_photo(bob, normalize_image(image_bytes("blue")))
        self.store.delete_person(alice)
        self.assertEqual([p.id for p in self.store.people()], [bob])
        self.assertEqual(len(self.store.photos(bob)), 1)

    def test_missing_entries_are_reported_and_database_failures_are_safe(self):
        person = self.store.create("Alice")
        with self.assertRaises(GalleryError):
            self.store.move_photo("missing", person)
        for action in [
            lambda: self.store.rename("missing", "Alice"),
            lambda: self.store.delete_person("missing"),
            lambda: self.store.delete_photo("missing"),
            lambda: self.store.move_photo("missing", "missing"),
        ]:
            with self.assertRaises(GalleryError):
                action()
        with patch(
            "src.blamera_pc.gallery_store.sqlite3.connect",
            side_effect=sqlite3.OperationalError("PRIVATE database location"),
        ):
            with self.assertRaises(GalleryError) as failure:
                self.store.people()
        self.assertNotIn("PRIVATE", str(failure.exception))

    def test_unwritable_directory_and_rename_conflict_preserve_entries(self):
        self.store.create("Alice")
        bob = self.store.create("Bob")
        with self.assertRaises(GalleryError):
            self.store.rename(bob, "ALICE")
        self.assertEqual([person.name for person in self.store.people()], ["Alice", "Bob"])
        with patch("pathlib.Path.mkdir", side_effect=OSError("PRIVATE path")):
            with self.assertRaises(GalleryError) as failure:
                GalleryStore(self.directory)
        self.assertNotIn("PRIVATE", str(failure.exception))

    def test_unknown_or_corrupt_database_is_not_replaced(self):
        for content in [b"not a database", None]:
            with tempfile.TemporaryDirectory() as directory:
                database = Path(directory) / "gallery.sqlite3"
                if content is None:
                    with closing(sqlite3.connect(database)) as connection, connection:
                        connection.execute("PRAGMA user_version=99")
                else:
                    database.write_bytes(content)
                before = database.read_bytes()
                with self.assertRaises(GalleryError):
                    GalleryStore(Path(directory))
                self.assertEqual(database.read_bytes(), before)

    def test_initial_schema_failure_rolls_back_and_next_open_recovers(self):
        original_connect = sqlite3.connect

        class FailingSchema(sqlite3.Connection):
            def execute(self, sql, parameters=()):
                if sql.startswith("CREATE TABLE photos"):
                    raise sqlite3.OperationalError("Synthetic schema failure")
                return super().execute(sql, parameters)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch(
                "src.blamera_pc.gallery_store.sqlite3.connect",
                side_effect=lambda *args, **kwargs: original_connect(
                    *args, **kwargs, factory=FailingSchema
                ),
            ):
                with self.assertRaises(GalleryError):
                    GalleryStore(root)
            recovered = GalleryStore(root)
            self.assertEqual(recovered.people(), [])


class ImageTests(unittest.TestCase):
    def test_oriented_jpeg_becomes_rgb_without_metadata(self):
        exif = Image.Exif()
        exif[274] = 6
        exif[270] = "SYNTHETIC PRIVATE EXIF"
        result = normalize_image(image_bytes(format="JPEG", exif=exif))
        self.assertEqual((result.width, result.height), (8, 12))
        with Image.open(BytesIO(result.image)) as image:
            self.assertEqual(image.mode, "RGB")
            self.assertEqual(image.getexif(), {})
            self.assertEqual(image.size, (8, 12))

    def test_png_webp_rgb_and_alpha_are_normalized(self):
        for format in ["PNG", "WEBP"]:
            result = normalize_image(image_bytes(format=format))
            self.assertEqual((result.width, result.height), (12, 8))
        stream = BytesIO()
        Image.new("RGBA", (3, 4), (12, 20, 30, 100)).save(stream, "PNG")
        self.assertEqual(normalize_image(stream.getvalue()).height, 4)

    def test_invalid_animated_and_excessive_images_are_rejected(self):
        for value in [b"", b"not a photo", image_bytes(format="BMP")]:
            with self.assertRaises(GalleryError):
                normalize_image(value)
        stream = BytesIO()
        Image.new("RGB", (2, 2), "red").save(
            stream,
            "PNG",
            save_all=True,
            append_images=[Image.new("RGB", (2, 2), "blue")],
            duration=20,
        )
        with self.assertRaises(GalleryError):
            normalize_image(stream.getvalue())
        with patch("src.blamera_pc.image_import.MAX_FILE_BYTES", 2):
            with self.assertRaises(GalleryError):
                normalize_image(image_bytes())
        with patch("src.blamera_pc.image_import.MAX_PIXELS", 50):
            with self.assertRaises(GalleryError):
                normalize_image(image_bytes())


class BatchTests(unittest.TestCase):
    setUp = GalleryTests.setUp

    def test_mixed_batch_continues_and_duplicate_does_not_count_as_added(self):
        identifier = self.store.create("Alice")
        photo = PhotoUpload("../synthetic.png", image_bytes())
        outcomes = import_batch(
            self.store, identifier, [photo, PhotoUpload("broken.png", b"broken"), photo]
        )
        self.assertEqual([row.added for row in outcomes], [True, False, False])
        self.assertEqual(
            [row.name for row in outcomes], ["synthetic.png", "broken.png", "synthetic.png"]
        )
        self.assertEqual(len(self.store.photos(identifier)), 1)

    def test_batch_limit_rejects_whole_batch_without_writes(self):
        identifier = self.store.create("Alice")
        files = [PhotoUpload("synthetic.png", image_bytes())] * 21
        outcomes = import_batch(self.store, identifier, files)
        self.assertEqual(len(outcomes), 1)
        self.assertFalse(outcomes[0].added)
        self.assertEqual(self.store.photos(identifier), [])
