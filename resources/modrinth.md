# Connection Verify

**Give every connection to your server a short, memorable number — and save a complete, log-style report of that exact connection whenever you want.**

Connection Verify is a lightweight, privacy-friendly toolkit for server operators and admins. When someone connects (or **fails** to connect), it prints a short **connection number** to the console. Type `cnt <number>` and it writes an exhaustive, human-readable report of *that* connection to a local file — who it was, from where, on what client, in what state, and (for failures) exactly why it dropped.

It ships as **one project with several purpose-built editions** — a Paper/Purpur/Folia plugin, a Velocity and a BungeeCord/Waterfall proxy "control tower", and Fabric & NeoForge server+client mods — so you can put connection numbers at every layer of your network. Pick the download that matches your platform from the table below.

> No database. No web server. Reports are written only to local files and never leave your machine. The single optional outbound request (an update check) is **off by default**. See **Privacy & network**.

---

## 📦 Which download do I need?

Every edition is published here as its own version, tagged with a build-metadata suffix and the matching Modrinth **loader**. Filter by your loader, or match the file name:

| Download | Loader tag | Platform | Minecraft | Java | Role |
|----------|-----------|----------|-----------|------|------|
| `connection-verify-<v>-mc1.21.jar` | `paper` `purpur` `folia` | Paper server | 1.21.4 – 1.21.11 | 21+ | Full connection reports on the server |
| `connection-verify-<v>-mc26.jar` | `paper` `purpur` `folia` | Paper server | 26.2 | 25+ | The same, built for the 26.x API |
| `connection-verify-velocity-<v>.jar` | `velocity` | Velocity proxy | 1.21.4 – 1.21.11 · 26.2 | 17+ | Network-wide numbering + backend routing |
| `connection-verify-bungee-<v>.jar` | `bungeecord` `waterfall` | BungeeCord / Waterfall | 1.21.4 – 1.21.11 · 26.2 | 17+ | The same, for BungeeCord networks |
| `connection-verify-fabric-<v>.jar` | `fabric` | Fabric server **+** client | 1.21.4 | 21+ | Server reports + client companion (needs **Fabric API**) |
| `connection-verify-neoforge-<v>.jar` | `neoforge` | NeoForge server **+** client | 1.21.4 | 21+ | Server reports + client companion |

The editions are independent — run just the plugin, or combine layers (e.g. the proxy plugin on Velocity **and** the server plugin on each backend) to get a connection number at both the proxy and the destination server.

---

## 🔧 How it works

When a player connects, the console shows two lines:

```
Join Steve
Connection number 4821
```

A **failed or dropped** connection gets a number too — including the nameless raw drops the server logs as `/<ip>:<port> lost connection: …`, which fire no Bukkit event at all:

```
Connection failed: Steve (KICK_BANNED)
Connection number 7392

Connection dropped: 203.0.113.7:51234 (Connection failed. Please try again or contact an administrator.) [Early failure]
Connection number 1574
```

The tag in brackets (`[Early failure]`, `[Timeout]`, `[Connection reset]`, …) is a quick classification of **why** it dropped. To keep everything, type the number in the console:

```
cnt 4821
```

…and Connection Verify writes the full report to a local file (path depends on the edition — see each edition below), e.g.:

```
plugins/connection-verify/connection/4821.txt
```

---

## 🧾 What a report looks like

Every field is captured **defensively** — anything a particular build can't provide is written as `(unavailable: …)` and empty values as `(unknown)`, so a report is never broken by one missing value. Prefer machine-readable output? Set `file.format: json` for pretty-printed JSON instead of text.

An abbreviated text report:

```
=========================================================
 Connection Verify — report 4821
 Recorded at: 2026-07-21 09:14:07.512 UTC
=========================================================

== Capture ==
  Event: PlayerJoinEvent     Thread: Server thread     Result: SUCCESS

== Identity ==
  Name: Steve                UUID: 8667ba71-...-9e3c   (v4, online-mode)
  Skin URL: http://textures.minecraft.net/texture/...  Cape: (none)

== Network ==
  IP: 203.0.113.7   Port: 51234   Virtual host: play.example.net:25565
  Client brand: vanilla   Protocol: 769   Ping: 34 ms   View/Sim distance: 12 / 10

== Session & player state ==
  First join: 2026-03-02   Op: false   Whitelisted: true   Game mode: SURVIVAL
  Health: 20.0   Hunger: 17   XP: L12   Pose: STANDING   Effects: NIGHT_VISION(0:42)

== Location / World ==
  World: world   XYZ: 128.5 / 71 / -344.2   Facing: 214.6°   Biome: plains   Light: 15

== Server ==
  Software: Paper 1.21.4-...   TPS: 20.0 / 20.0 / 19.98   MSPT: 3.1   Plugins: 14

== Runtime / System / JVM ==
  Java 21.0.4 (Temurin)   OS: Linux 6.8 (amd64)   CPU load: 0.21   Heap: 1408/4096 MB
```

