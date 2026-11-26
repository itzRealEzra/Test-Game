const token = process.env.DISCORD_BOT_TOKEN?.trim();
const applicationId = process.env.DISCORD_APPLICATION_ID?.trim();
const guildId = process.env.WEATHER_GUILD_ID?.trim();

const missing = [];
if (!token) missing.push("DISCORD_BOT_TOKEN");
if (!applicationId) missing.push("DISCORD_APPLICATION_ID");
if (!guildId) missing.push("WEATHER_GUILD_ID");
if (missing.length > 0) {
  throw new Error(`Missing environment variable(s): ${missing.join(", ")}. Values are not displayed.`);
}

const events = [
  ["Astral Surge", "astral-surge"],
  ["Verdant Bloom", "verdant-bloom"],
  ["Fortune Squall", "fortune-squall"],
  ["Harvest Tide", "harvest-tide"],
  ["Artisan Ember", "artisan-ember"],
  ["Shadowveil", "shadowveil"],
  ["Explorer's Aurora", "explorers-aurora"],
  ["Warfront", "warfront"],
];

const command = {
  name: "artemiz",
  description: "Control Artemiz game events",
  type: 1,
  options: [
    {
      type: 2,
      name: "weather",
      description: "Control global weather",
      options: [
        {
          type: 1,
          name: "start",
          description: "Start a global weather event",
          options: [
            {
              type: 3,
              name: "event",
              description: "Weather event",
              required: true,
              choices: events.map(([name, value]) => ({ name, value })),
            },
            { type: 4, name: "multiplier", description: "Multiplier from 2 to 50", required: true, min_value: 2, max_value: 50 },
            { type: 4, name: "duration_minutes", description: "Duration from 1 to 120 minutes", required: true, min_value: 1, max_value: 120 },
          ],
        },
        { type: 1, name: "stop", description: "End the active admin weather event" },
        { type: 1, name: "status", description: "Show the active admin weather event" },
      ],
    },
    {
      type: 2,
      name: "message",
      description: "Send a message to active game clients",
      options: [
        {
          type: 1,
          name: "send",
          description: "Show a message in the game",
          options: [
            { type: 3, name: "text", description: "Message shown to players", required: true, min_length: 1, max_length: 500 },
            { type: 4, name: "duration_minutes", description: "Visible for 1 to 120 minutes; default 5", required: false, min_value: 1, max_value: 120 },
          ],
        },
        { type: 1, name: "clear", description: "Clear the active game message" },
      ],
    },
  ],
};

const baseUrl = `https://discord.com/api/v10/applications/${encodeURIComponent(applicationId)}/guilds/${encodeURIComponent(guildId)}/commands`;
const headers = {
  Authorization: `Bot ${token}`,
  "Content-Type": "application/json",
};

const registerResponse = await fetch(baseUrl, {
  method: "POST",
  headers,
  body: JSON.stringify(command),
});
if (!registerResponse.ok) {
  throw new Error(`Artemiz command registration failed with HTTP ${registerResponse.status}.`);
}

const listResponse = await fetch(baseUrl, { headers });
if (!listResponse.ok) {
  throw new Error(`Could not check the guild command list (HTTP ${listResponse.status}).`);
}
const registered = await listResponse.json();
const legacyWeather = registered.find((item) => item.name === "weather");
if (legacyWeather) {
  const deleteResponse = await fetch(`${baseUrl}/${encodeURIComponent(legacyWeather.id)}`, {
    method: "DELETE",
    headers,
  });
  if (!deleteResponse.ok) {
    throw new Error(`Artemiz was registered, but legacy /weather removal failed with HTTP ${deleteResponse.status}.`);
  }
}

console.log("Registered /artemiz weather and message commands for the configured guild.");
if (legacyWeather) console.log("Removed the old /weather command.");
