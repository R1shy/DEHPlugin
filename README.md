# DEHPlugin

This is a plugin for The Dear Evan Hansen production by Stage One Cinematics

# Features
* Zero (reset) rotation of DEU block entities
* custom titles by username
* other stuff we need

# Discord bot

The other part of this repo is a bot that lets you manage the server from discord

# Dev hot reloading (never used in prod)

1. Point Gradle at a dev server (absolute path to the server dir, where server.jar lives):
   - `gradle.properties` → set `devServer=/path/to/dev-server`
   - or env var `DEH_SERVER=/path/to/dev-server`
   - or per-run: `./gradlew devServer -PdevServer=/path/to/dev-server`
2. Start the dev loop:

   ```
   ./gradlew devServer
   ```

   This task:
   1. builds the plugin and drops it into `<server>/plugins/DEHPlugin.jar`
   2. starts the Paper server (`paper*.jar` in the server dir, or override with
      `-PdevStartCommand="java -Xmx2G -jar paper.jar nogui"`)
   3. watches `src/` — every save rebuilds the jar and re-deploys it to `plugins/`
   4. keeps waiting; type server commands (like `/reload`) straight into the same terminal

   Ctrl+C stops both the watcher and the server.

   If you just want to deploy without running a server, use `./gradlew deployDevServer`.
