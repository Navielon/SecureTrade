# Secure Trade

<p align="center">
  <img src="mod_icon.png" alt="Secure Trade icon" width="160">
</p>

![Loader](https://img.shields.io/badge/Loader-Fabric%20%7C%20NeoForge-orange.svg)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://minecraft.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Secure Trade lets players exchange items and experience from anywhere, without running back to base or meeting in the same dimension.

It is built for co-op servers and modpacks where players often split up: one player is mining, another is crafting at base, someone else is in the Nether, and a simple handoff should not interrupt what everyone is doing. Send a request with `/trade <player>`, let the other player accept it in chat, and both players receive a shared trade menu.

By default, trades work across any distance and dimension. Server owners can keep that freedom, restrict trades by distance, block or allow specific dimensions, disable specific items, tune request timing, and keep a lightweight transaction history.

## Features

- Player-to-player trades through a shared GUI.
- Trade from anywhere by default, including across dimensions.
- Item and XP exchange in the same trade session.
- Chat-based trade requests with clickable accept and deny actions.
- Offer locking for both sides before completion.
- Automatic readiness reset when offered items or XP change.
- Configurable countdown before the trade is executed.
- Optional distance limit for servers that want local-only trading.
- Optional dimension allowlist or blocklist.
- Optional item blacklist, with `minecraft:bedrock` blocked by default.
- Request cooldowns to reduce spam.
- One active trade or pending request per player.
- Trade history command for checking recent exchanges.
- Optional async transaction logging to `logs/securetrade.log`.
- English and Russian localization.
- Fabric and NeoForge support for Minecraft 1.21.1.

## Commands

| Command | Description |
| --- | --- |
| `/trade <player>` | Sends a trade request. If both players request each other, the trade opens immediately. |
| `/trade accept` | Accepts the current pending trade request. |
| `/trade deny` | Denies the current pending trade request. |
| `/trade history` | Shows recent trades for the player, including items and XP. |

## Configuration

Server configuration is stored in `config/securetrade-server.toml`.

| Option | Type | Default | Description |
| --- | --- | --- | --- |
| `requestTimeoutSeconds` | Integer | `60` | Seconds before an unanswered trade request expires. |
| `tradeCooldownSeconds` | Integer | `10` | Seconds before a player can send another request to the same target. |
| `maxTradeDistance` | Double | `-1.0` | Maximum distance in blocks. Use `-1.0` for no distance limit. If a positive distance is set, players must be in the same dimension and within range. |
| `countdownSeconds` | Integer | `3` | Seconds to wait after both players lock their offers. |
| `enableTradeLogging` | Boolean | `true` | Writes completed trades to `logs/securetrade.log`. |
| `blacklistedItems` | String list | `["minecraft:bedrock"]` | Item IDs that cannot be traded. |
| `allowedDimensions` | String list | `[]` | Dimension IDs where trading is allowed. Leave empty to allow all dimensions unless blocked. |
| `blockedDimensions` | String list | `[]` | Dimension IDs where trading is blocked. Ignored if `allowedDimensions` is not empty. |
| `maxHistoryEntries` | Integer | `5` | Number of recent trade history entries shown by `/trade history`. |

## Notes

- Secure Trade is designed as a server utility for multiplayer worlds and modpacks.
- Both players must confirm the trade before it completes.
- If either side changes offered items or XP after locking, readiness is reset.
- If a player disconnects during a trade, offered items are safely returned or dropped at the player's last known position.

## License

Secure Trade is released under the MIT License. See [LICENSE](LICENSE) for details.
