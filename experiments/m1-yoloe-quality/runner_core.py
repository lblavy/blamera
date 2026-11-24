"""Explicit offline orchestration, never loads model modules on import."""

import hashlib
from legacy_geometry import prepare


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_files(root, records):
    root = root.resolve()
    for record in records:
        path = (root / record["path"]).resolve()
        if not path.is_relative_to(root):
            raise ValueError("File outside workspace")
        if not path.is_file() or sha256(path) != record["sha256"]:
            raise ValueError("Frozen file changed: " + record["path"])
    return True


def run_frames(records, profiles, session_factory, load_image, evaluate, emit):
    forwards = 0
    for model in dict.fromkeys(profile["model"] for profile in profiles):
        chosen = [profile for profile in profiles if profile["model"] == model]
        with session_factory(model) as session:
            for record in records:
                tensor, projection = prepare(load_image(record))
                raw, proto = session.run(tensor)
                forwards += 1
                for result in evaluate(
                    record, model, session.names, raw, proto, projection, chosen
                ):
                    emit(result)
                # The large PF raw tensor is released before the next forward, never serialized.
                del raw, proto, tensor
    return forwards