Sections captured include: **Capture** (event, thread, timestamp) · **Identity** (name, list name, UUID + version, decoded skin/cape texture URLs, profile signature) · **Network** (IP, port, virtual host, client brand, protocol, ping, view/sim distance, transfer flag) · **Client options** (locale, main hand, chat & particle visibility, skin parts) · **Session** (first join/played, op/ban/whitelist, game mode) · **Player state** (health, hunger, XP, movement, pose, air, fire, velocity, potion effects, scoreboard tags) · **Attributes** (value + base) · **Equipment & inventory** (armor, hands, used slots, ender chest) · **Location / World** (coords, facing, biome, light, chunk, difficulty, weather, world border, entity/player counts) · **Result** (for failures: login stage, kick/result message, disconnect reason, category & likely cause, plus any exception from the log) · **Server** (software, versions, MOTD, TPS, MSPT, tick state, loaded plugins) · **Runtime** (Java, OS, CPU, memory, GC, threads, classes, uptime) · **System / hardware** (CPU load, physical & swap memory, committed virtual memory, process id/CPU time, network interfaces) · **JVM details** (runtime/spec versions, VM flags, JIT compiler & compile time, memory managers & pools).

---

## 🖥️ Server plugin — Paper / Purpur / Folia

The flagship edition. Numbers **every** connection at exactly one stage, so each attempt gets exactly one number — no duplicates, and normal quits are never counted as failures:

| Stage | Covered cases |
|-------|---------------|
| Pre-login | ban, whitelist, server full |
| Login | a plugin disallows the login |
| Connection dropped | network errors, timeouts, generic *"lost connection"* disconnects after auth but before joining |
| Raw drop | nameless sockets logged as `/<ip>:<port> lost connection: …`, closed before a profile exists |

The raw-drop stage has no event — those connections die before a name exists — so Connection Verify reads them straight from the server log through a lightweight Log4j2 watcher (`logging.network-drops`, on by default). The leading `/` distinguishes a nameless raw drop from a named disconnect, so nothing is counted twice.

- **Reports:** `plugins/connection-verify/connection/<number>.txt` (or `.json`)
- **Folia-native:** uses Paper's region schedulers throughout, so the same jar runs on Paper, Purpur **and** Folia.
- **Async:** file writes and the optional update check never touch the main thread.

> It's a Paper plugin using Paper-only API, so it does **not** run on CraftBukkit/Spigot. For proxies and modded servers, use the editions below.

---

## 🗼 Proxy "control tower" — Velocity & BungeeCord/Waterfall

Run this on your **proxy** to number every connection that flows through the network and track where each player is routed. It is a companion to the server plugin: run the plugin on your backends and this on the proxy for connection numbers at both layers.

Proxy console:

```
Join Steve
Connection number 4821
Steve ⇒ lobby [#4821]
Steve ⇒ survival (from lobby) [#4821]
Steve disconnected (SUCCESSFUL_LOGIN) [#4821]
```

- **Numbers every proxy connection** — successful logins *and* failures/denials.
- **Routing view** — logs which backend each player lands on and every server switch.
- **`/cnt <number>`** writes a report with identity, network, the proxy's **network-wide server map**, and runtime/system info to `plugins/connectionverify/connection/<number>.txt`.
- **Config:** `plugins/connectionverify/config.properties` (number length, console toggles, IP masking, retention).
- **Java 17+** (the proxy minimum). Velocity and BungeeCord/Waterfall are separate downloads.

---

## 🧩 Server + client mods — Fabric & NeoForge

Bring connection numbers to modded Minecraft 1.21.4, on **both** sides:

- **Server side** — numbers every connection on join, prints the number, and writes a full report with `/cnt <number>` (permission level 3) to `<server dir>/connection-verify/<number>.txt`.
- **Client companion (optional)** — when you join a server running the mod, the client gathers advanced details the server can't otherwise see — **loaded mod list, FPS, render/view distance, client brand, OS, Java, memory** — and sends them over the `connectionverify:client_info` custom payload; the server merges them into that connection's report.

