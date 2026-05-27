# Secure Trade

<p align="center">
  <img src="mod_icon.png" alt="Secure Trade icon" width="160">
</p>

[![Loader](https://img.shields.io/badge/Loader-NeoForge-orange.svg)](https://neoforged.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://minecraft.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Secure Trade adds a direct player-to-player trade screen for Minecraft servers.

Send a request with `/trade <player>`, let the other player accept it in chat, and both players receive a shared trade menu. Each side places items into their offer, locks it, and the exchange completes after a short countdown.

By default, trades are not limited by distance or dimension. Players can trade from spawn, from separate bases, or across the Nether, End, and Overworld. Server owners can still configure distance limits, request timeouts, cooldowns, transaction logging, and the final countdown.

## Features

- Player-to-player trades through a shared GUI.
- Chat-based trade requests with clickable accept and deny actions.
- Offer locking for both sides before completion.
- Automatic readiness reset when offered items change.
- Configurable countdown before the trade is executed.
- Optional distance limit for servers that want local-only trading.
- Cross-dimension trading by default.
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

## Build

Requirements:

- Java 21 or newer
- Minecraft 1.21.1
- NeoForge 1.21.1

Build from source:

```bash
./gradlew build
```

The compiled jar is written to `build/libs/`.

## License

Secure Trade is released under the MIT License. See [LICENSE](LICENSE) for details.
