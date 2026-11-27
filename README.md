# Artemiz Android updates

Full XAPK and APK build outputs stay local. GitHub publishes `update.json` and the small, checksum-verified patch archives only. The current patch release supports patch-enabled installs on versions 664 through 670 for ARM64 and ARMv7.

## Local-only saves

Progress continues to save on the device. Google Play cloud-save restore is disabled, and the sign-in, cloud-save, and Grove online-account controls are hidden. Saves do not sync between devices.

## Online in-place updates

Artemiz checks the public `update.json` manifest on every launch and keeps **Continue to Game** disabled until the check finishes. When a newer release has a matching patch for the installed version and device ABI, the updater automatically downloads that small patch archive, checks its SHA-256, reconstructs and verifies the base and split APKs, then stages them together through Android's Package Installer. It does not download the full XAPK. Offline or failed checks release Continue. See [the patch release guide](update-client/README.md) for preparing each ABI's delta.

The patch client accepts only a delta matching the installed version and ABI. Updates must use a higher version code, reconstruct every APK split, and use the same signing key as the installed build. The patch archives reconstruct the APK set on the device; no full APK or XAPK is published in this repository.

The patch download and APK reconstruction happen automatically. Android can still require the user to allow Artemiz to request installs and confirm the update. This device runs Android 10, where PackageInstaller requires that confirmation; newer Android versions can allow user-action-free updates only when the platform's eligibility requirements are met. [Android's PackageInstaller API documents those conditions](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)).

The manifest and patch archives are served from this repository's public main branch. Keep the repository public while clients use these URLs; if you make it private, move `update.json` and the patch archives to a public host such as Cloudflare R2 and change the built-in manifest URL. Android may still show its own confirmation when installing the rebuilt package; the download and patch progress display inside Artemiz.

## Weather and combat updates

Each three-minute UTC slot selects the same event and stable 2×–50× multiplier for all clients. Events last two minutes, followed by one calm minute. Astral Surge boosts XP across all skills; Verdant Bloom speeds crop growth and multiplies normal and Golden Sunflower harvests; Fortune Squall boosts eligible loot quantities across skill categories and monster drop chance (capped at 100%); Harvest Tide boosts Fishing, Forestry, and Mining yields; Artisan Ember boosts Cooking, Crafting, Firemaking, Fletching, and Alchemy outputs; Shadowveil boosts Thieving loot; Explorer's Aurora boosts Discovery rewards; Warfront boosts combat damage and attack speed while reducing incoming damage by up to 95%.

The Weather tab and detail panel use the game's `contentbox` surface, native colors and Linotte fonts. The panel shows the current event icon, effect, remaining time and a live progress bar, with a short entrance and icon animation. Calm weather has its own icon and hides the random next-event forecast. In-game notices follow the active event identity, so a long admin event is not restarted or reported as ended when the local random cycle rolls over. The game stays quiet on first attach and does not replay transitions from time spent closed.

## Artemiz Discord commands

The [Cloudflare Worker](weather-bridge/cloudflare/README.md) URL is built into the Android client, so Discord weather and announcement commands work while the PC is off. The app also migrates the old default PC relay address on upgrade. If you deploy a Worker under a different URL, change the address in **Mod Menu → Etc. → Artemiz Game Commands**. Use `/artemiz weather ...` for global weather and `/artemiz message send|clear` for in-game popup messages. The Worker verifies Discord signatures and does not need a bot token at runtime; the phone-facing endpoint is read-only and does not connect to or modify the game's backend. The optional [local weather bridge](weather-bridge/README.md) remains available for PC-hosted testing.

Combat loot stays accumulated while the selected monster name and area remain the same, and resets when the target changes. The battle controls dispatch by their actual view IDs: Collect Loot calls the game's loot-claim handler, while Leave stops the combat timer before collecting drops and returning to the battle list. This avoids the shared click-selector sending either button to an unrelated Slayer action.

## Latest update — 2026-11-27 (v668)

- Updated the welcome screen to show v668 in both version labels.
- Fixed What's New so each bullet starts on its own line instead of running into the previous item.
- Published direct v664, v665, v666, and v667 patch paths for ARM64 and ARMv7. No APK or XAPK was published.

## Previous update — 2026-11-27 (v667)

- Moved the weather icon beside the skill level and XP bar; increased Normal Wishing Well caps to 500 and Endgame caps to 1,000.
- Removed Reddit, Community Hub, and Referrals from the menus, and cleaned up the announcement heading and What's New list.
- Rebuilt the Android split APKs with matching version codes after the earlier install failure.

## Previous update — 2026-11-27 (v666)

- Replaced Android update dialogs with an animated in-game update panel for checking, downloading, verifying, and preparing patches.
- Replaced the admin announcement dialog with a game-styled glass-line card that slides in and fades out after a short display.
- Published patch archives only. No APK or XAPK files are included in the GitHub update.

## Previous update — 2026-11-27 (v665)

- Weather events now use the same three-minute UTC schedule on every client, with Fortune Squall boosting loot quantity across categories and monster drop chance.
- Added global `/artemiz weather` controls and timed `/artemiz message send|clear` commands. Game announcements appear over the player's current screen and expire automatically.
- Updated the social links: new Discord invite, owner's Facebook profile, and no YouTube or TikTok entries.
- Built v665 as an automatic update patch for the v664 patch-enabled bootstrap. The APK has not been installed or launched; please test the patch through the in-game updater.

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
