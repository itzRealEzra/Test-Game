const EVENT_NAMES = {
  "astral-surge": "Astral Surge",
  "verdant-bloom": "Verdant Bloom",
  "fortune-squall": "Fortune Squall",
  "harvest-tide": "Harvest Tide",
  "artisan-ember": "Artisan Ember",
  shadowveil: "Shadowveil",
  "explorers-aurora": "Explorer's Aurora",
  warfront: "Warfront",
};

const encoder = new TextEncoder();
const MAX_INTERACTION_BYTES = 64 * 1024;
const MAX_SIGNATURE_AGE_MS = 5 * 60 * 1000;
const MAX_ANNOUNCEMENT_LENGTH = 500;

function jsonResponse(status, payload) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store, max-age=0",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

function hexToBytes(value) {
  if (typeof value !== "string" || value.length % 2 !== 0 || !/^[0-9a-f]+$/i.test(value)) {
    return null;
  }
  const bytes = new Uint8Array(value.length / 2);
  for (let i = 0; i < bytes.length; i += 1) {
    bytes[i] = Number.parseInt(value.slice(i * 2, i * 2 + 2), 16);
  }
  return bytes;
}

function concatBytes(first, second) {
  const result = new Uint8Array(first.length + second.length);
  result.set(first, 0);
  result.set(second, first.length);
  return result;
}

async function hasValidDiscordSignature(request, env, body) {
  const signatureHex = request.headers.get("X-Signature-Ed25519") || "";
  const timestamp = request.headers.get("X-Signature-Timestamp") || "";
  const publicKey = hexToBytes(env.DISCORD_PUBLIC_KEY || "");
  const signature = hexToBytes(signatureHex);
  const timestampMs = Number(timestamp) * 1000;

  if (!publicKey || publicKey.length !== 32 || !signature || signature.length !== 64
      || !Number.isFinite(timestampMs)
      || Math.abs(Date.now() - timestampMs) > MAX_SIGNATURE_AGE_MS) {
    return false;
  }

  try {
    const key = await crypto.subtle.importKey(
      "raw", publicKey, { name: "Ed25519" }, false, ["verify"]);
    return await crypto.subtle.verify(
      { name: "Ed25519" }, key, signature,
      concatBytes(encoder.encode(timestamp), body));
  } catch (_) {
    return false;
  }
}

function stateStub(env) {
  const id = env.WEATHER_STATE.idFromName("artemiz-weather-state");
  return env.WEATHER_STATE.get(id);
}

async function callState(env, path, payload) {
  const stub = stateStub(env);
  const options = payload === undefined
    ? { method: "GET" }
    : {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      };
  const response = await stub.fetch("https://weather-state" + path, options);
  if (!response.ok) throw new Error("weather-state request failed");
  return response.json();
}

function interactionReply(content) {
  return jsonResponse(200, { type: 4, data: { content, flags: 64 } });
}

function commandOptions(interaction) {
  const rootOptions = interaction.data?.options || [];
  const group = rootOptions.find((option) => option.type === 2);
  const subcommand = (group?.options || rootOptions).find((option) => option.type === 1);
  return {
    group: group?.name || "",
    name: subcommand?.name || "",
    options: subcommand?.options || [],
  };
}

function optionValue(options, name) {
  const option = options.find((item) => item.name === name);
  return option ? option.value : undefined;
}

