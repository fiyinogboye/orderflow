"""Package the source, keeping Java packages even if they are named 'data'."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

root = Path(__file__).resolve().parents[1]
destination = root.parent / "OrderFlow-Java-Learning-Project.zip"
excluded_root_folders = {"target", "data", ".git", ".idea", ".vscode"}

files = []
for path in root.rglob("*"):
    relative = path.relative_to(root)
    # Only exclude the runtime data folder at the project root.
    # src/main/java/.../data contains code and must stay in the ZIP.
    if relative.parts[0] in excluded_root_folders:
        continue
    if path.is_file() and path.name not in {".env", ".DS_Store"}:
        files.append(path)

with ZipFile(destination, "w", ZIP_DEFLATED) as archive:
    for path in sorted(files):
        archive.write(path, "orderflow/" + path.relative_to(root).as_posix())

with ZipFile(destination) as archive:
    assert archive.testzip() is None
    expected_sources = {"orderflow/" + p.relative_to(root).as_posix()
                        for p in (root / "src").rglob("*") if p.is_file()}
    assert expected_sources.issubset(set(archive.namelist())), "Source missing from ZIP"
    for path in files:
        assert archive.read("orderflow/" + path.relative_to(root).as_posix()) == path.read_bytes()

print(f"Packaged and checked {len(files)} files: {destination}")
