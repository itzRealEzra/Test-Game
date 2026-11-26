#!/usr/bin/env python3
"""Build an Artemiz split-APK delta archive for one exact source version."""

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

try:
    import bsdiff4
except ImportError as error:
    raise SystemExit("Install the updater tools with: python -m pip install -r update-client/requirements.txt") from error


PACKAGE_NAME = "uk.playdrop.cherrytree_idletextrpg"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def load_xapk(path: Path):
    with zipfile.ZipFile(path, "r") as archive:
        manifest = json.loads(archive.read("manifest.json"))
        if manifest.get("package_name") != PACKAGE_NAME:
            raise SystemExit(f"Wrong package in {path}: {manifest.get('package_name')!r}")
        rows = manifest.get("split_apks")
        if not isinstance(rows, list) or not 2 <= len(rows) <= 8:
            raise SystemExit(f"Invalid split_apks list in {path}")
        parts = {}
        for row in rows:
            split_id = row.get("id", "")
            filename = row.get("file", "")
            if not re.fullmatch(r"[A-Za-z0-9_.-]+", split_id):
                raise SystemExit(f"Invalid split id in {path}: {split_id!r}")
            if not re.fullmatch(r"[A-Za-z0-9_.-]+\.apk", filename):
                raise SystemExit(f"Invalid APK filename in {path}: {filename!r}")
            if split_id in parts:
                raise SystemExit(f"Duplicate split id in {path}: {split_id}")
            try:
                parts[split_id] = (filename, archive.read(filename))
            except KeyError as error:
                raise SystemExit(f"Missing {filename} in {path}") from error
    if "base" not in parts:
        raise SystemExit(f"No base APK in {path}")
    return manifest, parts


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--from-xapk", required=True, type=Path, help="Installed source XAPK")
    parser.add_argument("--to-xapk", required=True, type=Path, help="Target release XAPK")
    parser.add_argument("--output", required=True, type=Path, help="Output .patch.zip file")
    parser.add_argument("--url", default="PATCH_URL_TO_SET_AFTER_HOSTING", help="HTTPS URL for update.json")
    args = parser.parse_args()

    old_manifest, old_parts = load_xapk(args.from_xapk)
    new_manifest, new_parts = load_xapk(args.to_xapk)
    from_version = int(old_manifest["version_code"])
    to_version = int(new_manifest["version_code"])
    if to_version <= from_version:
        raise SystemExit("Target version_code must be greater than source version_code")
    if old_parts.keys() != new_parts.keys():
        raise SystemExit("Source and target XAPKs must contain the same split IDs")

    patch_rows = []
    patch_blobs = []
    for split_id in sorted(new_parts):
        filename, old_bytes = old_parts[split_id]
        new_filename, new_bytes = new_parts[split_id]
        if filename != new_filename:
            raise SystemExit(f"APK name changed for split {split_id}")
        patch_path = f"patches/{split_id}.bsdiff"
        patch_bytes = bsdiff4.diff(old_bytes, new_bytes)
        patch_rows.append({
            "id": split_id,
            "file": new_filename,
            "patch": patch_path,
            "old_sha256": sha256(old_bytes),
            "new_sha256": sha256(new_bytes),
            "new_size": len(new_bytes),
        })
        patch_blobs.append((patch_path, patch_bytes))

    patch_manifest = {
        "format": "artemiz-bsdiff-v1",
        "package_name": PACKAGE_NAME,
        "from_version_code": from_version,
        "to_version_code": to_version,
        "split_apks": patch_rows,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        archive.writestr("patch.json", json.dumps(patch_manifest, separators=(",", ":")))
        for name, data in patch_blobs:
            archive.writestr(name, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)

    digest = hashlib.sha256(args.output.read_bytes()).hexdigest()
    delta_entry = {
        "from_version_code": from_version,
        "to_version_code": to_version,
        "url": args.url,
        "sha256": digest,
        "size_bytes": args.output.stat().st_size,
    }
    print(f"Patch archive: {args.output} ({args.output.stat().st_size:,} bytes)")
    print(f"SHA-256: {digest}")
    print("Add this object under the matching ABI's `deltas` in update.json:")
    print(json.dumps({str(from_version): delta_entry}, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