async function handleArtemizCommand(interaction, env) {
  const userId = interaction.member?.user?.id || interaction.user?.id || "";
  if (interaction.guild_id !== env.WEATHER_GUILD_ID
      || userId !== env.WEATHER_ALLOWED_USER_ID) {
    return interactionReply("This command is restricted to its configured server and user.");
  }

  const command = commandOptions(interaction);
  if (command.group === "weather" && command.name === "start") {
    const event = optionValue(command.options, "event");
    const multiplier = Number(optionValue(command.options, "multiplier"));
    const durationMinutes = Number(optionValue(command.options, "duration_minutes"));
    if (!Object.prototype.hasOwnProperty.call(EVENT_NAMES, event)
        || !Number.isInteger(multiplier) || multiplier < 2 || multiplier > 50
        || !Number.isInteger(durationMinutes) || durationMinutes < 1 || durationMinutes > 120) {
      return interactionReply("Invalid weather settings. Choose a listed event, 2×–50×, and 1–120 minutes.");
    }

    try {
      const state = await callState(env, "/start", { event, multiplier, durationMinutes });
      return interactionReply(
        `Weather started: **${EVENT_NAMES[event]}** · **${multiplier}×** · **${durationMinutes} min**. Revision ${state.revision}.`);
    } catch (_) {
      return interactionReply("The weather relay could not save this event. Please try again.");
    }
  }

  if (command.group === "weather" && command.name === "stop") {
    try {
      await callState(env, "/stop", {});
      return interactionReply("Admin weather event ended. The game returned to its normal weather cycle.");
    } catch (_) {
      return interactionReply("The weather relay could not stop this event. Please try again.");
    }
  }

  if (command.group === "weather" && command.name === "status") {
    try {
      const state = await callState(env, "/state");
      if (state.mode === "event" && EVENT_NAMES[state.event]) {
        const seconds = Math.max(0, Math.ceil((state.endsAtMs - state.serverTimeMs) / 1000));
        return interactionReply(
          `**${EVENT_NAMES[state.event]}** · **${state.multiplier}×** · ${Math.floor(seconds / 60)}m ${seconds % 60}s remaining`);
      }
      return interactionReply("No admin event is active; normal in-game weather is running.");
    } catch (_) {
      return interactionReply("The weather relay could not read status. Please try again.");
    }
  }

  if (command.group === "message" && command.name === "send") {
    const message = String(optionValue(command.options, "text") || "").trim();
    const rawDuration = optionValue(command.options, "duration_minutes");
    const durationMinutes = rawDuration === undefined ? 5 : Number(rawDuration);
    if (!message || message.length > MAX_ANNOUNCEMENT_LENGTH
        || !Number.isInteger(durationMinutes) || durationMinutes < 1 || durationMinutes > 120) {
      return interactionReply("Enter a message of 1–500 characters and a duration of 1–120 minutes.");
    }
    try {
      const state = await callState(env, "/announce", { message, durationMinutes });
      const expiresIn = Math.max(1, Math.ceil((state.announcement.expiresAtMs - state.serverTimeMs) / 60000));
      return interactionReply(`Announcement sent to active game clients for ${expiresIn} minute(s).`);
    } catch (_) {
      return interactionReply("The Artemiz relay could not send this announcement. Please try again.");
    }
  }

  if (command.group === "message" && command.name === "clear") {
    try {
      await callState(env, "/clear-announcement", {});
      return interactionReply("The active game announcement was cleared.");
    } catch (_) {
      return interactionReply("The Artemiz relay could not clear this announcement. Please try again.");
    }
  }

  return interactionReply("Unknown Artemiz game command.");
}

async function handleDiscordInteraction(request, env) {
  if (!env.DISCORD_PUBLIC_KEY) return jsonResponse(503, { error: "Discord public key is not configured" });

  const body = new Uint8Array(await request.arrayBuffer());
  if (body.length > MAX_INTERACTION_BYTES) return jsonResponse(413, { error: "Request is too large" });
  if (!await hasValidDiscordSignature(request, env, body)) {
    return jsonResponse(401, { error: "Invalid Discord signature" });
  }

  let interaction;
  try {
    interaction = JSON.parse(new TextDecoder().decode(body));
  } catch (_) {
    return jsonResponse(400, { error: "Invalid JSON" });
  }

  if (interaction.type === 1) return jsonResponse(200, { type: 1 });
  if (interaction.type !== 2) return jsonResponse(400, { error: "Unsupported interaction type" });
  if (!env.WEATHER_GUILD_ID || !env.WEATHER_ALLOWED_USER_ID) {
    return interactionReply("The Artemiz command is not configured yet.");
  }
  if (interaction.data?.name !== "artemiz") return interactionReply("Unknown command.");
  return handleArtemizCommand(interaction, env);
}

export class WeatherState {
  constructor(ctx) {
    this.ctx = ctx;
  }

  localState(revision, announcement = null) {
    return {
      mode: "local",
      event: "",
      multiplier: 1,
      startedAtMs: 0,
      endsAtMs: 0,
      revision,
      announcement,
    };
  }

