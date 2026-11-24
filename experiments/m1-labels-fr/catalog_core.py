"""Validate and assemble a position-preserving display catalogue."""

import hashlib
import json


def verify_model_files(directory, files):
    if not files:
        raise ValueError("Empty model manifest")
    for entry in files:
        path = directory / entry["file"]
        if path.parent != directory or not path.is_file():
            raise ValueError("Model file missing or outside its pinned directory")
        if path.stat().st_size != entry["size"]:
            raise ValueError("Model file size mismatch")
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        if digest.hexdigest() != entry["sha256"]:
            raise ValueError("Model file SHA mismatch")
    return len(files)


def checked_source(raw, expected_sha, count):
    if hashlib.sha256(raw).hexdigest() != expected_sha:
        raise ValueError("Canonical English catalogue SHA mismatch")
    return _names(json.loads(raw), count)


def read_checkpoint(raw, identity, source):
    document = json.loads(raw)
    if not isinstance(document, dict) or set(document) != set(identity) | {"translations"}:
        raise ValueError("Unexpected checkpoint structure")
    if any(document[key] != value for key, value in identity.items()):
        raise ValueError("Checkpoint belongs to another generation")
    translations = document["translations"]
    if not isinstance(translations, list) or len(translations) > len(source):
        raise ValueError("Checkpoint is not a source prefix")
    return _names(translations, len(translations))


def make_catalogue(source, translations, overrides, english_sha):
    translated = _names(translations, len(source))
    if not isinstance(overrides, dict) or not set(overrides) <= set(source):
        raise ValueError("Override names must belong to the pinned source")
    labels = [overrides.get(english, french) for english, french in zip(source, translated)]
    return {
        "schema": 1,
        "locale": "fr",
        "englishSha256": english_sha,
        "labels": _names(labels, len(source)),
    }


def validate_retained(source, labels, retained):
    _names(labels, len(source))
    expected = {
        index
        for index, (en, fr) in enumerate(zip(source, labels))
        if en.casefold() == fr.casefold()
    }
    if not isinstance(retained, list):
        raise ValueError("Retained names must be a list")
    seen = set()
    for row in retained:
        if not isinstance(row, dict) or set(row) != {"id", "english", "french", "reason"}:
            raise ValueError("Malformed retained-name explanation")
        index = row["id"]
        if type(index) is not int or index not in expected or index in seen:
            raise ValueError("Retained names must cover exact unique unchanged IDs")
        if row["english"] != source[index] or row["french"] != labels[index]:
            raise ValueError("Retained-name mapping changed")
        _names([row["reason"]], 1)
        seen.add(index)
    if seen != expected:
        raise ValueError("Unexplained unchanged names")
    return len(seen)


def _names(values, count):
    if not isinstance(values, list) or len(values) != count:
        raise ValueError("Unexpected number of labels")
    for value in values:
        if not isinstance(value, str) or not value.strip() or value != value.strip():
            raise ValueError("Each label must be a nonempty trimmed string")
        if len(value) > 160 or "\ufffd" in value or any(not char.isprintable() for char in value):
            raise ValueError("Invalid characters or oversized label")
        if value in {"TODO", "[MISSING]", "[[MISSING]]"}:
            raise ValueError("Placeholder is not a translated category")
    return list(values)
