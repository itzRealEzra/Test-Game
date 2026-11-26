"""Local Discord slash-command bot and read-only LAN weather relay."""

import ipaddress
import json
import os
import socket
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parent
STATE_PATH = ROOT / "weather_state.json"
EVENTS = {
    "astral-surge": "Astral Surge",
    "verdant-bloom": "Verdant Bloom",
    "fortune-squall": "Fortune Squall",
    "harvest-tide": "Harvest Tide",
    "artisan-ember": "Artisan Ember",
    "shadowveil": "Shadowveil",
    "explorers-aurora": "Explorer's Aurora",
    "warfront": "Warfront",
}
STATE_LOCK = threading.RLock()


def load_env_file() -> None:
    """Read a simple local .env file without overriding process environment."""
    env_path = ROOT / ".env"
    if not env_path.is_file():
        return
    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip().strip("\"'")
        os.environ.setdefault(key.strip(), value)


def now_ms() -> int:
    return int(time.time() * 1000)


def default_state() -> dict[str, Any]:
    return {
        "mode": "local",
        "event": "",
        "multiplier": 1,
        "startedAtMs": 0,
        "endsAtMs": 0,
        "revision": 0,
        "announcement": None,
    }


def read_state() -> dict[str, Any]:
    try:
        data = json.loads(STATE_PATH.read_text(encoding="utf-8"))
        if data.get("mode") not in ("local", "event"):
            return default_state()
        if data.get("mode") == "event":
            if data.get("event") not in EVENTS or not (2 <= int(data.get("multiplier", 0)) <= 50):
                return default_state()
        data.setdefault("announcement", None)
        return data
    except (OSError, ValueError, TypeError, json.JSONDecodeError):
        return default_state()


STATE = read_state()


def persist_state() -> None:
    temporary = STATE_PATH.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(STATE, separators=(",", ":")), encoding="utf-8")
    temporary.replace(STATE_PATH)


def expire_if_needed() -> None:
    now = now_ms()
    changed = False
    announcement = STATE.get("announcement")
    if isinstance(announcement, dict) and int(announcement.get("expiresAtMs", 0)) <= now:
        announcement = None
        STATE["announcement"] = None
        changed = True
    if STATE.get("mode") == "event" and int(STATE.get("endsAtMs", 0)) <= now:
        revision = int(STATE.get("revision", 0)) + 1
        STATE.clear()
        STATE.update(default_state())
        STATE["revision"] = revision
        STATE["announcement"] = announcement
        changed = True
    if changed:
        persist_state()


def public_state() -> dict[str, Any]:
    with STATE_LOCK:
        expire_if_needed()
        state = dict(STATE)
    state["serverTimeMs"] = now_ms()
    return state


def set_event(event: str, multiplier: int, duration_minutes: int) -> dict[str, Any]:
    started = now_ms()
    with STATE_LOCK:
        STATE.update({
            "mode": "event",
            "event": event,
            "multiplier": multiplier,
            "startedAtMs": started,
            "endsAtMs": started + duration_minutes * 60_000,
            "revision": int(STATE.get("revision", 0)) + 1,
        })
        persist_state()
        return dict(STATE)


def stop_event() -> dict[str, Any]:
    with STATE_LOCK:
        revision = int(STATE.get("revision", 0)) + 1
        announcement = STATE.get("announcement")
        STATE.clear()
        STATE.update(default_state())
        STATE["revision"] = revision
        STATE["announcement"] = announcement
        persist_state()
        return dict(STATE)


def set_announcement(message: str, duration_minutes: int) -> dict[str, Any]:
    created = now_ms()
    with STATE_LOCK:
        STATE["announcement"] = {
            "id": f"{created}-{int(STATE.get('revision', 0)) + 1}",
            "text": message,
            "createdAtMs": created,
            "expiresAtMs": created + duration_minutes * 60_000,
        }
        STATE["revision"] = int(STATE.get("revision", 0)) + 1
        persist_state()
        return dict(STATE)


