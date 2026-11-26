#!/usr/bin/env python3
"""Add the combined mod-menu/updater DEX to an unsigned base APK."""

import argparse
import zipfile
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, type=Path, help="Unsigned base APK")
    parser.add_argument("--dex", required=True, type=Path, help="Combined classes2.dex")
    parser.add_argument("--output", required=True, type=Path, help="Output unsigned APK")
    args = parser.parse_args()

    if not args.base.is_file() or not args.dex.is_file():
        raise SystemExit("Base APK and DEX must both exist.")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.base, "r") as source:
        if source.testzip() is not None:
            raise SystemExit("Base APK archive is damaged.")
        if "classes2.dex" in source.namelist():
            raise SystemExit("Base APK already contains classes2.dex.")
        with zipfile.ZipFile(args.output, "w", compression=zipfile.ZIP_DEFLATED,
                             compresslevel=9) as target:
            for entry in source.infolist():
                target.writestr(entry, source.read(entry.filename))
            target.write(args.dex, "classes2.dex", compress_type=zipfile.ZIP_DEFLATED,
                         compresslevel=9)

    with zipfile.ZipFile(args.output, "r") as result:
        if result.testzip() is not None or "classes2.dex" not in result.namelist():
            raise SystemExit("Injected APK failed its archive check.")
    print(f"Added {args.dex.stat().st_size:,} bytes of classes2.dex to {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
