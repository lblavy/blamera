"""Décodage borné et normalisation sans métadonnées des imports explicites."""

import hashlib
import warnings
from io import BytesIO

from PIL import Image, ImageOps, UnidentifiedImageError

from .gallery import GalleryError, NormalizedImage

MAX_FILE_BYTES = 20 * 1024 * 1024
MAX_PIXELS = 24_000_000
MAX_BATCH = 20


def normalize_image(content: bytes) -> NormalizedImage:
    if not content or len(content) > MAX_FILE_BYTES:
        raise GalleryError("Fichier vide ou supérieur à 20 Mio.")
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            return _decode(content)
    except (
        UnidentifiedImageError,
        OSError,
        ValueError,
        Image.DecompressionBombError,
        Image.DecompressionBombWarning,
        MemoryError,
    ):
        raise GalleryError(
            "Image illisible, trop volumineuse ou format non pris en charge."
        ) from None


def _decode(content: bytes) -> NormalizedImage:
    with Image.open(BytesIO(content), formats=["JPEG", "PNG", "WEBP"]) as source:
        if source.width * source.height > MAX_PIXELS:
            raise GalleryError("Image supérieure à 24 millions de pixels.")
        if getattr(source, "n_frames", 1) != 1:
            raise GalleryError("Les images animées ne sont pas acceptées.")
        with ImageOps.exif_transpose(source) as oriented, oriented.convert("RGB") as rgb:
            # Recreate only pixels: source.info may retain EXIF, ICC or textual metadata.
            with Image.frombytes("RGB", rgb.size, rgb.tobytes()) as clean:
                output = BytesIO()
                clean.save(output, "PNG")
                data = output.getvalue()
                return NormalizedImage(
                    data, hashlib.sha256(data).hexdigest(), clean.width, clean.height
                )
