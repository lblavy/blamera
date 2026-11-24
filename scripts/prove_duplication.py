"""Integration fixture: the real pinned detector and gate must reject copied code."""

from pathlib import Path
import subprocess

from quality_gates import QualityError, duplication_metrics, require_duplication


def main():
    root = Path(__file__).resolve().parents[1]
    fixture = root / "build/quality-fixtures/duplication"
    sources = fixture / "app/src/main/java"
    sources.mkdir(parents=True, exist_ok=True)
    code = """fun calculate(value: Int): Int {
    val first = value + 10
    val second = first * 2
    val third = second - 3
    val fourth = third / 2
    val fifth = fourth + first
    val sixth = fifth * second
    val seventh = sixth - third
    return seventh + fourth + fifth + sixth
}
"""
    changed = {}
    for name in ("A.kt", "B.kt"):
        (sources / name).write_text(code, encoding="utf-8")
        changed["app/src/main/java/" + name] = set(range(1, len(code.splitlines()) + 1))
    output = root / "build/reports/quality/self-tests/duplication"
    subprocess.run(["node", "node_modules/jscpd/bin/jscpd", sources.relative_to(root).as_posix(),
                    "--config", ".jscpd.json", "--output", output.relative_to(root).as_posix(),
                    "--reporters", "json"], check=True, cwd=root)
    metrics = duplication_metrics(output / "jscpd-report.json", changed, fixture)
    try:
        require_duplication(metrics["new_duplicated_lines"], metrics["new_lines"])
    except QualityError:
        print("Copied-code fixture correctly rejected:", metrics)
        return
    raise QualityError("Copied-code fixture incorrectly passed the duplication gate")


if __name__ == "__main__":
    main()
