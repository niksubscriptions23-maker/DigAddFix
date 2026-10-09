#!/usr/bin/env python3
"""Run real JVM policy/protocol checks without downloading Gradle or an Android SDK."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
sources = sorted((root / "core/src/main/java").rglob("*.java"))
sources += sorted((root / "core/src/test/java").rglob("*.java"))
with tempfile.TemporaryDirectory(prefix="digaddfix-checks-") as output:
    subprocess.run(["java", "-m", "jdk.compiler/com.sun.tools.javac.Main",
                    "--release", "8", "-Xlint:all", "-d", output,
                    *map(str, sources)], check=True)
    subprocess.run(["java", "-ea", "-cp", output,
                    "com.digaddfix.core.CoreChecks", str(root)], check=True)
