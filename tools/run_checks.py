#!/usr/bin/env python3
"""Run host-side regression checks against already-built app classes; no TV access."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]

def run(args):
    subprocess.run([str(a) for a in args], cwd=ROOT, check=True)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--variant', choices=['debug', 'release'], default='debug')
    parser.add_argument('--json-jar', type=Path, required=True, help='org.json JVM runtime JAR, e.g. org.json:json:20240303')
    parser.add_argument('--android-jar', type=Path)
    parser.add_argument('--launcher-dir', type=Path, default=ROOT / 'launcher', help='Launcher checkout containing the built modules')
    args = parser.parse_args()
    runtime = args.json_jar.resolve()
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    android = args.android_jar.resolve() if args.android_jar else Path(sdk or '') / 'platforms/android-34/android.jar'
    variant = args.variant
    capital = variant.capitalize()
    classes = [args.launcher_dir.resolve() / f'{module}/build/intermediates/javac/{variant}/compile{capital}JavaWithJavac/classes' for module in ('app', 'companion')]
    for path in [runtime, android, *classes]:
        if not path.exists():
            parser.error(f'Missing dependency: {path}. Build both {variant} apps first and provide the SDK/JSON paths.')
    run([sys.executable, 'tools/generate_cleanup.py', '--check'])
    run([sys.executable, '-m', 'unittest', 'discover', '-s', 'tests', '-p', 'test_*.py'])
    java_home = os.environ.get('JAVA_HOME')
    executable = lambda name: Path(java_home) / 'bin' / (name + ('.exe' if os.name == 'nt' else '')) if java_home else name
    output = ROOT / f'build/host-tests/{variant}'
    output.mkdir(parents=True, exist_ok=True)
    classpath = os.pathsep.join(str(path) for path in [runtime, android, *classes])
    tests = sorted((ROOT / 'tests').glob('*Test.java'))
    run([executable('javac'), '-encoding', 'UTF-8', '-cp', classpath, '-d', output, *tests])
    for test in tests:
        source = test.read_text(encoding='utf-8-sig')
        package = re.search(r'\bpackage\s+([\w.]+)\s*;', source)
        name = (package[1] + '.' if package else '') + test.stem
        run([executable('java'), '-cp', str(output) + os.pathsep + classpath, name])
    print(f'PASS: Python checks and {len(tests)} Java suites ({variant}).')

if __name__ == '__main__':
    main()
