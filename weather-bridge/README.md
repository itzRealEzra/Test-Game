# Discord weather bridge

This small Windows-hosted service connects Discord slash commands to the modded Android client while both devices are on the same private Wi-Fi. The bot connects outbound to Discord; no public server, paid host, or router port-forward is needed. For a relay that keeps working when this PC is off, see the [Cloudflare deployment](cloudflare/README.md).

The relay exposes only a read-only weather-state endpoint to private-network clients. Weather changes can only be issued by the Discord bot, which checks both the configured server ID and one allowed Discord user ID. The Discord bot token stays on the PC in `.env` and is never placed in the APK.

## One-time Discord setup

1. In the Discord Developer Portal, create an application and add a bot. Copy its bot token locally; do not post it in chat or commit it.
2. Enable Developer Mode in Discord, then copy the target server ID and your own user ID.
3. Invite the bot to that server with the `bot` and `applications.commands` OAuth scopes. It needs no channel permissions beyond seeing the server and registering slash commands.
4. Run `run-weather-relay.ps1` once. It creates `.env` from the template and installs the Python dependency into a local virtual environment.
5. Edit `.env` and set `DISCORD_BOT_TOKEN`, `DISCORD_GUILD_ID`, and `DISCORD_ALLOWED_USER_ID`. Restart `run-weather-relay.ps1`.
6. If Windows Firewall asks, allow Python on **Private networks only**. If it does not prompt and the phone cannot connect, run `setup-private-firewall.ps1` once from an elevated PowerShell window. Keep the PC and phone on the same private Wi-Fi; do not forward port 8765 on your router.

The script prints the PC address, for example `http://192.168.1.20:8765`. In the game, open **Mod Menu → Etc. → Artemiz Game Commands** and paste the printed address. The game polls the relay while it is open. The relay status below the field changes to “Connected” after the first successful request.

## Discord commands

- `/artemiz weather start` — choose one of the eight events, a multiplier from 2× to 50×, and a duration from 1 to 120 minutes. The event is synchronized for clients connected to the relay.
- `/artemiz weather status` — show the active admin event and remaining time.
- `/artemiz weather stop` — end the admin event and return to the shared event schedule.
- `/artemiz message send` — show a message over the current game screen for connected clients. It expires after 5 minutes by default; set 1–120 minutes if needed.
- `/artemiz message clear` — clear the active game message.

Commands are registered only in `DISCORD_GUILD_ID` and only `DISCORD_ALLOWED_USER_ID` can use them. Weather and announcement state survive a bot restart and expire at their configured times.

## Scope

The shared event schedule and admin override affect clients running this mod and connected to the relay. The relay is intentionally read-only from the phone; there is no HTTP command endpoint.
