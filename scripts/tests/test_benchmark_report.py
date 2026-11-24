import base64
import hashlib
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from benchmark_report import ReportError, decode_transfer


def wire(document, transfer="transfer-one"):
    raw = json.dumps(document, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    chunks = [raw[i : i + 1024] for i in range(0, len(raw), 1024)]
    sha = hashlib.sha256(raw).hexdigest()
    identity = f"transfer={transfer} run={document['runId']}"
    return [
        f"report begin {identity} parts={len(chunks)} bytes={len(raw)} sha256={sha} schema=2",
        *[
            f"report chunk {identity} part={i}/{len(chunks)} data={base64.b64encode(chunk).decode()}"
            for i, chunk in enumerate(chunks, 1)
        ],
        f"report end {identity} parts={len(chunks)} sha256={sha}",
    ]


class ReportWireTests(unittest.TestCase):
    def test_malformed_headers_footer_and_json_are_rejected(self):
        good = wire(self.payload())
        changes = [
            [good[0].replace("schema=2", "schema=3"), *good[1:]],
            [good[0].replace("parts=", "parts=bad"), *good[1:]],
            [good[0].replace("transfer=transfer-one", "transfer=bad!"), *good[1:]],
            [good[0] + " schema=2", *good[1:]],
            [good[0].replace(" sha256=", " sha256=!"), *good[1:]],
            [*good[:-1], good[-1].replace("run=run-one", "run=other")],
            [good[0], good[1].replace("part=1/", "part=0/"), *good[2:]],
            [good[0], good[1].replace("data=", "data=!"), *good[2:]],
            [good[0], good[1].replace("part=1/", "part=bad/"), *good[2:]],
        ]
        for lines in changes:
            with self.assertRaises(ReportError):
                decode_transfer(lines)

    def payload(self):
        return {"schema": 2, "runId": "run-one", "status": "complete", "text": "é猫" * 2000}

    def test_utf8_chunks_reassemble_with_hash_numbering_and_identical_duplicate_allowed(self):
        payload = self.payload()
        lines = wire(payload)
        self.assertEqual(
            decode_transfer(
                ["old unrelated line", lines[0], *reversed(lines[1:-1]), lines[1], lines[-1]]
            ),
            payload,
        )

    def test_missing_corrupt_incomplete_latest_and_conflicting_duplicate_fail_closed(self):
        good = wire(self.payload())
        bad = good.copy()
        bad[1] = bad[1][:-4] + "AAAA"
        missing = [good[0], *good[2:]]
        conflict = [*good[:-1], good[1][:-4] + "AAAA", good[-1]]
        for lines in [
            [],
            good[1:],
            good[:-1],
            bad,
            missing,
            conflict,
            [*good, *wire(self.payload(), "new-transfer")[:-1]],
        ]:
            with self.subTest(lines=len(lines)):
                self.assertRaises(ReportError, decode_transfer, lines)

    def test_payload_identity_status_schema_and_transport_limits_are_checked(self):
        good = wire(self.payload())
        cases = [
            wire({**self.payload(), "status": "running"}),
            wire({**self.payload(), "status": "cancelled"}),
            wire({**self.payload(), "status": "error"}),
            wire({**self.payload(), "schema": 1}),
            [good[0].replace("run=run-one", "run=other"), *good[1:]],
            ["x" * 1801],
            [good[0].replace("parts=10", "parts=999999")],
        ]
        for lines in cases:
            with self.subTest(lines=len(lines)):
                self.assertRaises(ReportError, decode_transfer, lines)


if __name__ == "__main__":
    unittest.main()
