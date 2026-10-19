#!/usr/bin/env python3
"""Export reviewed public source without machine files or Git history.

SPDX-License-Identifier: GPL-3.0-or-later
Added for Yuklick on 2026-10-20. See CHANGELOG.md.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

ROOT = Path(__file__).resolve().parents[1]
ROOT_FILES = {
    '.gitignore', '.gitattributes', 'README.md', 'LICENSE', 'CHANGELOG.md',
    'THIRD_PARTY_NOTICES.md', 'build.gradle.kts', 'settings.gradle.kts',
    'gradle.properties', 'gradlew', 'gradlew.bat',
}
ROOT_DIRS = {'.github', 'build-logic', 'core', 'feature', 'gradle', 'scripts',
             'smartautoclicker', 'documentation'}
BLOCK_DIRS = {'.git', '.gradle', '.kotlin', '.idea', '.vscode', '.cxx',
              '.externalNativeBuild', '__pycache__', 'build', 'node_modules',
              '.local-tools', '.codex', '.agents', '.aws', 'captures'}
BLOCK_SUFFIXES = {'.apk', '.aab', '.aar', '.dex', '.class', '.log', '.jks',
                  '.keystore', '.p12', '.pfx', '.pem', '.key', '.hprof', '.dmp',
                  '.dump', '.trace', '.tmp', '.bak', '.pyc', '.zip', '.7z', '.orig', '.rej'}
BLOCK_NAMES = {'local.properties', 'key.properties', 'keystore.properties',
               'signing.properties', 'google-services.json', 'credentials.json',
               'Thumbs.db', '.DS_Store', 'ComponentConfig.kt'}
BLOCK_PREFIXES = ('core/smart/detection/src/release/opencv/',
                  'core/smart/detection/src/release/ncnn/', 'documentation/diagrams/')
# Only the maintained workflow is published; no inherited schedule or store deployment.
WORKFLOW = '.github/workflows/build.yml'
PATTERNS = {
    'private key': re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    'GitHub token': re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})\b'),
    'cloud access key': re.compile(r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b'),
    'Slack token': re.compile(r'\bxox[baprs]-[A-Za-z0-9-]{20,}\b'),
    'user home path': re.compile(r'(?:[A-Za-z]:[\\/](?:Users|Documents and Settings)[\\/][^\s"<>]+|/(?:Users|home)/[^\s"<>/]+/)'),
    'credential in URL': re.compile(r'https?://[^\s/:]+:[^\s/@]+@'),
}


def allowed(relative: Path) -> bool:
    parts = relative.parts
    name = relative.name
    path = relative.as_posix()
    if len(parts) == 1:
        return name in ROOT_FILES
    if parts[0] not in ROOT_DIRS or any(p in BLOCK_DIRS for p in parts):
        return False
    if name in BLOCK_NAMES or name == '.env' or name.startswith('.env.'):
        return False
    if relative.suffix.lower() in BLOCK_SUFFIXES or '.log.' in name:
        return False
    if path.startswith(BLOCK_PREFIXES):
        return False
    if path.startswith('.github/workflows/') and path != WORKFLOW:
        return False
    # Keep the wrapper JAR; other dependency binaries are retrieved by Gradle.
    if relative.suffix.lower() == '.jar' and path != 'gradle/wrapper/gradle-wrapper.jar':
        return False
    return True


def source_files() -> list[Path]:
    import os
    result = []
    for folder, dirs, names in os.walk(ROOT, followlinks=False):
        directory = Path(folder)
        relative_dir = directory.relative_to(ROOT)
        dirs[:] = [d for d in dirs if d not in BLOCK_DIRS and
                   not (directory / d).is_symlink() and
                   (directory != ROOT or d in ROOT_DIRS) and
                   not ((relative_dir / d).as_posix() + '/').startswith(BLOCK_PREFIXES)]
        for name in names:
            file = directory / name
            if file.is_symlink():
                continue
            relative = file.relative_to(ROOT)
            if allowed(relative):
                result.append(relative)
    return sorted(result)


def scan(files: list[Path]) -> list[dict[str, object]]:
    findings = []
    for relative in files:
        data = (ROOT / relative).read_bytes()
        if len(data) > 95 * 1024 * 1024:
            findings.append({'file': relative.as_posix(), 'reason': 'file exceeds 95 MiB'})
        if b'\0' in data:
            continue
        try:
            text = data.decode('utf-8-sig')
        except UnicodeDecodeError:
            continue
        for label, pattern in PATTERNS.items():
            for match in pattern.finditer(text):
                # Report location only; never echo a matched secret.
                findings.append({'file': relative.as_posix(), 'line': text.count('\n', 0, match.start()) + 1,
                                 'reason': label})
    return findings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='scan only; do not create an export')
    args = parser.parse_args()
    files = source_files()
    missing = sorted(ROOT_FILES - {p.as_posix() for p in files})
    if missing:
        print('Missing required source files:', ', '.join(missing), file=sys.stderr)
        return 1
    findings = scan(files)
    if findings:
        print(json.dumps({'status': 'blocked', 'findings': findings}, ensure_ascii=False, indent=2))
        return 1
    print(f'Scanned {len(files)} public files; no configured secret/path patterns found.')
    if args.check:
        return 0
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    output = ROOT / '.local-tools' / 'publication' / stamp
    output.mkdir(parents=True, exist_ok=False)
    tree = output / 'Yuklick'
    archive = output / 'Yuklick-source.zip'
    manifest = []
    with ZipFile(archive, 'w', ZIP_DEFLATED) as package:
        for relative in files:
            target = tree / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / relative, target)
            data = target.read_bytes()
            mode = 0o755 if relative.as_posix() == 'gradlew' else 0o644
            target.chmod(mode)
            entry = ZipInfo('Yuklick/' + relative.as_posix())
            entry.create_system = 3
            entry.external_attr = (0o100000 | mode) << 16
            entry.compress_type = ZIP_DEFLATED
            package.writestr(entry, data)
            manifest.append({'file': relative.as_posix(), 'bytes': len(data),
                             'sha256': hashlib.sha256(data).hexdigest()})
    (output / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    (output / 'SHA256SUMS').write_text(f'{digest}  {archive.name}\n', encoding='ascii')
    print(f'Public tree: {tree}\nSource ZIP: {archive}\nSHA-256: {digest}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
