import hashlib
import tempfile
import unittest
from pathlib import Path
from contextlib import contextmanager
import numpy as np
from runner_core import verify_files, run_frames


class RunnerTests(unittest.TestCase):
    def test_tamper_and_outside_workspace_fail_before_session_can_load(self):
        with tempfile.TemporaryDirectory(
            dir="build/reports/validation/m1-yoloe-quality"
        ) as directory:
            root = Path(directory)
            path = root / "input.jpg"
            path.write_bytes(b"frozen")
            records = [
                {"path": "input.jpg", "sha256": hashlib.sha256(b"frozen").hexdigest()}
            ]
            verify_files(root, records)
            path.write_bytes(b"tampered")
            self.assertRaises(ValueError, verify_files, root, records)
            self.assertRaises(
                ValueError, verify_files, root, [{"path": "../outside", "sha256": "x"}]
            )

    def test_exactly_one_forward_per_model_image_and_no_overlapping_sessions(self):
        events = []
        active = []
        results = []

        @contextmanager
        def factory(model):
            self.assertEqual(active, [])
            active.append(model)
            events.append(("open", model))

            class Session:
                names = {0: "cat"}

                def run(self, tensor):
                    events.append(("forward", model))
                    return np.zeros((1, 37, 8400), np.float32), np.zeros(
                        (1, 32, 160, 160), np.float32
                    )

            try:
                yield Session()
            finally:
                events.append(("close", model))
                active.pop()

        profiles = [
            {"model": "baseline", "key": "a"},
            {"model": "yoloe", "key": "b"},
            {"model": "yoloe", "key": "c"},
        ]
        records = [{"key": "one"}, {"key": "two"}]

        def evaluate(record, model, names, raw, proto, projection, chosen):
            return [{"key": record["key"], "profile": p["key"]} for p in chosen]

        count = run_frames(
            records,
            profiles,
            factory,
            lambda record: np.zeros((8, 8, 3), np.uint8),
            evaluate,
            results.append,
        )
        self.assertEqual(count, 4)
        self.assertEqual(len(results), 6)
        self.assertEqual(
            events,
            [
                ("open", "baseline"),
                ("forward", "baseline"),
                ("forward", "baseline"),
                ("close", "baseline"),
                ("open", "yoloe"),
                ("forward", "yoloe"),
                ("forward", "yoloe"),
                ("close", "yoloe"),
            ],
        )

    def test_failure_closes_owned_session_without_starting_next_model(self):
        events = []

        @contextmanager
        def factory(model):
            events.append("open")

            class Session:
                names = {}

                def run(self, tensor):
                    raise ValueError("bad native output")

            try:
                yield Session()
            finally:
                events.append("close")

        with self.assertRaisesRegex(ValueError, "bad native"):
            run_frames(
                [{}],
                [{"model": "baseline"}],
                factory,
                lambda record: np.zeros((2, 2, 3), np.uint8),
                None,
                None,
            )
        self.assertEqual(events, ["open", "close"])


if __name__ == "__main__":
    unittest.main()
