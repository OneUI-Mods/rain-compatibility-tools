#!/usr/bin/env python3
"""Build and package a continuously signed release from committed sources."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CERTIFICATE = '7fff3c5765ee1fb0a351c286ec190de50905feac997c9a991befe6fd6fb4c281'


def run(*args, capture=False):
    return subprocess.run([str(value) for value in args], cwd=ROOT, check=True,
                          text=True, capture_output=capture)


def main():
    if not os.environ.get('ONEUI_SIGNING_PROPERTIES'):
        raise SystemExit('Set ONEUI_SIGNING_PROPERTIES to the private UTF-8 signing properties file')
    run('git', 'diff', '--exit-code', 'HEAD')
    if run('git', 'ls-files', '--others', '--exclude-standard', capture=True).stdout.strip():
        raise SystemExit('Commit release sources first')
    run('./gradlew', ':app:assembleRelease', ':app:lintRelease')
    tools = Path(os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk')) / 'build-tools/37.0.0'
    output = ROOT / 'app/build/outputs/apk/release'
    metadata = json.loads((output / 'output-metadata.json').read_text())
    entry = metadata['elements'][0]
    assert metadata['variantName'] == 'release'
    version = entry['versionName']
    apk = output / entry['outputFile']
    with zipfile.ZipFile(apk) as archive:
        assert not any(name.startswith('META-INF/xposed/') for name in archive.namelist())
        assert archive.read('assets/classes.dex') == (ROOT / 'app/build/generated/payloadAssets/classes.dex').read_bytes()
    badging = run(tools / 'aapt2', 'dump', 'badging', apk, capture=True).stdout
    assert "name='dev.ryuya.raincompat'" in badging
    assert "targetSdkVersion:'37'" in badging and 'application-debuggable' not in badging
    signature = run(tools / 'apksigner', 'verify', '--verbose', '--print-certs', apk, capture=True).stdout
    assert CERTIFICATE in signature
    run(tools / 'zipalign', '-c', '-P', '16', '4', apk)
    destination = ROOT / 'artifacts/release' / version
    destination.mkdir(parents=True, exist_ok=True)
    signed = destination / f'RainCompatibilityTools-{version}-release.apk'
    shutil.copy2(apk, signed)
    source = destination / f'RainCompatibilityTools-{version}-source.zip'
    run('git', 'archive', '--format=zip', f'--prefix=RainCompatibilityTools-{version}/',
        f'--output={source}', 'HEAD')
    (destination / 'verification.txt').write_text(signature + '\nRelease variant; SDK 37; 16 KiB aligned.\n')
    release = {'schema': 1, 'packageName': 'dev.ryuya.raincompat',
               'versionCode': entry['versionCode'], 'versionName': version, 'apk': signed.name,
               'sha256': hashlib.sha256(signed.read_bytes()).hexdigest()}
    (destination / 'release-metadata.json').write_text(json.dumps(release, indent=2) + '\n')
    files = [signed, source, destination / 'verification.txt', destination / 'release-metadata.json']
    (destination / 'SHA256SUMS.txt').write_text(''.join(
        hashlib.sha256(path.read_bytes()).hexdigest() + '  ' + path.name + '\n' for path in files))
    print(signed)


if __name__ == '__main__':
    main()