The companion channel is registered **optional**, so a modded client can still join any server and the mod stays completely silent on vanilla or modless servers. The client half is optional to install; the server half works on its own. Fabric needs **Fabric API**; NeoForge needs only NeoForge itself.

---

## ⌨️ Commands & permissions

**Server plugin** (aliases for the admin command: `/cverify`, `/converify`):

| Command | Description | Permission (default `op`) |
|---------|-------------|---------------------------|
| `/cnt <number>` | Save the stored report for a connection number to a file. | `connectionverify.command.save` |
| `/connectionverify reload` | Reload `config.yml` and the language files. | `connectionverify.command.admin` |
| `/connectionverify info` | Show records in memory, language and log folder. | `connectionverify.command.admin` |
| `/connectionverify version` | Show version and build target. | `connectionverify.command.admin` |

`connectionverify.*` grants all of the above. `/cnt` is meant for the **console** (which always has permission) but also works for ops in-game, with tab-completion of every number issued this session. On the **proxy**, `/cnt <number>` uses the same `connectionverify.command.save` node. On the **mods**, `/cnt <number>` requires permission **level 3**.

---

## ⚙️ Configuration highlights

The server plugin's `config.yml` is fully commented — 30+ options with live reload. Defaults shown:

| Key | Default | What it does |
|-----|---------|--------------|
| `language` | `en` | Bundled `en` and `ko`; drop a file in `lang/` to add your own. Every message is translatable. |
| `connection-number.length` / `allow-leading-zeros` | `4` / `true` | Look of the numbers (e.g. `0421` vs `1000`–`9999`). |
| `logging.network-drops` | `true` | Also number nameless raw drops read from the server log. |
| `file.folder` / `file.format` | `connection` / `text` | Where reports go, and `text` vs `json`. |
| `privacy.mask-ip` / `privacy.hide-uuid` | `false` / `false` | IP masking (e.g. `203.0.x.x`) and UUID redaction for GDPR-conscious networks. |
| `records.max-in-memory` / `expire-after-minutes` | `1000` / `0` | Bound how many numbers stay in memory, and for how long (`0` = no time limit). |
| `capture.*` | on | Switch any detail section on or off. |
| `update-checker.enabled` | `false` | Opt-in Modrinth update check at startup (see below). |

Proxy editions use a `config.properties` with the equivalent number/console/privacy/retention settings.

---

## 🔒 Privacy & network

- **Reports stay local.** Every report — including IPs and system details — is written only to files under the plugin/mod folder. Nothing is uploaded; there is no database and no web server.
- **Privacy controls.** IP masking and UUID redaction are one config toggle each, and any capture section can be disabled entirely.
- **One optional outbound request.** The only time Connection Verify contacts the internet is the **update checker** — **off by default**, opt-in via `update-checker.enabled`. When on, it asks the public Modrinth API **once at startup** whether a newer version exists. No information about your server or players is ever transmitted.

---

## ✅ Installation

- **Paper / Purpur / Folia:** drop `connection-verify-<v>-mc1.21.jar` (MC 1.21.x) or `-mc26.jar` (MC 26.2) into `plugins/` and restart.
- **Velocity:** drop `connection-verify-velocity-<v>.jar` into the proxy's `plugins/`.
- **BungeeCord / Waterfall:** drop `connection-verify-bungee-<v>.jar` into the proxy's `plugins/`.
- **Fabric:** drop `connection-verify-fabric-<v>.jar` into `mods/` **with Fabric API**. Install it on the server, on clients, or both.
- **NeoForge:** drop `connection-verify-neoforge-<v>.jar` into `mods/`. NeoForge is the only requirement.

Download the file whose Minecraft version and loader match your setup.

---

## ❓ FAQ

**Does the client mod have to be installed?** No. The server half works alone; the client companion only *adds* client-only detail, and is silent on servers without the mod.

**Will connecting to my server require players to have the mod?** No — the companion channel is optional, so vanilla and modless clients connect normally.

**Are numbers kept after a restart?** Numbers live in memory for the current session; run `cnt <number>` while the server is up to persist a report to disk.

**Does it run on Spigot / CraftBukkit?** No — the plugin uses Paper-only API. Use the proxy or mod editions for those environments.

---

## 📚 Links

- **Source & full docs:** https://github.com/neramc/connection-verify
- **Wiki:** https://github.com/neramc/connection-verify/wiki
- **Issues / requests:** https://github.com/neramc/connection-verify/issues
- **License:** Apache-2.0

Made with care by [neramc](https://github.com/neramc).
