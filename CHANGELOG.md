# Changelog

## 2026-11-27

### v675 Cultivation navigation and breakthrough controls

- Removed the extra `‹ Menu` row from Cultivation so the game keeps its original hamburger navigation.
- Tapping the original hamburger closes the Cultivation panel before the original game menu opens.
- Kept the breakthrough control pressable until cultivation is complete; tapping it without enough Qi shows the existing explanation instead of appearing unresponsive.
- Built and signature-checked the v675 base APK and all split APKs for ARM64 and ARMv7. Generated direct patch-only updates from v664 through v674. APK/XAPK bundles stay local; the game was not installed or launched.


### v674 Proceed to Game crash fix

- Restored `CultivationMigration` to the combined second DEX. `MainActivity` invokes this class during startup, and v673 had omitted it when compiling the mod classes.
- Kept the game DEX at the known-good v671 build and retained the newline-separated What's New bullets.
- Built direct patch-only updates from v664 through v673 for ARM64 and ARMv7. Full APK/XAPK bundles remain local; no device was connected for runtime verification.

### v673 Game entry and release notes

- Preserved the known-good game DEX while rebuilding the v673 resource table, and isolated optional worker initialization so worker errors do not take down game entry.
- Changed the What's New separators to Android newline escapes; the compiled resource now contains a separate line break before each bullet.
- Built direct patch-only updates from v664 through v672 for ARM64 and ARMv7. Full APK/XAPK bundles remain local; no device was connected for runtime verification.

### v672 Inn workers and activity updates

- Added an Inn panel with three hire offers that refresh every five minutes. Workers have rarity, traits, levels, and one active assignment at a time.
- Workers automate Farming through the game’s normal planting and harvesting paths, so regular seeds, inventory, and Farming XP apply. Worker automation continues offline for up to eight hours and uses no special farming item.
- Moved Cultivation into a full native game panel that keeps the game’s top, side, and bottom navigation visible.
- Collect all accumulated battle drops through the game’s normal collection flow when the player dies.
- Removed Safecracking loot and skill-stat webhooks. Achievement Activity Log entries remain individual plain-text messages without Discord mentions.
- Added generated Inn artwork and a 12-worker portrait atlas.
- Built direct patch-only updates from v664 through v671 for ARM64 and ARMv7. Full APK/XAPK bundles remain local; the game was not installed.

### v671 Cultivation panel layout

- Replaced the full-screen Cultivation overlay with a centered, bounded panel matching the game’s other panels. The side navigation and top/bottom HUD stay visible behind it.
- The panel uses the game content-box styling and closes when tapped outside.
- Published patch-only direct updates from v664 through v670 for ARM64 and ARMv7.

### v670 cultivation screen and progression

- Replaced the Cultivation dialog with a full-screen in-game panel and added generated cultivation icon and static meditation art.
- Qi now accrues automatically, including up to 8 hours offline; gain increases by 5% after each successful breakthrough. Breakthrough attempts remain manual.
- Added the Mortal Realm baseline and major breakthrough path into Qi Gathering Stage I–X and the remaining realms. Existing v669 cultivation saves are migrated once to preserve their displayed progress.
- Kept the existing breakthrough costs, failure chance and Qi loss, plus the +20% Attack and Max Health ceilings.
- Built patch-only direct updates from v664 through v669 for ARM64 and ARMv7; full APK/XAPK artifacts remain local.

### v669 navigation, Wishing Well, and Cultivation

- Moved the live weather icon out of the skill XP bar and into Navigation immediately above My Profile. The row opens the existing weather details panel.
- Audited Wishing Well caps and stat lookups: Normal/Beginner wish levels are capped at 500 and Endgame levels at 1,000; bonus formulas read the stored wish level without an extra lower cap. Existing explicit effect limits, such as minimum action intervals and the Thieving success ceiling, remain in force.
- Added local Cultivation with 10 realms and 10 stages per realm. It gathers one Qi per second, continues offline for up to 8 hours, and uses a 70% breakthrough chance that increases by 5 percentage points per failure. Failed attempts remove 20% of stored Qi.
- Cultivation milestones add 0.2% Attack and Max Health each, capped at +20% apiece. Attack is included before temporary Warfront weather scaling, and the Max Health bonus is reapplied after the game recalculates the base maximum.
- Updated the welcome screen and release manifest to v669. Generated direct patch updates from v664 through v668 for ARM64 and ARMv7. Published only patch ZIPs; APK/XAPK build artifacts remain local.

## 2026-11-27

### v668 welcome screen update

- Updated both welcome-screen version labels to v668.
- Fixed the What's New list so bullets display on separate lines.
- Published direct update patches for v664, v665, v666, and v667 on ARM64 and ARMv7. No APK/XAPK was published.

### v667 patch hotfix

- Rebuilt all ARM64 and ARMv7 configuration splits with version code 667 and the matching signing certificate. This fixes the `INSTALL_FAILED_INVALID_APK` error caused by stale version 666 split manifests.
- Replaced the six v664/v665/v666-to-v667 patch archives and checksums. The existing updater downloads the corrected archive again on retry; no APK/XAPK was published.
- Added package assembly checks so future XAPK builds reject a split with mismatched package, version, or split ID.

### v667 UI and progression update

- Moved the weather icon from the currency strip to the shared skill level and XP bar, and updated binding to follow the visible skill screen.
- Raised every Normal/Beginner Wishing Well cap to level 500 and every Endgame Wishing Well cap to level 1,000.
- Removed Reddit from the social chooser and hid the Community Hub and Referrals entry points.
- Simplified the announcement heading and replaced the What's New hyphen list with clean bullet lines.
- Generated direct patch updates from supported v664, v665, and v666 installs for both ARM architectures. No APK/XAPK was pushed, and the game was not installed or launched.

