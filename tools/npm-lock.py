#!/usr/bin/env python3
"""Keeps package-lock.json in step with bun.lock, for builders that install with npm (F-Droid).

  python3 tools/npm-lock.py          regenerate package-lock.json from the bun-installed node_modules
  python3 tools/npm-lock.py --check  fail if the two lockfiles resolve any package differently

Run it after `bun install` whenever dependencies change, and commit both lockfiles.
"""
from pathlib import Path
import json
import re
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
REGISTRY = 'https://registry.npmjs.org/'


def bun_packages():
    text = re.sub(r',(\s*[}\]])', r'\1', (root / 'bun.lock').read_text())
    packages = {}
    for entry in json.loads(text)['packages'].values():
        name, _, version = entry[0].rpartition('@')
        packages[(name, version)] = entry[3] if len(entry) > 3 else None
    return packages


def npm_packages(lock):
    for path, info in lock['packages'].items():
        if path and not info.get('link'):
            yield path, path.rpartition('node_modules/')[2], info


def check(lock, bun):
    npm = {(name, info['version']) for _, name, info in npm_packages(lock)}
    problems = [f'only in bun.lock: {n}@{v}' for n, v in sorted(set(bun) - npm)]
    problems += [f'only in package-lock.json: {n}@{v}' for n, v in sorted(npm - set(bun))]
    for path, name, info in npm_packages(lock):
        if info.get('integrity') != bun.get((name, info['version'])):
            problems.append(f'integrity differs: {path}')
    return problems


def main():
    bun = bun_packages()
    if '--check' not in sys.argv:
        (root / 'package-lock.json').unlink(missing_ok=True)
        subprocess.run(['npm', 'install', '--package-lock-only', '--ignore-scripts', '--no-audit', '--no-fund'],
                       cwd=root, check=True)
        lock = json.loads((root / 'package-lock.json').read_text())
        # npm can't recover these from a node_modules tree that bun installed.
        packages = lock['packages']
        for path, name, info in list(npm_packages(lock)):
            integrity = bun.get((name, info['version']))
            if integrity:
                tarball = f"{REGISTRY}{name}/-/{name.rpartition('/')[2]}-{info['version']}.tgz"
                rest = {k: v for k, v in info.items() if k not in ('version', 'resolved', 'integrity')}
                packages[path] = {'version': info['version'], 'resolved': tarball, 'integrity': integrity, **rest}
        (root / 'package-lock.json').write_text(json.dumps(lock, indent=2) + '\n')
    problems = check(json.loads((root / 'package-lock.json').read_text()), bun)
    for problem in problems:
        print(problem, file=sys.stderr)
    if problems:
        sys.exit('package-lock.json is out of step with bun.lock; run python3 tools/npm-lock.py')
    print('package-lock.json matches bun.lock')


main()
