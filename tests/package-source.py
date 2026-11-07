"""Rebuild the static site's portable source download without repository metadata."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

root = Path(__file__).resolve().parents[1]
files = [root / 'README.md', root / 'run-java.sh']
for directory, patterns in [('java', ['*.java']), ('tests', ['*.mjs', '*.py']), ('docs', ['*.md'])]:
    for pattern in patterns:
        files.extend(sorted((root / directory).rglob(pattern)))
files.extend(root / 'dist' / name for name in ['index.html', 'style.css', 'app.mjs', 'engine.mjs', 'proposal.html'])
with ZipFile(root / 'dist/source.zip', 'w', ZIP_DEFLATED) as archive:
    for path in files:
        archive.write(path, Path('freshness-prototype') / path.relative_to(root))
print(f'Packaged {len(files)} portable source files.')
