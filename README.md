# Artemiz Android builds

- `release/CherryTree-Modded.xapk` — ARM64 build.
- `release/Artemiz-Android32.xapk` — ARMv7 32-bit build.

Both XAPK bundles contain the Artemiz base APK and matching split APKs. Use an installer that supports XAPK packages.

## Weather and combat updates

Each three-minute slot randomly selects an event and a stable 2×–50× multiplier; the selection remains fixed for that slot. Events last two minutes, followed by one calm minute. Astral Surge boosts XP across all skills; Verdant Bloom speeds crop growth and multiplies normal and Golden Sunflower harvests; Fortune Squall boosts monster drop chance, capped at 100%; Harvest Tide boosts Fishing, Forestry, and Mining yields; Artisan Ember boosts Cooking, Crafting, Firemaking, Fletching, and Alchemy outputs; Shadowveil boosts Thieving loot; Explorer's Aurora boosts Discovery rewards; Warfront boosts combat damage and attack speed while reducing incoming damage by up to 95%.

The Weather tab and detail panel use the game's `contentbox` surface, native colors and Linotte fonts. The panel shows the current event icon, effect, remaining time and a live progress bar, with a short entrance and icon animation. Calm weather has its own icon and hides the random next-event forecast. In-game notices appear when an event starts or ends; the game stays quiet on first attach and does not replay transitions from time spent closed.

## Discord weather commands

The [Cloudflare Worker](weather-bridge/cloudflare/README.md) URL is built into the Android client, so Discord weather commands work while the PC is off. The app also migrates the old default PC relay address on upgrade. If you deploy a Worker under a different URL, change the address in **Mod Menu → Etc. → Discord Weather Relay**. The Worker verifies Discord signatures and does not need a bot token; the phone-facing endpoint is read-only and does not connect to or modify the game's backend. The optional [local weather bridge](weather-bridge/README.md) remains available for PC-hosted testing.

Combat loot stays accumulated while the selected monster name and area remain the same, and resets when the target changes. The battle controls dispatch by their actual view IDs: Collect Loot calls the game's loot-claim handler, while Leave stops the combat timer before collecting drops and returning to the battle list. This avoids the shared click-selector sending either button to an unrelated Slayer action.

## Latest update — 2026-11-26

- Anti-AFK now applies Android's keep-screen-on window flag while the game is open. There is no menu toggle.
- Added the Discord weather bridge and Android relay client. Remote events set the active weather and multiplier, expire automatically, and return to the normal cycle when stopped.
- Built the deployed Cloudflare Worker URL into the Android client and migrated the old default PC relay URL on upgrade, so no manual relay URL entry is needed for the configured Worker.
- The Baby Red Dragon battle card is explicitly grouped under Dragon Island.
- Skill thresholds preserve the original values through level 140, then rise by a fixed 0.5% of the level-140 XP step for each later level. The shared curve reaches level 500 at 110,882,942,772 total XP.
- The Base Camp reward view now shows the game's real reward descriptions in a scrollable list, with claimed, next and locked states plus progress. It does not invent or change reward effects.
- The new [CHANGELOG.md](CHANGELOG.md) records the current and prior mod features.
- The updated ARMv7 package was installed with `adb install-multiple -r` on the connected TECNO KE5 (version `OB659.181126.2835`). The game was not launched.
- The Discord bot still needs its local `.env` token and server ID before slash commands can connect; credentials must not be shared in chat or committed.

## SHA-256

- `CherryTree-Modded.xapk`: `8D503FD6A3609A931F8F65C89F6BAED4F9F543FC8D37B75EA35870335ADBA5E7`
- `Artemiz-Android32.xapk`: `126FB7D0467F3208C4CF173722FFC44F2B79E62671B5DACFBCF72BBC46C9A638`
