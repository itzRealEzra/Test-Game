# Artemiz Android builds

- `release/CherryTree-Modded.xapk` — ARM64 build.
- `release/Artemiz-Android32.xapk` — ARMv7 32-bit build.

Both XAPK bundles contain the Artemiz base APK and matching split APKs. Use an installer that supports XAPK packages.

## Local-only saves

Progress continues to save on the device. Google Play cloud-save restore is disabled, and the sign-in, cloud-save, and Grove online-account controls are hidden. Saves do not sync between devices.

## Online in-place updates

Artemiz checks the public update.json manifest on every launch and keeps **Continue to Game** disabled until the check finishes. If an update is available, it offers the update before gameplay. If the device is offline or the check fails, Continue becomes available. There is no in-game manual update button. The updater chooses the ARM64 or ARMv7 XAPK for the device, verifies the bundle's SHA-256, stages all APK splits together, and opens Android's installer. Android may first ask you to allow Artemiz to request installs; the system installer then asks you to confirm the update.

The updater first shipped in version 660. Version 660 is a one-time update for players on version 659; install it over the existing app with an XAPK installer or `adb install-multiple -r` to keep the local save. Version 664 and later are checked automatically at launch, before Continue is enabled. Updates must use a higher version code and the same signing key as the installed build.

The manifest and XAPKs are served from this repository's public main branch. Keep the repository public while clients use these URLs; if you make it private, move update.json and both XAPKs to a public host such as Cloudflare R2 and change the built-in manifest URL.

## Weather and combat updates

Each three-minute slot randomly selects an event and a stable 2×–50× multiplier; the selection remains fixed for that slot. Events last two minutes, followed by one calm minute. Astral Surge boosts XP across all skills; Verdant Bloom speeds crop growth and multiplies normal and Golden Sunflower harvests; Fortune Squall boosts monster drop chance, capped at 100%; Harvest Tide boosts Fishing, Forestry, and Mining yields; Artisan Ember boosts Cooking, Crafting, Firemaking, Fletching, and Alchemy outputs; Shadowveil boosts Thieving loot; Explorer's Aurora boosts Discovery rewards; Warfront boosts combat damage and attack speed while reducing incoming damage by up to 95%.

The Weather tab and detail panel use the game's `contentbox` surface, native colors and Linotte fonts. The panel shows the current event icon, effect, remaining time and a live progress bar, with a short entrance and icon animation. Calm weather has its own icon and hides the random next-event forecast. In-game notices follow the active event identity, so a long admin event is not restarted or reported as ended when the local random cycle rolls over. The game stays quiet on first attach and does not replay transitions from time spent closed.

## Discord weather commands

The [Cloudflare Worker](weather-bridge/cloudflare/README.md) URL is built into the Android client, so Discord weather commands work while the PC is off. The app also migrates the old default PC relay address on upgrade. If you deploy a Worker under a different URL, change the address in **Mod Menu → Etc. → Discord Weather Relay**. The Worker verifies Discord signatures and does not need a bot token; the phone-facing endpoint is read-only and does not connect to or modify the game's backend. The optional [local weather bridge](weather-bridge/README.md) remains available for PC-hosted testing.

Combat loot stays accumulated while the selected monster name and area remain the same, and resets when the target changes. The battle controls dispatch by their actual view IDs: Collect Loot calls the game's loot-claim handler, while Leave stops the combat timer before collecting drops and returning to the battle list. This avoids the shared click-selector sending either button to an unrelated Slayer action.

## Latest update — 2026-11-26 (v664)

- Fixed the skill XP bar crash above 2,147,483,647 XP. XP and thresholds stay 64-bit; the bar now calculates progress relative to the current level on a 0–10,000 scale.
- Published the signed ARM64 and ARMv7 v664 bundles and update manifest for the built-in launch checker. This build was not installed so the auto-update can be tested.

### v663 update

- Raised the shared skill XP cap to the level-500 threshold (110,882,942,772 XP) and removed the remaining 2,000,000,000 clamps from skill XP rendering.
- Fixed weather notices for long admin events: the two-hour event no longer produces false end/start notices at each three-minute local-cycle boundary, and a genuine event change uses one notice.
- Update checks now finish before Continue to Game is enabled on launch. The Home-screen Check for Updates button was removed; offline or failed checks still let the player continue.
- Installed and committed the v663 update without launching the game.

- Removed the daily safe cracking limit and stopped safe cracks from consuming the daily tally; hid the daily limit labels.
- Fixed the Continue to Game crash and added checksum-verified in-place updates through Android's Package Installer without clearing local app data.
- Replaced the stale Welcome What's New text with current v663 release notes.
- Disabled automatic Google Play cloud-save restore and hid its sign-in/backup entry points; local character creation and on-device saving remain available.
- Anti-AFK keeps the screen on while the game is open. There is no menu toggle.
- Added the Discord weather bridge and Android relay client. Remote events set the active weather and multiplier, expire automatically, and return to the normal cycle when stopped.
- Built the deployed Cloudflare Worker URL into the Android client and migrated the old default PC relay URL on upgrade, so no manual relay URL entry is needed for the configured Worker.
- The Baby Red Dragon battle card is explicitly grouped under Dragon Island.
- Skill thresholds preserve the original values through level 140, then rise by a fixed 0.5% of the level-140 XP step for each later level. The shared curve reaches level 500 at 110,882,942,772 total XP.
- The Base Camp reward view now shows the game's real reward descriptions in a scrollable list, with claimed, next and locked states plus progress. It does not invent or change reward effects.
- The new [CHANGELOG.md](CHANGELOG.md) records the current and prior mod features.
- The updated local-only ARMv7 package was installed with `adb install-multiple -r` on the connected TECNO KE5 (version `OB663.261126.0`). The game was not launched.
- The Discord bot still needs its local `.env` token and server ID before slash commands can connect; credentials must not be shared in chat or committed.

## SHA-256

- `CherryTree-Modded.xapk`: `9C45878646492D16C8FEE7E5B009E8F40F223E72A9E1D233B46BA29707741B53`
- `Artemiz-Android32.xapk`: `B68A92546041C1F6F142F6209273B49D0018950F6A8F952B05B02FB1DA34EC15`
