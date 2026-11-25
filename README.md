# Artemiz Android builds

- `release/CherryTree-Modded.xapk` — ARM64 build.
- `release/Artemiz-Android32.xapk` — ARMv7 32-bit build.

Both XAPK bundles contain the Artemiz base APK and matching split APKs. Use an installer that supports XAPK packages.

## Weather and combat updates

Each three-minute slot randomly selects an event and a stable 2×–50× multiplier; the selection remains fixed for that slot. Events last two minutes, followed by one calm minute. Astral Surge boosts XP across all skills; Verdant Bloom speeds crop growth and multiplies normal and Golden Sunflower harvests; Fortune Squall boosts monster drop chance, capped at 100%; Harvest Tide boosts Fishing, Forestry, and Mining yields; Artisan Ember boosts Cooking, Crafting, Firemaking, Fletching, and Alchemy outputs; Shadowveil boosts Thieving loot; Explorer's Aurora boosts Discovery rewards; Warfront boosts combat damage and attack speed while reducing incoming damage by up to 95%.

The Weather tab and rounded detail panel use a solid charcoal-and-gold style. Calm weather has its own icon and hides the random next-event forecast. In-game notices appear when an event starts or ends; the game stays quiet on first attach and does not replay transitions from time spent closed.

Combat loot stays accumulated while the selected monster name and area remain the same, and resets when the target changes. Collect Loot is wired to the game's loot-claim handler. Leave stops the combat timer before collecting accumulated drops and returning to the battle list, so collecting loot cannot keep a queued respawn alive.

## SHA-256

- `CherryTree-Modded.xapk`: `65E2AAF249416C9B3FB65F0C668E32E29DF87E10419B2557DAA993028FDE9CB9`
- `Artemiz-Android32.xapk`: `D3F62FAB64E93081FAAE4A27569C40A3ACA658A3DB10F06417EDE6A4F94CB35B`
