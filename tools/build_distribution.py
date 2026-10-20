"""Package the Laina plugin and administrator configuration; no bundled resource pack."""
from pathlib import Path
import hashlib
import shutil
import zipfile
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / 'dist'
VERSION = ElementTree.parse(ROOT / 'pom.xml').getroot().findtext('{http://maven.apache.org/POM/4.0.0}version')
if not VERSION:
    raise ValueError('Missing project version')
JAR_NAME = f'CasinoCore-{VERSION}.jar'
ARCHIVE = DIST / f'CasinoCore_Laina_{VERSION}_26.3.zip'


def main():
    jar = ROOT / 'target' / JAR_NAME
    with zipfile.ZipFile(jar) as source:
        if source.testzip() is not None:
            raise ValueError('Invalid plugin archive')
        assert not any('/visual/' in n or '/interaction/' in n or n.startswith('assets/') for n in source.namelist())
    DIST.mkdir(exist_ok=True)
    shutil.copyfile(jar, DIST / JAR_NAME)
    files = {
        JAR_NAME: jar.read_bytes(),
        'CasinoCore/config.yml': (ROOT / 'src/main/resources/config.yml').read_bytes(),
        'README.md': (ROOT / 'README.md').read_bytes(),
    }
    checksums = ''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n' for name, data in files.items())
    files['SHA256SUMS.txt'] = checksums.encode('utf-8')
    with zipfile.ZipFile(ARCHIVE, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as output:
        for name, data in files.items():
            entry = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            output.writestr(entry, data, compresslevel=9)
    with zipfile.ZipFile(ARCHIVE) as output:
        assert set(output.namelist()) == set(files) and output.testzip() is None
    print(f'PASS: {ARCHIVE.relative_to(ROOT)}')
    print(f'JAR SHA-256: {hashlib.sha256(jar.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    main()
