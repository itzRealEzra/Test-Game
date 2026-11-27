#!/usr/bin/env python3
"""Assemble a local XAPK from a matching split bundle and rebuilt APK files."""

import argparse
import json
import re
import subprocess
import zipfile
from pathlib import Path


def read_apk_metadata(aapt: str, apk_path: Path) -> dict[str, str]:
    result = subprocess.run(
        [aapt, "dump", "badging", str(apk_path)],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise SystemExit(f"Could not read APK manifest for {apk_path}: {result.stderr.strip()}")
    package_line = next(
        (line for line in result.stdout.splitlines() if line.startswith("package:")), None
    )
    if package_line is None:
        raise SystemExit(f"No package metadata found in {apk_path}")
    return dict(re.findall(r"([A-Za-z][A-Za-z0-9]*)='([^']*)'", package_line))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-xapk", required=True, type=Path)
    parser.add_argument("--output-xapk", required=True, type=Path)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--aapt", required=True, help="Path to Android SDK aapt")
    parser.add_argument(
        "--replace", action="append", default=[], metavar="SPLIT_ID=APK",
        help="Replace a split from the source bundle; repeat for every rebuilt split.",
    )
    args = parser.parse_args()

    replacements: dict[str, Path] = {}
    for value in args.replace:
        split_id, separator, file_name = value.partition("=")
        if not separator or not split_id or split_id in replacements:
            raise SystemExit(f"Invalid or repeated replacement: {value!r}")
        replacements[split_id] = Path(file_name)
        if not replacements[split_id].is_file():
            raise SystemExit(f"Replacement APK does not exist: {file_name}")

    args.output_xapk.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.source_xapk, "r") as source:
        manifest = json.loads(source.read("manifest.json"))
        package_name = manifest.get("package_name")
        if not isinstance(package_name, str) or not package_name:
            raise SystemExit("Source XAPK has no package name.")
        rows = manifest.get("split_apks")
        if not isinstance(rows, list) or not rows:
            raise SystemExit("Source XAPK has no split APK list.")
        split_by_id = {row.get("id"): row.get("file") for row in rows}
        if None in split_by_id or len(split_by_id) != len(rows):
            raise SystemExit("Source XAPK has invalid or duplicate split IDs.")
        if set(replacements) != set(split_by_id):
            raise SystemExit("Provide one rebuilt APK for every source split ID.")

        for split_id, apk_path in replacements.items():
            metadata = read_apk_metadata(args.aapt, apk_path)
            expected_split = "" if split_id == "base" else split_id
            if metadata.get("name") != package_name:
                raise SystemExit(f"Wrong package name in {apk_path}: {metadata.get('name')!r}")
            if metadata.get("versionCode") != str(args.version_code):
                raise SystemExit(
                    f"Wrong APK version code in {apk_path}: "
                    f"expected {args.version_code}, got {metadata.get('versionCode')!r}"
                )
            if metadata.get("versionName") != args.version_name:
                raise SystemExit(
                    f"Wrong APK version name in {apk_path}: "
                    f"expected {args.version_name!r}, got {metadata.get('versionName')!r}"
                )
            if metadata.get("split", "") != expected_split:
                raise SystemExit(
                    f"Wrong split ID in {apk_path}: "
                    f"expected {expected_split or 'base'!r}, got {metadata.get('split')!r}"
                )

        manifest = dict(manifest)
        manifest["version_code"] = str(args.version_code)
        manifest["version_name"] = args.version_name
        manifest["total_size"] = sum(path.stat().st_size for path in replacements.values())
        replacement_by_filename = {
            split_by_id[split_id]: path for split_id, path in replacements.items()
        }

        with zipfile.ZipFile(args.output_xapk, "w", compression=zipfile.ZIP_DEFLATED,
                             compresslevel=9) as output:
            for entry in source.infolist():
                if entry.filename == "manifest.json":
                    data = json.dumps(manifest, separators=(",", ":")).encode("utf-8")
                elif entry.filename in replacement_by_filename:
                    data = replacement_by_filename[entry.filename].read_bytes()
                else:
                    data = source.read(entry.filename)
                output.writestr(entry, data)

    with zipfile.ZipFile(args.output_xapk, "r") as result:
        if result.testzip() is not None:
            raise SystemExit("Output XAPK contains a damaged archive member.")
        final_manifest = json.loads(result.read("manifest.json"))
        actual_size = sum(len(result.read(row["file"])) for row in final_manifest["split_apks"])
        if (final_manifest.get("version_code") != str(args.version_code)
                or final_manifest.get("version_name") != args.version_name
                or final_manifest.get("total_size") != actual_size):
            raise SystemExit("Output XAPK metadata or total size is incorrect.")
    print(f"Built local XAPK {args.output_xapk} ({args.output_xapk.stat().st_size:,} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
