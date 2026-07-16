# Connection Verify

**Give every connection to your server a short, memorable number — and save a complete, log-style report of it whenever you want.**

Connection Verify is a lightweight, privacy-friendly Paper plugin for server operators and admins. When a player connects (or **fails** to connect), it prints a connection number to the console; type `cnt <number>` and the plugin writes an exhaustive report of that connection to a file.

No database. No web server. Reports never leave your machine — they are written only to local files. (See **Privacy & network** below.)

---

## ✨ Features

- **A number for every connection** — successful joins *and* failures (ban, whitelist, server full, plugin denials, dropped/lost connections).
- **Exhaustive, log-style reports** — identity, network, client options, session, full player state & attributes, equipment/inventory, location/biome/light, world, **deep system & JVM diagnostics**, and more.
- **Text *or* JSON** output for human reading or log pipelines.
- **Privacy controls** — optional IP masking (`203.0.x.x`) and UUID redaction.
- **Fully localizable** — bundled English & Korean, add your own language file.
- **30+ config options** and live reload (`/connectionverify reload`).
- **Folia-native** — uses Paper's region schedulers, so it runs on Paper, Purpur *and* Folia.
- **Async everything** — file writes and the optional update check never touch the main thread.
- **Zero dependencies** — a small, self-contained jar.

---

## 🔧 How it works

When a player connects, the console shows:

```
Join Steve
Connection number 4821
```

A failed/dropped connection gets one too — including the nameless raw drops the
server logs as `/<ip>:<port> lost connection: …`, which have no Bukkit event:

```
Connection failed: Steve (KICK_BANNED)
Connection number 7392

Connection dropped: 203.0.113.7:51234 (Connection failed. Please try again or contact an administrator.)
Connection number 1574
```

Run the command in the console:

```
cnt 4821
```

…and Connection Verify writes the full report to:

```
plugins/connection-verify/connection/4821.txt
```

---

## 🧾 What's captured

Reports are grouped into clearly labelled sections, including:

- **Identity** — name, display/list name, UUID (+ version), decoded **skin/cape texture URLs**, profile signature
- **Network** — IP, port, virtual host, client brand, protocol, ping, view/sim distance, transfer flag
- **Client options** — locale, main hand, chat & particle visibility, skin parts
- **Session & player state** — first join, op/ban/whitelist, game mode, health, hunger, XP, movement, pose, potion effects, …
- **Attributes** — every applicable attribute (value + base)
- **Equipment & inventory** — armor, hands, used slots, ender chest
- **Location / World** — coordinates, facing, biome, light, chunk, difficulty, weather, world border
- **Server** — versions, MOTD, TPS, MSPT, op/whitelist/ban counts, worlds, plugins
- **System / hardware** — CPU load, physical & swap memory, committed virtual memory, process info, network interfaces
- **JVM details** — runtime/spec versions, VM flags, JIT, memory managers & pools

Every field is captured defensively, so a value unavailable on your build is shown as `(unavailable: …)` — a report is never broken by a single field.

---

## 🔒 Privacy & network

Connection Verify is designed to keep operator and player data on your own server:

- **Reports stay local.** Connection reports — including IP addresses and system details — are written only to files under `plugins/connection-verify/`. Nothing is uploaded, and the plugin runs no database or web server.
- **Privacy controls.** `config.yml` offers IP masking (`privacy.mask-ip`, e.g. `203.0.x.x`) and UUID redaction (`privacy.hide-uuid`) for GDPR-conscious networks. You can also disable any capture section you don't want.
- **One optional outbound request.** The only time the plugin contacts the internet is the **update checker**, which is **off by default** and opt-in via `update-checker.enabled`. When enabled it asks the public Modrinth API **once at startup** whether a newer version exists — no information about your server or players is ever transmitted.

---

## ✅ Compatibility

| File | Minecraft / Paper | Java |
|------|-------------------|------|
| `connection-verify-<version>-mc1.21.jar` | 1.21.4 – 1.21.x | 21+ |
| `connection-verify-<version>-mc26.jar` | 26.2 | 25+ |

**Runs on Paper, Purpur and Folia** — it uses Paper's region schedulers throughout, so it is Folia-native. (It's a Paper plugin using Paper-only API, so it does not run on CraftBukkit/Spigot, on proxies, or as a mod.)

Download the jar that matches your server version.

> Connection numbers are kept in memory for the current server session; run `cnt <number>` while the server is running to save a report.

---

## 📚 Links

- **Source & full docs:** https://github.com/neramc/connection-verify
- **Wiki:** https://github.com/neramc/connection-verify/wiki
- **Issues / requests:** https://github.com/neramc/connection-verify/issues
- **License:** Apache-2.0

Made with care by [neramc](https://github.com/neramc).
