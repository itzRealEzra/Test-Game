# Patch updater release guide

The v664 bootstrap installed on the connected ARMv7 device can update from a small BSDIFF patch archive. The client downloads only that archive, verifies the archive and every reconstructed APK, then submits the full base-plus-split set to Android PackageInstaller. It does not fetch or unpack a full XAPK.

## Build a patch for a release

Use the exact local source XAPK and signed target XAPK for each supported installed version. Source and target XAPKs are build inputs only and stay outside GitHub. The v664 patch-enabled bootstrap artifacts are kept in `F:\Cherry Tree RPG Mod\modded\weather-update\build-work\update-v664-patch-client\`; the v665 source XAPKs are in `build-work\update-v665\`.

- ARM64 source: `CherryTree-Modded-v664-patch.xapk`
- ARMv7 source: `Artemiz-Android32-v664-patch.xapk`

Build target XAPKs locally with a higher version code and the same signing certificate as the installed package. Keep the same split IDs and APK filenames. Generate a delta for every supported source version and ABI. For v666, the release contains v664-to-v666 and v665-to-v666 archives for both architectures. The commands below show the ARMv7 v664 delta; repeat with the ARM64 and v665 inputs using their matching filenames:

```powershell
python -m pip install -r update-client/requirements.txt

python update-client/make_update_patch.py `
  --from-xapk "..\build-work\update-v664-patch-client\Artemiz-Android32-v664-patch.xapk" `
  --to-xapk "..\build-work\update-v666\Artemiz-Android32-v666.xapk" `
  --output "release\Artemiz-Android32-v664-to-v666.patch.zip" `
  --url "https://raw.githubusercontent.com/itzRealEzra/Test-Game/main/release/Artemiz-Android32-v664-to-v666.patch.zip"
```

The generator prints the SHA-256 and the `deltas` JSON object for each archive. Publish the patch archives at those HTTPS URLs and put the matching delta under each ABI in `update.json`. For a patch-only repository, omit full XAPK `url` and `sha256` fields:

```json
{
  "version_code": 666,
  "version_name": "OB666.261127.0",
  "armv7": {
    "deltas": {
      "664": {
        "from_version_code": 664,
        "to_version_code": 666,
        "url": "https://example.invalid/Artemiz-Android32-v664-to-v666.patch.zip",
        "sha256": "<patch-archive-sha256>",
        "size_bytes": 123456
      }
    }
  }
}
```

The patch-enabled client uses only the delta matching its installed version and ABI. If no exact delta exists, it will not fall back to a full XAPK; the update check fails safely and the player can continue on the installed version. Do not add APK or XAPK build outputs to GitHub when publishing a patch-only release.

## Installation behavior

The patch download and APK reconstruction are automatic after the launch-time manifest check finds a matching update. Android can still show its installer confirmation. Android 10 does not support `SessionParams.setRequireUserAction`; on Android 12 and later, user-action-free installs depend on the documented installer, target SDK, permission, and ownership requirements. The client handles Android's pending-user-action result by launching the system confirmation screen.

Install the patch-enabled v664 bootstrap over an existing install with `adb install-multiple -r` and its matching four APKs. That preserves app data. Do not launch the app as part of installation. The connected device now has version code 664 with the patch client; the next release should therefore include a `664` delta for ARMv7. A release built from a different base/split set will be rejected by the source APK hash checks.

## Client implementation

- `GameUpdater.java` selects the exact ABI/version delta, validates the manifest, archive, source APK hashes and reconstructed output hashes, then stages all splits in a single PackageInstaller session.
- `GameUpdateOverlay.java` draws the update check, progress, and result card inside the game activity. Android's package installer can still show its required confirmation screen.
- `BsdiffPatch.java` applies BSDIFF40 patches with bounded output and bzip2 decoding.
- `make_update_patch.py` generates the patch ZIP and prints the manifest fragment.
- `inject_classes2.py` and `package_xapk.py` prepare local build inputs; generated APKs and XAPKs remain outside GitHub.
- `build_updater_dex.ps1` compiles the updater and game-mod classes into one DEX using a fresh class directory; Maven dependencies are downloaded with SHA-256 verification and are not committed.

### Rebuild the patch-enabled bootstrap

The game's existing second DEX contains `ModMenu` as well as the previous updater. **Do not replace it with an updater-only DEX**: game classes call `ModMenu` during startup, and the app will crash if that class disappears. The build script requires the compiled game-mod classes directory and merges those classes with the new updater into one DEX:

```powershell
& "update-client\build_updater_dex.ps1" `
  -GameModClassesDir "..\..\classes-weather" `
  -OutputDex "..\build-work\update-v664-patch-client\classes2.dex"
```

When changing the decoded game smali, rebuild `classes.dex` with `AssembleSmali.java` and Apktool's bundled smali libraries, then place that DEX in the unsigned base APK. This avoids rebuilding resources and keeps the previously built resource table intact. From the repository root, the current local build uses:

```powershell
$helperOut = "..\build-work\smali-assembler"
javac -cp "C:\AndroidTools\apktool\apktool.jar" -d $helperOut "update-client\AssembleSmali.java"
java -Xmx2g -cp ("C:\AndroidTools\apktool\apktool.jar;" + $helperOut) `
  AssembleSmali "..\..\base-src\smali" `
  "..\build-work\update-v664-patch-client\classes.dex" 29
```

The v664 crash fix adds a wide `1` before the successful-safe statistic update in `e9.h()`; the method reused that register as an integer earlier, which ART rejected on the user's Android 10 device.

After rebuilding the main DEX, inject the combined ModMenu/updater DEX as `classes2.dex`, align and sign the base with the installed app's key, and rebuild both bootstrap XAPKs. The XAPKs now contain the same corrected base APK and their matching ABI/resource splits. Use the corrected XAPK as the `--from-xapk` source for future deltas.
