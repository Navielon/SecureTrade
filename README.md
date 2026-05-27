# Secure Trade

<p align="center">
  <img src="mod_icon.png" alt="Secure Trade icon" width="160">
</p>

[![Loader](https://img.shields.io/badge/Loader-NeoForge-orange.svg)](https://neoforged.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://minecraft.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Secure Trade lets players exchange items from anywhere, without running back to base or meeting in the same dimension.

It is built for co-op servers and modpacks where players often split up: one player is mining, another is crafting at base, someone else is in the Nether, and a simple item handoff should not interrupt what everyone is doing. Send a request with `/trade <player>`, let the other player accept it in chat, and both players receive a shared trade menu.

By default, trades work across any distance and dimension. If a server owner wants a more local trading experience, distance limits can still be enabled in the server config alongside request timeouts, cooldowns, transaction logging, and the final countdown.

## Features

- Player-to-player trades through a shared GUI.
- Trade from anywhere by default, including across dimensions.
- Chat-based trade requests with clickable accept and deny actions.
- Offer locking for both sides before completion.
- Automatic readiness reset when offered items change.
- Configurable countdown before the trade is executed.
- Optional distance limit for servers that want local-only trading.
- Request cooldowns to reduce spam.
- One active trade or pending request per player.
- Optional transaction logging to `logs/securetrade.log`.
- English and Russian localization.

## Commands

| Command | Description |
| --- | --- |
| `/trade <player>` | Sends a trade request. If both players request each other, the trade opens immediately. |
| `/trade accept` | Accepts the current pending trade request. |
| `/trade deny` | Denies the current pending trade request. |

## Configuration

Server configuration is stored in `config/securetrade-server.toml`.

| Option | Type | Default | Description |
| --- | --- | --- | --- |
| `requestTimeoutSeconds` | Integer | `60` | Seconds before an unanswered trade request expires. |
| `tradeCooldownSeconds` | Integer | `10` | Seconds before a player can send another request to the same target. |
| `maxTradeDistance` | Double | `-1.0` | Maximum distance in blocks. Use `-1.0` for no distance or dimension limit. |
| `countdownSeconds` | Integer | `3` | Seconds to wait after both players lock their offers. |
| `enableTradeLogging` | Boolean | `true` | Writes completed trades to `logs/securetrade.log`. |

## License

Secure Trade is released under the MIT License. See [LICENSE](LICENSE) for details.