def clear_announcement() -> dict[str, Any]:
    with STATE_LOCK:
        STATE["announcement"] = None
        STATE["revision"] = int(STATE.get("revision", 0)) + 1
        persist_state()
        return dict(STATE)


def allowed_lan_address(address: str) -> bool:
    try:
        ip = ipaddress.ip_address(address.split("%", 1)[0])
    except ValueError:
        return False
    return ip.is_loopback or ip.is_private


class WeatherHandler(BaseHTTPRequestHandler):
    server_version = "ArtemizWeatherRelay/1"

    def log_message(self, format_string: str, *args: Any) -> None:
        # Avoid dumping request data into console logs.
        print("[relay] " + (format_string % args))

    def send_json(self, status: int, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store, max-age=0")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:  # noqa: N802 - stdlib handler method name
        if not allowed_lan_address(self.client_address[0]):
            self.send_json(403, {"error": "LAN clients only"})
            return
        route = urlparse(self.path).path
        if route == "/health":
            self.send_json(200, {"ok": True, "serverTimeMs": now_ms()})
        elif route == "/v1/weather/state":
            self.send_json(200, public_state())
        else:
            self.send_json(404, {"error": "Not found"})

    def do_POST(self) -> None:  # noqa: N802
        self.send_json(405, {"error": "Read-only relay"})

    def do_PUT(self) -> None:  # noqa: N802
        self.send_json(405, {"error": "Read-only relay"})

    def do_DELETE(self) -> None:  # noqa: N802
        self.send_json(405, {"error": "Read-only relay"})


def local_ipv4() -> str:
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("192.0.2.1", 80))
        address = probe.getsockname()[0]
        return address if address and not address.startswith("127.") else "<PC-LAN-IP>"
    except OSError:
        return "<PC-LAN-IP>"
    finally:
        probe.close()


def start_relay() -> ThreadingHTTPServer:
    port = int(os.environ.get("WEATHER_RELAY_PORT", "8765"))
    if not 1024 <= port <= 65535:
        raise ValueError("WEATHER_RELAY_PORT must be between 1024 and 65535")
    server = ThreadingHTTPServer(("0.0.0.0", port), WeatherHandler)
    server.daemon_threads = True
    threading.Thread(target=server.serve_forever, name="weather-http-relay", daemon=True).start()
    print("Weather relay is listening on port " + str(port))
    print("Phone URL: http://" + local_ipv4() + ":" + str(port))
    print("Only read-only state is exposed; event changes require the Discord command.")
    return server


