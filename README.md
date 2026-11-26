# Artemiz Android builds

- `release/CherryTree-Modded.xapk` — ARM64 build.
- `release/Artemiz-Android32.xapk` — ARMv7 32-bit build.

Both XAPK bundles contain the Artemiz base APK and matching split APKs. Use an installer that supports XAPK packages.

## Weather and combat updates

Each three-minute slot randomly selects an event and a stable 2×–50× multiplier; the selection remains fixed for that slot. Events last two minutes, followed by one calm minute. Astral Surge boosts XP across all skills; Verdant Bloom speeds crop growth and multiplies normal and Golden Sunflower harvests; Fortune Squall boosts monster drop chance, capped at 100%; Harvest Tide boosts Fishing, Forestry, and Mining yields; Artisan Ember boosts Cooking, Crafting, Firemaking, Fletching, and Alchemy outputs; Shadowveil boosts Thieving loot; Explorer's Aurora boosts Discovery rewards; Warfront boosts combat damage and attack speed while reducing incoming damage by up to 95%.

The Weather tab and detail panel use the game's `contentbox` surface, native colors and Linotte fonts. The panel shows the current event icon, effect, remaining time and a live progress bar, with a short entrance and icon animation. Calm weather has its own icon and hides the random next-event forecast. In-game notices appear when an event starts or ends; the game stays quiet on first attach and does not replay transitions from time spent closed.

## Discord weather commands

The optional [weather bridge](weather-bridge/README.md) runs on the PC and syncs `/weather start`, `/weather status`, and `/weather stop` with the modded Android client over the private Wi-Fi. The current TECNO install is prefilled with this PC's address (`http://192.168.0.106:8765`); change it under **Mod Menu → Etc. → Discord Weather Relay** if the PC gets a different address. The Discord bot token is stored only in the bridge's ignored local `.env` file. Setup requires the bot token and Discord server ID; the bridge also restricts commands to one configured Discord user. The phone-facing endpoint is read-only and does not connect to or modify the game's backend.

Combat loot stays accumulated while the selected monster name and area remain the same, and resets when the target changes. The battle controls dispatch by their actual view IDs: Collect Loot calls the game's loot-claim handler, while Leave stops the combat timer before collecting drops and returning to the battle list. This avoids the shared click-selector sending either button to an unrelated Slayer action.

## Latest update — 2026-11-26

- Anti-AFK now applies Android's keep-screen-on window flag while the game is open. There is no menu toggle.
- Added the Discord weather bridge and Android relay client. Remote events set the active weather and multiplier, expire automatically, and return to the normal cycle when stopped.
- The Baby Red Dragon battle card is explicitly grouped under Dragon Island.
- Skill thresholds preserve the original values through level 140, then rise by a fixed 0.5% of the level-140 XP step for each later level. The shared curve reaches level 500 at 110,882,942,772 total XP.
- The Base Camp reward view now shows the game's real reward descriptions in a scrollable list, with claimed, next and locked states plus progress. It does not invent or change reward effects.
- The new [CHANGELOG.md](CHANGELOG.md) records the current and prior mod features.
- The updated ARMv7 package was installed with `adb install-multiple -r` on the connected TECNO KE5 (version `OB659.181126.2835`). The game was not launched.
- The Discord bot still needs its local `.env` token and server ID before slash commands can connect; credentials must not be shared in chat or committed.

## SHA-256

- `CherryTree-Modded.xapk`: `14B8491AA5D4944AE15272279825F750E0EBE48928078EB328A9B86B0AF63A36`
- `Artemiz-Android32.xapk`: `6AA58040E34605EDFC3C05783156B1BDC6603C901E9F8C1F49420D2E0EE0FF0C`
