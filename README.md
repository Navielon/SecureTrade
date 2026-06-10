# Secure Trade

<p align="center">
  <img src="mod_icon.png" alt="Secure Trade icon" width="160">
</p>

![Loader](https://img.shields.io/badge/Loader-Fabric%20%7C%20Forge%20%7C%20NeoForge-orange.svg)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.16.5%20--%201.21.1%20%7C%2026.1.x-brightgreen.svg)](https://minecraft.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Secure Trade** adds a direct player-to-player trade menu for multiplayer worlds, co-op servers, and modpacks.

Trade items and experience with another player from anywhere: across a base, across the map, or even across dimensions. No more running back home just to hand over ores, tools, drops, or XP.

## Why Use It?

Minecraft multiplayer often splits players across different jobs and locations. One player may be mining, another may be crafting at base, and someone else may be exploring the Nether or a modded dimension. Secure Trade keeps that flow intact: send a trade request, confirm the exchange, and keep playing.

By default, trades are not limited by distance or dimension. Server owners can keep that freedom or configure stricter rules for local-only trading, dimension restrictions, item blacklists, cooldowns, logging, and trade history.

## How It Works

1. Run `/trade <player>`.
2. The other player accepts the request from chat.
3. Both players get a shared trade screen.
4. Each side adds items and/or XP.
5. Both players confirm.
6. A short countdown starts, then the exchange completes.

If either player changes their offer after confirming, the ready state is reset so both sides can review the updated trade.

## Features

- Trade items with another player through a shared GUI.
- Trade from anywhere by default, including across dimensions.
- Exchange items and XP in the same trade session.
- 27 trade slots per player.
- Clickable chat requests with accept and deny actions.
- Clear confirmation states and countdown before completion.
- Custom trade sounds for requests, countdown, blocked items, and successful trades.
- Automatic readiness reset when items or XP change.
- Optional distance limit for servers that want local-only trading.
- Optional dimension allowlist or blocklist.
- Optional item blacklist, including protected items inside supported containers.
- One active trade or pending request per player.
- Request cooldowns to reduce spam.
- Trade history command for checking recent exchanges.
- Optional async transaction logging to `logs/securetrade.log`.
- Disconnect-safe item return or drop handling.
- Localization for 23 languages.

## Supported Loaders And Versions

Secure Trade is maintained across multiple Minecraft versions.

| Minecraft | Loaders |
| --- | --- |
| 26.1.x | Fabric, NeoForge |
| 1.21.1 | Fabric, NeoForge |
| 1.20.1 | Fabric, Forge |
| 1.19.2 | Fabric, Forge |
| 1.18.2 | Fabric, Forge |
| 1.16.5 | Fabric, Forge |

Use the file that matches your Minecraft version and mod loader.

## Commands

| Command | Description |
| --- | --- |
| `/trade <player>` | Sends a trade request. If both players request each other, the trade opens immediately. |
| `/trade accept` | Accepts the current pending trade request. |
| `/trade deny` | Denies the current pending trade request. |
| `/trade history` | Shows recent trades for the player, including items and XP. |

## For Server Owners

Secure Trade is designed as a lightweight server utility. It can stay permissive for co-op play, or be restricted for stricter multiplayer servers.

The server configuration is stored in:

```text
config/securetrade-server.toml
```

| Option | Default | Description |
| --- | --- | --- |
| `requestTimeoutSeconds` | `60` | Seconds before an unanswered trade request expires. |
| `tradeCooldownSeconds` | `10` | Seconds before a player can send another request to the same target. |
| `maxTradeDistance` | `-1.0` | Maximum distance in blocks. Use `-1.0` for no distance limit. If positive, players must be in the same dimension and within range. |
| `countdownSeconds` | `3` | Seconds to wait after both players confirm their offers. |
| `enableTradeLogging` | `true` | Writes completed trades to `logs/securetrade.log`. |
| `blacklistedItems` | `["minecraft:bedrock"]` | Item IDs that cannot be traded. |
| `allowedDimensions` | `[]` | Dimension IDs where trading is allowed. Leave empty to allow all dimensions unless blocked. |
| `blockedDimensions` | `[]` | Dimension IDs where trading is blocked. Ignored if `allowedDimensions` is not empty. |
| `maxHistoryEntries` | `5` | Number of recent trade history entries shown by `/trade history`. |

## Safety Notes

- Both players must confirm before a trade completes.
- Changing items or XP after confirming resets readiness.
- Blacklisted items are blocked before they enter the trade grid.
- If a player disconnects during a trade, offered items are returned or dropped at the player's last known position.
- Completed trades can be written to a lightweight async log file.

## Support

Found a bug or compatibility issue? Please open a GitHub issue and include:

- Minecraft version
- Mod loader and loader version
- Secure Trade version
- Steps to reproduce
- `latest.log`

[Report an issue on GitHub](https://github.com/Navielon/SecureTrade/issues/new/choose)

## License

Secure Trade is released under the MIT License. See [LICENSE](LICENSE) for details.