### v666 in-game update and announcement overlays

- Added an animated Artemiz update panel for launch checks, patch download progress, patch application, and update results instead of Android progress or alert dialogs.
- Replaced Discord admin announcement dialogs with a compact dark glass-line card using the game's gold palette. It animates into the current game screen and fades away after a short display.
- Left Android's required package-install confirmation flow intact; the update progress and status remain in-game.
- Published only checksum-verified patch ZIPs to GitHub. Full APK and XAPK build outputs stay local.
- Built ARM64 and ARMv7 patch targets from v664 and v665; no install or game launch was performed.

### v665 global events and community updates

- Weather event slots now align to UTC epoch time, so clients use the same rotating event and multiplier instead of seeding the cycle on each install.
- Fortune Squall now multiplies eligible loot quantities and battle drop chances for all categories covered by the shared reward paths.
- Added timed `/artemiz message send` and `/artemiz message clear` Discord commands. Connected clients receive one popup over the currently open game screen; announcements expire after the selected duration.
- Moved global weather controls under `/artemiz weather` and changed the in-game relay section to **Artemiz Game Commands**.
- Updated the community links to the new Discord invite and the owner's Facebook profile. Removed YouTube and TikTok from the social list.
- Updated the Welcome screen release notes and bumped the app to v665.
- Built the next update as an automatic patch for the v664 patch-enabled client. This build has not been installed or launched; runtime behavior remains for the user's testing.

## 2026-11-26

### v664 progression crash fix

- Removed the skill progress bar's exact `long`-to-`int` conversions. Thresholds above 2,147,483,647 XP now render on a bounded 0–10,000 progress scale while XP totals and threshold math remain 64-bit.
- Built and signature-checked ARM64 and ARMv7 v664 XAPK bundles; the base APK and all four splits report version code 664 and align correctly.
- Published v664 in the update manifest for launch-time update checking.

### Patch-only updater bootstrap

- Added a patch client that selects a BSDIFF delta by installed version and device ABI, verifies the patch and every reconstructed APK, and submits the complete split set through Android PackageInstaller.
- Added the release-side patch generator, checksum-pinned DEX build script, and third-party notices. Full release steps and the required manifest delta format are in [update-client/README.md](update-client/README.md).
- Built the patch client into a v664 bootstrap using the same signing key and existing game resources/splits. Kept version code 664 so it can update the existing v664 install without a data reset.
- Installed the bootstrap over the connected ARMv7 device with `adb install-multiple -r`. Package Manager reports v664 with the base, ARMv7, English, and mdpi splits. The game was not launched.
- The current public manifest remains at v664. A future release must include an exact `664` delta for ARMv7, generated from the matching patch-enabled bootstrap XAPK. Android 10 still requires its system update confirmation screen.

### Continue-to-Game crash fix

- Fixed the `NoClassDefFoundError` for `ModMenu`: the first patch bootstrap had overwritten the existing second DEX with updater-only classes. The rebuilt `classes2.dex` now contains both `ModMenu` and the patch updater.
- Fixed ART's `VerifyError` in the safe-crack success handler by explicitly setting its safe-count increment to a wide `1` after the code path merge.
- Reassembled the main DEX, signed the base with the installed certificate, regenerated both v664 bootstrap XAPKs, and installed the complete ARMv7 split set over the existing app. ADB returned `Success`; the game was not launched.

### v663 follow-up

- Removed the shared 2,000,000,000 XP ceiling. Every skill now uses the final threshold in the extended level-500 curve (110,882,942,772 XP), including its skill-card display path.
- Weather notices now compare event identity and actual admin start/end times. A 50×, 120-minute admin event no longer triggers false end/start popups when the separate three-minute local weather slot changes; actual event replacement posts one concise change notice.
- The startup update check now completes before **Continue to Game** is enabled. The Home-screen manual update button and its callback were removed. Failed/offline checks release Continue.
- Updated the Welcome screen's What's New and release metadata to v663.
- Built ARM64 and ARMv7 v663 XAPKs with the existing signing key, verified the base signature and package alignment, and installed the ARMv7 splits in place without launching the game.

### Device and interface

- Completely removed daily Safecracking usage: opening and cracking safes no longer increments the daily tally, neither safe-opening nor lock-breaking checks a cap, and the remaining-safes labels are hidden.
- Fixed the Continue to Game crash by preserving the wide register type in the safe-cracking flow.
- Moved the manual update control to the game Home screen. Startup checks the public update manifest, and Home → Check for Updates can check again. It downloads the matching split XAPK, verifies its SHA-256, and asks Android's Package Installer to update the existing app.
- Replaced the Welcome screen's stale What's New text with the v662 release notes.
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
- Installed the ARMv7 v662 package with `adb install-multiple -r` on the connected TECNO KE5. Package Manager reports version `OB662.261126.0` and the expected base, ARMv7, English, and mdpi splits.
- The game was not launched; runtime behavior of the new UI and progression changes has not been exercised in-game.

## Earlier updates

- Rebranded the app as Artemiz and added the custom game icon and square draggable floating menu icon.
- Added the Combat, Auto, Etc., and Weather controls, with solid in-game themed menu surfaces and automatic dismissal on outside touch.
- Added safe-cracking, Discovery, Area Exploration, and Slayer automation using existing game controls and reward paths.
- Added weather icons and effects, safe loot and skill-stat reporting, Activity Log messages, and the local redeem-code reward.
- Fixed previous launch, attack-speed refresh, loot accumulation, crop harvest, combat exit, Collect Loot, and enemy respawn issues.
