# Artemiz Android builds

- `release/CherryTree-Modded.xapk` — ARM64 build.
- `release/Artemiz-Android32.xapk` — ARMv7 32-bit build.

Both XAPK bundles contain the Artemiz base APK and matching split APKs. Use an installer that supports XAPK packages.

## Weather cycle

The local cycle has eight fixed three-minute slots. Each event lasts two minutes, followed by one calm minute. Every event rolls a stable 2×–50× multiplier for its slot: Astral Surge boosts XP across all 17 skills; Verdant Bloom speeds Farming growth and increases harvest yield; Fortune Squall boosts monster drop chance, capped at 100%; Harvest Tide boosts Fishing, Forestry, and Mining yields; Artisan Ember boosts Cooking, Crafting, Firemaking, Fletching, and Alchemy outputs; Shadowveil boosts Thieving loot; Explorer's Aurora boosts Discovery rewards; Warfront boosts combat damage and attack speed while reducing incoming damage (capped at 95%). Each event has its own in-game style icon.

Explorer's Aurora applies its multiplier to every item in a Discovery reward batch.

Combat respawn now waits for the defeated enemy's full tick to finish so its loot and rewards are recorded before the next target refreshes.

## SHA-256

- `CherryTree-Modded.xapk`: 2BC31A4B72739B3EE80A31C15CEB1157366AC7502B6A098E07AE7D1E0E62A7EC
- `Artemiz-Android32.xapk`: 14ACF9A2D954B4CE2AD314D7397591C15E2C5A251C373051C019453C5F7DF346
