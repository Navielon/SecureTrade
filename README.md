# Secure Trade

[![Platform](https://img.shields.io/badge/Loader-NeoForge-orange.svg)](https://neoforged.net/)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://minecraft.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A lightweight, secure, and professional player-to-player trading mod for **Minecraft (NeoForge 1.21.1)**. Designed with multiplayer servers in mind to eliminate dupe exploits, scamming, and spam.

---

## 🌟 Key Features

* 🔒 **Dupe & Scam Protection**: Server-authoritative inventory checks. Players cannot change their offered items after locking without resetting the trade validation.
* ⏱️ **3-Second Countdown**: A synchronized visual and auditory countdown starts once both players lock. The trade can be aborted at any point before completion.
* 🔊 **Immersive Sounds**: Native sound effects accompany locks, ticking seconds (with rising pitch), completion, and trade cancellations.
* 🟩 **Visual Locking Overlay**: Locked side inventories are highlighted with a translucent green tint (`0x3500FF00`) for clear visual confirmation.
* 🚫 **Anti-Spam Cooldown**: Prevents command spamming. If a trade is denied or expires, a configurable cooldown prevents the sender from spamming the target player.
* 🤝 **Mutual Trade Requests**: If Player A requests Player B, and Player B requests Player A, the trade session starts immediately without needing `/trade accept`.
* 🛡️ **Concurrent Trade Safety**:
  * Only one incoming pending request is allowed per player at a time.
  * Active players in a trade are set to "Busy" and cannot receive or accept other requests.
* 📡 **Distance & Dimension Constraints**: Trades are automatically cancelled if players walk too far apart or travel to different dimensions.
* 📝 **Transaction Logging**: Logs all successful transactions to `logs/securetrade.log` detailing coordinates, player UUIDs, and items traded.

---

## ⌨️ Commands

* `/trade <player>` - Sends a trade request to a player. If both players request each other, the trade menu opens immediately.
* `/trade accept` - Accepts the pending trade request.
* `/trade deny` - Denies the pending trade request.

---

## ⚙️ Configuration

The server configuration file can be found in your server or local directory under `config/securetrade-server.toml`.

| Config Parameter | Type | Default | Description |
|---|---|---|---|
| `requestTimeoutSeconds` | Integer | `60` | Time in seconds before an unanswered trade request expires. |
| `tradeCooldownSeconds` | Integer | `10` | Cooldown in seconds before a player can request a trade with the *same* player again. |
| `maxTradeDistance` | Double | `-1.0` | Max distance in blocks allowed between trading players (`-1.0` for infinite). |
| `countdownSeconds` | Integer | `3` | Seconds to count down before executing the trade after both players are ready. |
| `enableTradeLogging` | Boolean | `true` | Log all successful trades in `logs/securetrade.log`. |

---

## 🌐 Localization

Full translation support is included for:
* 🇺🇸 **English** (`en_us.json`)
* 🇷🇺 **Russian** (`ru_ru.json`)

---

## 🛠️ Build & Installation

### Requirements
* Java 21 or higher
* NeoForge (1.21.1)

### Building from Source
Clone the repository and run the build script:
```bash
./gradlew build
```
The compiled `.jar` file will be generated in `build/libs/`.

---

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
