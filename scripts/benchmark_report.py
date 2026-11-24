"""Validate manually re-emitted app reports; no device or private-file access."""

import base64
import binascii
import hashlib
import json
import math
import re

MAX_REPORT_BYTES = 8 * 1024 * 1024
CHUNK_BYTES = 1024


class ReportError(ValueError):
    pass


def fields(text, expected):
    try:
        pieces = [item.split("=", 1) for item in text.split()]
        values = dict(pieces)
    except (ValueError, TypeError) as error:
        raise ReportError("Malformed report fields") from error
    if len(values) != len(pieces) or set(values) != set(expected):
        raise ReportError("Missing/duplicate report fields")
    return values


def new_transfer(text):
    header = fields(text, ["transfer", "run", "parts", "bytes", "sha256", "schema"])
    for key in ["transfer", "run"]:
        if not re.fullmatch(r"[A-Za-z0-9-]{1,80}", header[key]):
            raise ReportError("Invalid transfer identity")
    try:
        parts, length = int(header["parts"]), int(header["bytes"])
    except ValueError as error:
        raise ReportError("Invalid transfer counts") from error
    if (
        header["schema"] != "2"
        or not 0 < length <= MAX_REPORT_BYTES
        or parts != math.ceil(length / CHUNK_BYTES)
    ):
        raise ReportError("Unsupported/oversized report")
    if not re.fullmatch("[0-9a-f]{64}", header["sha256"]):
        raise ReportError("Invalid checksum")
    return {"header": header, "parts": parts, "length": length, "chunks": {}, "ended": False}


def add_chunk(state, text):
    frame = fields(text, ["transfer", "run", "part", "data"])
    header = state["header"]
    if any(frame[key] != header[key] for key in ["transfer", "run"]):
        raise ReportError("Mixed transfer identity")
    if state["ended"]:
        raise ReportError("Data after report end")
    try:
        index, total = map(int, frame["part"].split("/"))
        data = base64.b64decode(frame["data"], validate=True)
    except (ValueError, binascii.Error) as error:
        raise ReportError("Invalid report chunk") from error
    if total != state["parts"] or not 1 <= index <= total:
        raise ReportError("Invalid chunk number")
    expected = min(CHUNK_BYTES, state["length"] - (index - 1) * CHUNK_BYTES)
    if len(data) != expected:
        raise ReportError("Invalid chunk length")
    if index in state["chunks"] and state["chunks"][index] != data:
        raise ReportError("Conflicting duplicate chunk")
    state["chunks"][index] = data


def finish_transfer(state, text):
    footer = fields(text, ["transfer", "run", "parts", "sha256"])
    if state["ended"] or any(footer[key] != state["header"][key] for key in footer):
        raise ReportError("Invalid report end")
    state["ended"] = True


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ReportError("Duplicate JSON key")
        result[key] = value
    return result


def invalid_constant(value):
    raise ReportError("Non-finite JSON number")


def payload_document(state):
    if state is None or not state["ended"] or len(state["chunks"]) != state["parts"]:
        raise ReportError("Incomplete latest transfer; re-emit the last report")
    raw = b"".join(state["chunks"][index] for index in range(1, state["parts"] + 1))
    if len(raw) != state["length"] or hashlib.sha256(raw).hexdigest() != state["header"]["sha256"]:
        raise ReportError("Report checksum mismatch")
    try:
        document = json.loads(
            raw.decode("utf-8"), object_pairs_hook=unique_object, parse_constant=invalid_constant
        )
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ReportError("Invalid UTF8/JSON report") from error
    if (
        not isinstance(document, dict)
        or document.get("schema") != 2
        or document.get("runId") != state["header"]["run"]
    ):
        raise ReportError("Payload/header mismatch")
    if document.get("status") != "complete":
        raise ReportError("Last run is not complete")
    return document


def decode_transfer(lines):
    state = None
    for original in lines:
        line = original.strip()
        if len(line) > 1800:
            raise ReportError("Oversized transport line")
        if line.startswith("report begin "):
            state = new_transfer(line[len("report begin ") :])
        elif state is not None and line.startswith("report chunk "):
            add_chunk(state, line[len("report chunk ") :])
        elif state is not None and line.startswith("report end "):
            finish_transfer(state, line[len("report end ") :])
    return payload_document(state)
