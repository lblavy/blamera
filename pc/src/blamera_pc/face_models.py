"""Poids publics figés ; provisionnement uniquement par commande explicite."""

import argparse
import hashlib
import json
import re
import tempfile
import urllib.request
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol, cast

from .face_data import FaceProfile
from .gallery import GalleryError

REPOSITORY = Path(__file__).resolve().parents[3]
MODEL_DIRECTORY = REPOSITORY / "models/m3-02"
MANIFEST = REPOSITORY / "config/face-models.json"


class Readable(Protocol):
    def read(self, size: int = -1) -> bytes: ...


class Writable(Protocol):
    def write(self, data: bytes) -> int: ...


@dataclass(frozen=True)
class ModelArchive:
    name: str
    size: int
    sha256: str
    member: str


@dataclass(frozen=True)
class ModelFile:
    role: str
    name: str
    size: int
    sha256: str
    url: str
    archive: ModelArchive | None = None


@dataclass(frozen=True)
class FaceConfiguration:
    profile: FaceProfile
    files: tuple[ModelFile, ...]
    detector_size: int
    score: float
    nms: float


def load_configuration(path: Path = MANIFEST) -> FaceConfiguration:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        raise GalleryError("Configuration des visages indisponible.") from None
    if not isinstance(raw, dict) or raw.get("schema") != 1 or raw.get("dimension") != 512:
        raise GalleryError("Profil de visages incompatible.")
    data = cast(dict[str, object], raw)
    if (
        data.get("profile") != "opencv-yunet2023mar-arcface-r50-webface600k-v1"
        or data.get("preprocessing") != "opencv-alignCrop-5points-RGB112-minus127.5-div127.5-L2-v1"
        or data.get("runtime") != "opencv-dnn-4.13.0.92-cpu"
    ):
        raise GalleryError("Prétraitement de visage incompatible.")
    files = data.get("files")
    if not isinstance(files, list):
        raise GalleryError("Liste des modèles invalide.")
    parsed = tuple(_model_file(item) for item in files)
    if len(parsed) != 7 or {file.role for file in parsed} != {
        "detector",
        "aligner",
        "encoder",
        "detector_license",
        "aligner_license",
        "encoder_terms",
        "encoder_code_license",
    }:
        raise GalleryError("Poids ou notices de modèles manquants.")
    signature = hashlib.sha256(
        json.dumps(data, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()
    size = data.get("detector_size")
    if not isinstance(size, int) or isinstance(size, bool) or not 128 <= size <= 1280:
        raise GalleryError("Résolution du détecteur invalide.")
    return FaceConfiguration(
        FaceProfile(signature, 512), parsed, size, _ratio(data, "score"), _ratio(data, "nms")
    )


def _ratio(data: dict[str, object], key: str) -> float:
    value = data.get(key)
    if isinstance(value, bool) or not isinstance(value, (float, int)) or not 0 < value <= 1:
        raise GalleryError("Paramètre de détection invalide.")
    return float(value)


def _model_file(raw: object) -> ModelFile:
    if not isinstance(raw, dict):
        raise GalleryError("Description de modèle invalide.")
    values = [raw.get(key) for key in ("role", "name", "sha256", "url")]
    if any(not isinstance(value, str) for value in values):
        raise GalleryError("Description de modèle incomplète.")
    role, name, digest, url = cast(list[str], values)
    _safe_name(name)
    size = raw.get("size")
    if (
        not isinstance(size, int)
        or isinstance(size, bool)
        or not 0 < size <= 256 * 1024 * 1024
        or re.fullmatch("[0-9a-f]{64}", digest) is None
    ):
        raise GalleryError("Empreinte de modèle invalide.")
    commit = "48b10fa505aeb51fbd127fc60a5c660040e823b7"
    prefixes: tuple[str, ...] = (
        f"https://media.githubusercontent.com/media/opencv/opencv_zoo/{commit}/models/",
        f"https://raw.githubusercontent.com/opencv/opencv_zoo/{commit}/models/",
    )
    insightface = "3e6486942a1be2da0e5b475fac375ea73264bd21"
    prefixes += (f"https://raw.githubusercontent.com/deepinsight/insightface/{insightface}/",)
    archive = _archive(raw.get("archive"))
    release = "https://github.com/deepinsight/insightface/releases/download/v0.7/buffalo_l.zip"
    if not url.startswith(prefixes) and not (role == "encoder" and url == release and archive):
        raise GalleryError("Source de modèle non figée.")
    if archive is not None and (role != "encoder" or url != release):
        raise GalleryError("Archive de modèle incompatible.")
    return ModelFile(role, name, size, digest, url, archive)


def _archive(raw: object) -> ModelArchive | None:
    if raw is None:
        return None
    if not isinstance(raw, dict):
        raise GalleryError("Archive de modèle invalide.")
    name, member, digest = (raw.get(key) for key in ("name", "member", "sha256"))
    if not all(isinstance(value, str) for value in (name, member, digest)):
        raise GalleryError("Description d'archive incomplète.")
    _safe_name(cast(str, name))
    _safe_name(cast(str, member))
    size = raw.get("size")
    if (
        not isinstance(size, int)
        or isinstance(size, bool)
        or not 0 < size <= 512 * 1024 * 1024
        or re.fullmatch("[0-9a-f]{64}", cast(str, digest)) is None
    ):
        raise GalleryError("Empreinte d'archive invalide.")
    return ModelArchive(cast(str, name), size, cast(str, digest), cast(str, member))


def _safe_name(name: str) -> None:
    if re.fullmatch(r"[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+", name) is None:
        raise GalleryError("Nom de fichier de modèle invalide.")


def _valid(file: ModelFile, directory: Path) -> bool:
    _safe_name(file.name)
    path = directory / file.name
    try:
        if path.stat().st_size != file.size:
            return False
        with path.open("rb") as stream:
            return hashlib.file_digest(stream, "sha256").hexdigest() == file.sha256
    except OSError:
        return False


def verify_models(configuration: FaceConfiguration, directory: Path = MODEL_DIRECTORY) -> None:
    if not all(_valid(file, directory) for file in configuration.files):
        raise GalleryError("Modèles absents ou altérés. Exécutez scripts/pc.ps1 Models sur le PC.")


def provision(configuration: FaceConfiguration, directory: Path = MODEL_DIRECTORY) -> None:
    try:
        directory.mkdir(parents=True, exist_ok=True)
        for file in configuration.files:
            if not _valid(file, directory):
                if file.archive is None:
                    _download(file, directory)
                else:
                    _from_archive(file, directory)
    except (OSError, TimeoutError):
        raise GalleryError(
            "Téléchargement des modèles impossible ; fichiers valides conservés."
        ) from None
    verify_models(configuration, directory)


def _from_archive(file: ModelFile, directory: Path) -> None:
    archive = file.archive
    if archive is None:
        raise GalleryError("Description d'archive absente.")
    source = ModelFile(file.role, archive.name, archive.size, archive.sha256, file.url)
    if not _valid(source, directory):
        _download(source, directory)
    temporary: Path | None = None
    try:
        with zipfile.ZipFile(directory / archive.name) as bundle:
            matches = [info for info in bundle.infolist() if info.filename == archive.member]
            if len(matches) != 1 or matches[0].file_size != file.size:
                raise GalleryError("Contenu de l'archive de modèle incompatible.")
            with (
                bundle.open(matches[0]) as content,
                tempfile.NamedTemporaryFile(dir=directory, suffix=".part", delete=False) as target,
            ):
                temporary = Path(target.name)
                _copy_bounded(content, target, file.size)
        _activate(file, temporary, directory)
    except (zipfile.BadZipFile, RuntimeError):
        raise GalleryError("Archive de modèle illisible ; ancien fichier conservé.") from None
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def _download(file: ModelFile, directory: Path) -> None:
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(dir=directory, suffix=".part", delete=False) as target:
            temporary = Path(target.name)
            with urllib.request.urlopen(file.url, timeout=60) as response:
                _copy_bounded(response, target, file.size)
        _activate(file, temporary, directory)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def _copy_bounded(source: Readable, target: Writable, size: int) -> None:
    length = 0
    while chunk := source.read(64 * 1024):
        length += len(chunk)
        if length > size:
            raise GalleryError("Téléchargement de modèle trop volumineux.")
        target.write(chunk)


def _activate(file: ModelFile, temporary: Path, directory: Path) -> None:
    if not _valid(
        ModelFile(file.role, temporary.name, file.size, file.sha256, file.url), directory
    ):
        raise GalleryError("Empreinte du téléchargement incorrecte ; ancien fichier conservé.")
    temporary.replace(directory / file.name)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Vérifier ou préparer les modèles publics de visage"
    )
    parser.add_argument("--download", action="store_true", required=True)
    parser.parse_args()
    try:
        provision(load_configuration())
    except GalleryError as error:
        print(str(error))
        return 1
    print("Modèles et notices de visage vérifiés.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
