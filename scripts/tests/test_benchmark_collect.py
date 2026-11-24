import io
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from unittest.mock import patch

from benchmark_collect import collect, live_lines, main, stop_reader


class CollectorTests(unittest.TestCase):
    def test_partial_overlong_and_overflow_capture_are_rejected_and_own_reader_times_out(self):
        for text in ["nothing\n", "x" * 1802]:
            with self.assertRaises(ValueError):
                live_lines(Mock(stdout=io.StringIO(text)), io.StringIO())
        process = Mock()
        process.poll.return_value = None
        process.wait.side_effect = [subprocess.TimeoutExpired("reader", 5), 0]
        stop_reader(process)
        process.kill.assert_called_once()
        process = Mock()
        process.poll.return_value = 0
        stop_reader(process)
        process.terminate.assert_not_called()
        with (
            patch(
                "sys.argv",
                ["collector", "--adb", "adb", "--serial", "local", "--output", "unused.log"],
            ),
            patch("benchmark_collect.collect") as fake,
        ):
            main()
            fake.assert_called_once()

    def test_only_pidof_and_strict_tag_live_logcat_are_used_and_only_own_client_is_closed(self):
        run = Mock(return_value=subprocess.CompletedProcess([], 0, "1234\n", ""))
        process = Mock(
            stdout=io.StringIO(
                "old end\nreport begin transfer=t run=r\nreport chunk x\nreport end transfer=t run=r\n"
            )
        )
        process.poll.return_value = None
        popen = Mock(return_value=process)
        ready = Mock()
        with tempfile.TemporaryDirectory(
            dir=Path(__file__).resolve().parents[2] / "build"
        ) as directory:
            target = Path(directory) / "capture.log"
            collect("adb.exe", "local-target", target, run, popen, ready)
            self.assertIn("report end", target.read_text())
            self.assertEqual(
                run.call_args.args[0],
                ["adb.exe", "-s", "local-target", "shell", "pidof", "-s", "dev.blamera"],
            )
            self.assertEqual(
                popen.call_args.args[0],
                [
                    "adb.exe",
                    "-s",
                    "local-target",
                    "logcat",
                    "--pid=1234",
                    "-v",
                    "raw",
                    "-T",
                    "1",
                    "BlameraBenchmark:I",
                    "*:S",
                ],
            )
            self.assertEqual(ready.call_count, 1)
            self.assertEqual(process.terminate.call_count, 1)
            self.assertEqual(process.wait.call_count, 1)

    def test_bad_pid_and_existing_output_do_not_start_capture(self):
        with tempfile.TemporaryDirectory(
            dir=Path(__file__).resolve().parents[2] / "build"
        ) as directory:
            target = Path(directory) / "capture.log"
            for value in ["", "12 34", "0", "12;bad"]:
                run = Mock(return_value=subprocess.CompletedProcess([], 0, value, ""))
                popen = Mock()
                with self.assertRaises((ValueError, FileExistsError)):
                    collect("adb", "s", target, run, popen)
                popen.assert_not_called()
            target.write_text("existing")
            with self.assertRaises(FileExistsError):
                collect("adb", "s", target, run, popen)
            self.assertEqual(target.read_text(), "existing")


if __name__ == "__main__":
    unittest.main()
