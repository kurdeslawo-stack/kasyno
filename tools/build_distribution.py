"""Package the compiled plugin, resource pack and administrator README."""
from pathlib import Path
import shutil
import zipfile
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / 'dist'
project = ElementTree.parse(ROOT / 'pom.xml').getroot()
VERSION = project.findtext('{http://maven.apache.org/POM/4.0.0}version')
if not VERSION:
    raise ValueError('Project version is missing from pom.xml')
JAR_NAME = f'CasinoCore-{VERSION}.jar'
PACK_NAME = 'CasinoCore_ResourcePack_26.3.zip'
ARCHIVE = DIST / 'CasinoCore_TestServer_26.3.zip'


def main():
    jar = ROOT / 'target' / JAR_NAME
    pack = DIST / PACK_NAME
    for path in (jar, pack):
        if not path.is_file():
            raise FileNotFoundError(f'Missing build artifact: {path.name}')
        with zipfile.ZipFile(path) as archive:
            if archive.testzip() is not None:
                raise ValueError(f'Invalid archive: {path.name}')
    DIST.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(jar, DIST / JAR_NAME)
    files = {
        JAR_NAME: jar,
        PACK_NAME: pack,
        'CasinoCore/config.yml': ROOT / 'src/main/resources/config.yml',
        'README.md': ROOT / 'README.md',
    }
    with zipfile.ZipFile(ARCHIVE, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, path in files.items():
            entry = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, path.read_bytes(), compresslevel=9)
    with zipfile.ZipFile(ARCHIVE) as archive:
        if set(archive.namelist()) != set(files) or archive.testzip() is not None:
            raise ValueError('Invalid distribution structure')
    print(f'Ready: dist/{ARCHIVE.name}')


if __name__ == '__main__':
    main()
