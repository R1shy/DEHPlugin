# DEHPlugin

This is a plugin for The Dear Evan Hansen production by Stage One Cinematics

# Features
* Zero (reset) rotation of DEU block entities
* custom titles by username
* other stuff we need

# Discord bot

Server management moved out of the plugin into a standalone bot that fully replaces
`mc-agent.py`. The bot survives server downtime, so it can start the server from a
stopped state.

Build:

    ./gradlew bot:shadowJar

Configure `bot.conf` (token, guild/channels, `admins` + `readers` Discord user IDs,
`server_dir`, `mode=docker`, container name, backup dir). Run it as its own always-on
service — it must be running before the server needs to be started:

    java -jar bot/build/libs/bot-all.jar --config bot.conf

Systemd unit example (`/etc/systemd/system/mc-bot.service`):

    [Unit]
    Description=Minecraft Discord bot
    After=network-online.target docker.service
    Wants=network-online.target

    [Service]
    Type=simple
    WorkingDirectory=/home/minecraft
    ExecStart=/usr/bin/java -jar /home/minecraft/bot-all.jar --config /home/minecraft/bot.conf
    Restart=always

    [Install]
    WantedBy=multi-user.target

# Commands

| Command | Tier |
|---|---|
| `/start` `/stop` `/restart` | admins |
| `/status` | readers+ |
| `/logs [lines]` | readers+ |
| `/files list` `/files read` `/files download` | readers+ |
| `/files write` `/files upload` `/files mkdir` `/files move` `/files delete` `/files extract` | admins |
| `/backup create` | admins |
| `/backups` | readers+ |

* Logs stream continuously to `log_channel`; `/logs` fetches history on demand.
* Downloads are capped at Discord's 25 MB attachment limit; larger files are refused.
* Backups are created on the host and never uploaded to Discord.