def start_discord_bot() -> None:
    token = os.environ.get("DISCORD_BOT_TOKEN", "").strip()
    guild_id_text = os.environ.get("DISCORD_GUILD_ID", "").strip()
    allowed_user_text = os.environ.get("DISCORD_ALLOWED_USER_ID", "").strip()
    if not token or not guild_id_text or not allowed_user_text:
        print("Discord bot is not connected. Fill in DISCORD_BOT_TOKEN, DISCORD_GUILD_ID, and "
              "DISCORD_ALLOWED_USER_ID in weather-bridge/.env, then restart.")
        return

    try:
        import discord
        from discord import app_commands
    except ImportError as error:
        raise RuntimeError("Install dependencies with: python -m pip install -r requirements.txt") from error

    guild_id = int(guild_id_text)
    allowed_user_id = int(allowed_user_text)
    guild = discord.Object(id=guild_id)
    client = discord.Client(intents=discord.Intents.none())
    tree = app_commands.CommandTree(client)
    artemiz_group = app_commands.Group(name="artemiz", description="Control Artemiz game events")
    weather_group = app_commands.Group(name="weather", description="Control global game weather")
    message_group = app_commands.Group(name="message", description="Send a message to active game clients")

    async def authorized(interaction: discord.Interaction) -> bool:
        if interaction.guild_id != guild_id or interaction.user.id != allowed_user_id:
            await interaction.response.send_message(
                "This command is restricted to its configured server and user.", ephemeral=True)
            return False
        return True

    event_choices = [app_commands.Choice(name=name, value=key) for key, name in EVENTS.items()]

    @weather_group.command(name="start", description="Start a weather event")
    @app_commands.describe(event="Weather event", multiplier="Bonus multiplier (2–50)",
                           duration_minutes="Event duration (1–120 minutes)")
    @app_commands.choices(event=event_choices)
    async def start_weather(
        interaction: discord.Interaction,
        event: app_commands.Choice[str],
        multiplier: app_commands.Range[int, 2, 50],
        duration_minutes: app_commands.Range[int, 1, 120],
    ) -> None:
        if not await authorized(interaction):
            return
        state = set_event(event.value, int(multiplier), int(duration_minutes))
        title = EVENTS[event.value]
        await interaction.response.send_message(
            f"Weather started: **{title}** · **{multiplier}×** · **{duration_minutes} min**. "
            f"Revision {state['revision']}.", ephemeral=True)

    @weather_group.command(name="stop", description="End the active weather event")
    async def stop_weather(interaction: discord.Interaction) -> None:
        if not await authorized(interaction):
            return
        stop_event()
        await interaction.response.send_message(
            "Admin weather event ended. The game returned to its normal weather cycle.",
            ephemeral=True)

    @weather_group.command(name="status", description="Show the current weather event")
    async def status_weather(interaction: discord.Interaction) -> None:
        if not await authorized(interaction):
            return
        state = public_state()
        if state["mode"] == "event":
            name = EVENTS[state["event"]]
            seconds = max(0, (state["endsAtMs"] - state["serverTimeMs"] + 999) // 1000)
            message = (f"**{name}** · **{state['multiplier']}×** · "
                       f"{seconds // 60}m {seconds % 60}s remaining")
        else:
            message = "No admin event is active; normal in-game weather is running."
        await interaction.response.send_message(message, ephemeral=True)

    @message_group.command(name="send", description="Show a message in active game clients")
    @app_commands.describe(text="Message shown to players (up to 500 characters)",
                           duration_minutes="How long the message stays available (1–120 minutes)")
    async def send_announcement(
        interaction: discord.Interaction,
        text: str,
        duration_minutes: app_commands.Range[int, 1, 120] = 5,
    ) -> None:
        if not await authorized(interaction):
            return
        message = text.strip()
        if not message or len(message) > 500:
            await interaction.response.send_message(
                "Enter a message of 1–500 characters.", ephemeral=True)
            return
        state = set_announcement(message, int(duration_minutes))
        await interaction.response.send_message(
            f"Announcement sent to active game clients for {duration_minutes} minute(s). "
            f"Revision {state['revision']}.", ephemeral=True)

    @message_group.command(name="clear", description="Clear the active game announcement")
    async def clear_announcement_command(interaction: discord.Interaction) -> None:
        if not await authorized(interaction):
            return
        clear_announcement()
        await interaction.response.send_message(
            "The active game announcement was cleared.", ephemeral=True)

    artemiz_group.add_command(weather_group)
    artemiz_group.add_command(message_group)
    tree.add_command(artemiz_group, guild=guild)

    @client.event
    async def on_ready() -> None:
        await tree.sync(guild=guild)
        print("Artemiz Discord bot connected as " + str(client.user))
        print("Slash commands synced to guild " + str(guild_id))

    client.run(token, log_handler=None)


def main() -> None:
    load_env_file()
    relay = start_relay()
    try:
        start_discord_bot()
        # With no bot credentials, keep the read-only LAN service available until stopped.
        while True:
            time.sleep(3600)
    except KeyboardInterrupt:
        print("Stopping weather relay.")
    finally:
        relay.shutdown()
        relay.server_close()


if __name__ == "__main__":
    main()
