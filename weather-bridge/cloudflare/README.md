# Cloudflare weather relay (no PC runtime)

This Worker replaces the always-running local Discord bot/HTTP server. Cloudflare receives Discord's signed slash-command interactions, checks the application signature and the configured guild/user IDs, then stores global weather and announcement state in a SQLite-backed Durable Object. The Android client reads the same `/v1/weather/state` JSON shape as the local bridge.

The Worker does not need a Discord bot token. It uses the app's public key to validate signed interactions. The PC-hosted bridge can remain running during setup; switch Discord and the Android client to the Worker only after deployment is ready.

## Deploy

1. Create or sign in to a Cloudflare account. Install Node.js if needed.
2. Open PowerShell in this folder and run:

   ```powershell
   npm install
   npx wrangler login
   npx wrangler deploy
   ```

3. In the Discord Developer Portal for the existing application, open **General Information** and copy **Public Key**. Set the Worker values from PowerShell; each command prompts for its value and keeps it out of this repository:

   ```powershell
   npx wrangler secret put DISCORD_PUBLIC_KEY
   npx wrangler secret put WEATHER_GUILD_ID
   npx wrangler secret put WEATHER_ALLOWED_USER_ID
   ```

   Use the same guild and allowed-user IDs currently in `weather-bridge/.env`. The Worker does not need `DISCORD_BOT_TOKEN`.

4. In **General Information**, set **Interactions Endpoint URL** to:

   ```text
   https://<your-worker-name>.<your-account-subdomain>.workers.dev/interactions
   ```

   Discord verifies the endpoint with a signed PING. This Worker verifies Ed25519 signatures and replies to PINGs.

5. Register the new `/artemiz` command tree after deploying the Worker. In PowerShell, open this folder and enter:

   ```powershell
   $env:DISCORD_APPLICATION_ID = "<Application ID>"
   $env:WEATHER_GUILD_ID = "<Server ID>"
   try {
     $secureToken = Read-Host "Paste the Bot token (input is hidden)" -AsSecureString
     $env:DISCORD_BOT_TOKEN = [System.Net.NetworkCredential]::new("", $secureToken).Password
     if ([string]::IsNullOrWhiteSpace($env:DISCORD_BOT_TOKEN)) { throw "Bot token was empty." }
     npm run register-commands
   } finally {
     Remove-Item Env:DISCORD_BOT_TOKEN, Env:DISCORD_APPLICATION_ID, Env:WEATHER_GUILD_ID -ErrorAction SilentlyContinue
   }
   ```

   The script upserts `/artemiz` and removes the old `/weather` command. It does not print or save the token. The command tree includes `/artemiz weather start|stop|status` and `/artemiz message send|clear`.
6. The current Android release has `https://artemiz-weather-relay.artemizweather.workers.dev` built in and migrates the old default PC address on upgrade. If your Worker has a different URL, enter its base URL in **Etc → Artemiz Game Commands** without `/interactions`; the Android app appends `/v1/weather/state` itself.
7. Check `https://<worker-url>/health`, then use `/artemiz weather status` and `/artemiz message send` in Discord Web. Once the cloud path works, stop the PC bridge.

The Durable Object state is persistent. Global weather returns to the shared scheduled cycle after an admin event expires; announcements expire automatically or can be cleared with Discord. Only signed interactions from the configured guild and user can write state. The phone endpoint is read-only.

## Free-plan sizing

One client polling every five seconds makes about 17,280 state reads per day. Current Cloudflare Free limits include 100,000 Worker requests/day and 100,000 Durable Object requests/day. Multiple devices share those limits. The relay does not run when the PC is off; Cloudflare serves it independently.

## Local development

Copy `.dev.vars.example` to `.dev.vars`, fill the same three values, and run `npm run dev`. `.dev.vars` is ignored by Git. Never commit Discord tokens or local secrets.
