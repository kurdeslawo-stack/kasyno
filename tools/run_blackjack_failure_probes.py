"""Run standalone fault-injection probes after mvn test/package.

Exit 1 means a safety guarantee is not implemented. These probes are deliberately
separate from regression tests: they verify crash recovery and safe reconciliation.
"""

from pathlib import Path
import shutil
import subprocess
import tempfile


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    classes = root / "target" / "classes"
    if not classes.is_dir():
        raise SystemExit("Build first: mvn test")
    # Windows Java launchers can mishandle emoji in canonical classpath names.
    with tempfile.TemporaryDirectory(prefix="casinocore-fault-probes-") as temporary:
        work = Path(temporary)
        shutil.copytree(classes, work / "classes")
        source = work / "BlackjackFailureProbe.java"
        shutil.copyfile(root / "tools" / "probes" / source.name, source)
        subprocess.run(
            ["javac", "-encoding", "UTF-8", "-cp", str(work / "classes"),
             "-d", str(work / "classes"), str(source)], check=True,
        )
        result = subprocess.run(
            ["java", "-cp", str(work / "classes"),
             "pl.kurdeslawo.casinocore.blackjack.BlackjackFailureProbe"],
            capture_output=True, text=True,
        )
        output = result.stdout + result.stderr
        (root / "target" / "failure-probe-results.txt").write_text(output, encoding="utf-8")
        print(output, end="")
        return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