  async currentState() {
    let state = await this.ctx.storage.get("weather-state");
    if (!state) {
      state = this.localState(0);
      await this.ctx.storage.put("weather-state", state);
    } else {
      const now = Date.now();
      const announcement = state.announcement && state.announcement.expiresAtMs > now
        ? state.announcement : null;
      const eventExpired = state.mode === "event" && state.endsAtMs <= now;
      if (eventExpired) {
        state = this.localState(state.revision + 1, announcement);
        await this.ctx.storage.put("weather-state", state);
      } else if (state.announcement !== announcement || !Object.prototype.hasOwnProperty.call(state, "announcement")) {
        state = { ...state, announcement };
        await this.ctx.storage.put("weather-state", state);
      }
    }
    return { ...state, serverTimeMs: Date.now() };
  }

  async fetch(request) {
    const path = new URL(request.url).pathname;
    if (request.method === "GET" && path === "/state") {
      return jsonResponse(200, await this.currentState());
    }

    if (request.method === "POST" && path === "/start") {
      let payload;
      try {
        payload = await request.json();
      } catch (_) {
        return jsonResponse(400, { error: "Invalid JSON" });
      }
      const event = payload.event;
      const multiplier = Number(payload.multiplier);
      const durationMinutes = Number(payload.durationMinutes);
      if (!Object.prototype.hasOwnProperty.call(EVENT_NAMES, event)
          || !Number.isInteger(multiplier) || multiplier < 2 || multiplier > 50
          || !Number.isInteger(durationMinutes) || durationMinutes < 1 || durationMinutes > 120) {
        return jsonResponse(400, { error: "Invalid weather event" });
      }
      const previous = await this.currentState();
      const startedAtMs = Date.now();
      const state = {
        mode: "event",
        event,
        multiplier,
        startedAtMs,
        endsAtMs: startedAtMs + durationMinutes * 60_000,
        revision: previous.revision + 1,
        announcement: previous.announcement || null,
      };
      await this.ctx.storage.put("weather-state", state);
      return jsonResponse(200, { ...state, serverTimeMs: Date.now() });
    }

    if (request.method === "POST" && path === "/stop") {
      const previous = await this.currentState();
      const state = this.localState(previous.revision + 1, previous.announcement || null);
      await this.ctx.storage.put("weather-state", state);
      return jsonResponse(200, { ...state, serverTimeMs: Date.now() });
    }

    if (request.method === "POST" && path === "/announce") {
      let payload;
      try {
        payload = await request.json();
      } catch (_) {
        return jsonResponse(400, { error: "Invalid JSON" });
      }
      const message = typeof payload.message === "string" ? payload.message.trim() : "";
      const durationMinutes = Number(payload.durationMinutes);
      if (!message || message.length > MAX_ANNOUNCEMENT_LENGTH
          || !Number.isInteger(durationMinutes) || durationMinutes < 1 || durationMinutes > 120) {
        return jsonResponse(400, { error: "Invalid announcement" });
      }
      const previous = await this.currentState();
      const now = Date.now();
      const { serverTimeMs: _serverTimeMs, ...baseState } = previous;
      const state = {
        ...baseState,
        revision: previous.revision + 1,
        announcement: {
          id: crypto.randomUUID(),
          text: message,
          createdAtMs: now,
          expiresAtMs: now + durationMinutes * 60_000,
        },
      };
      await this.ctx.storage.put("weather-state", state);
      return jsonResponse(200, { ...state, serverTimeMs: Date.now() });
    }

    if (request.method === "POST" && path === "/clear-announcement") {
      const previous = await this.currentState();
      const { serverTimeMs: _serverTimeMs, ...baseState } = previous;
      const state = { ...baseState, revision: previous.revision + 1, announcement: null };
      await this.ctx.storage.put("weather-state", state);
      return jsonResponse(200, { ...state, serverTimeMs: Date.now() });
    }

    return jsonResponse(404, { error: "Not found" });
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return jsonResponse(200, { ok: true, serverTimeMs: Date.now() });
    }
    if (request.method === "GET" && url.pathname === "/v1/weather/state") {
      try {
        return jsonResponse(200, await callState(env, "/state"));
      } catch (_) {
        return jsonResponse(503, { error: "Weather state is unavailable" });
      }
    }
    if (request.method === "POST" && url.pathname === "/interactions") {
      return handleDiscordInteraction(request, env);
    }
    return jsonResponse(404, { error: "Not found" });
  },
};
