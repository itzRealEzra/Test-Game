# Changelog

## 2026-11-26

### Device and interface

- Completely removed daily Safecracking usage: opening and cracking safes no longer increments the daily tally, neither safe-opening nor lock-breaking checks a cap, and the remaining-safes labels are hidden.
- Added an online Artemiz updater in v660 and published the v661 follow-up. Startup checks a version manifest; Mod Menu → Etc. → Check for Updates can check again. It downloads the matching split XAPK, verifies its SHA-256, and asks Android's Package Installer to update the existing app.
- Switched to local-only saves: disabled automatic Google Play cloud-save restore and hid sign-in/cloud-save entry points plus the Grove online-account panel. Local character creation and on-device saves remain available.
- Added the local Discord weather bridge and a Cloudflare Worker deployment option. The Worker handles signed `/weather start`, `/weather status`, and `/weather stop` interactions, stores state in a Durable Object, and serves a read-only HTTPS endpoint so weather commands can work while the PC is off.
- Built the configured Cloudflare Worker URL into the Android client by default. Upgrades migrate the prior default PC relay URL, while custom relay URLs are preserved.
- Added automatic keep-screen-on behavior while the game activity is open. It has no toggle.
- Reworked the Weather panel to use the game's `contentbox` background, native colors, and Linotte fonts. It now shows an event icon, effect, live time remaining, progress bar, and opening/pulse animations. Calm weather continues to hide the next-event forecast.
- Rebuilt the Base Camp reward list as a scrollable, progress-aware view with claimed, next, and locked labels, using the original in-game reward descriptions.
- Corrected Battle grouping so Baby Red Dragon is placed in Dragon Island.
- Retained the square, borderless, draggable Artemiz floating icon and the Combat, Auto, Etc., and Weather menu tabs.

### Progression and events

- Reworked the shared XP threshold curve used by all skills. Original thresholds through level 140 are unchanged. After level 140, the XP step grows by a fixed 0.5% of the level-140 step per level; level 500 requires 110,882,942,772 total XP.
- Retained the level-500 skill cap and the existing per-action XP reward paths.
- Kept the randomized three-minute weather cycle: each selected event is active for two minutes and is followed by one calm minute. The 2×–50× event roll stays stable for its slot.
- Weather effects cover all-skill XP, Farming growth and harvests, monster drop chance, Fishing/Forestry/Mining output, Cooking/Crafting/Firemaking/Fletching/Alchemy output, Thieving, Discovery, and combat damage/attack speed/incoming damage.
- Kept in-game start/end weather notifications and the Calm-specific icon.

### Combat and automation

- Retained attack power and attack speed multipliers, God Mode, and instant enemy respawn through the selected target's normal spawn path.
- Kept combat Leave and Collect Loot routed by their view IDs, preventing the Slayer prompt from replacing the normal battle exit or loot claim.
- Kept cumulative loot visible for repeated kills of the same target and reset it when the target changes.
- Added/retained Auto Safe Crack: automatically presses Break Lock and generates the next safe; safe cracking now has no daily cap.
- Added/retained Auto Discovery fast-taps unrevealed tiles and Auto Exploration follows the current route through normal direction controls; both remain independent.
- Combined task assignment, kill completion, and task repeat into one Auto Slayer control.
- Added/retained one-click selling for unlocked burnt food and maximum-batch cooking for unlocked recipes with available ingredients.

### Reports and account tools

- Kept the text-only Safecracking loot report, which edits one message and resets its totals when the safe area changes.
- Kept the aligned all-skill level/XP report as a single edited message, and the Activity Log as one message per new game entry with the configured mention.
- Kept the one-time `AdminEzra!!` local redeem reward of 500,000,000 Gold Coins.

### Build and installation

- Rebuilt and signature-verified the base APK and both XAPK bundles. XAPK archive integrity and manifest size checks passed.
- Installed the ARMv7 v661 package with `adb install-multiple -r` on the connected TECNO KE5. Package Manager reports version `OB661.261126.0` and the expected base, ARMv7, English, and mdpi splits.
- The game was not launched; runtime behavior of the new UI and progression changes has not been exercised in-game.

## Earlier updates

- Rebranded the app as Artemiz and added the custom game icon and square draggable floating menu icon.
- Added the Combat, Auto, Etc., and Weather controls, with solid in-game themed menu surfaces and automatic dismissal on outside touch.
- Added safe-cracking, Discovery, Area Exploration, and Slayer automation using existing game controls and reward paths.
- Added weather icons and effects, safe loot and skill-stat reporting, Activity Log messages, and the local redeem-code reward.
- Fixed previous launch, attack-speed refresh, loot accumulation, crop harvest, combat exit, Collect Loot, and enemy respawn issues.
