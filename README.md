# Connection Verify

[![Build](https://github.com/neramc/connection-verify/actions/workflows/build.yml/badge.svg)](https://github.com/neramc/connection-verify/actions/workflows/build.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE.md)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4%20--%2026.x-brightgreen.svg)](#compatibility)

**Connection Verify** is a lightweight Paper plugin that gives every connection
to your server a short, memorable **connection number** and lets you save a
complete, human‑readable report of that connection to a file whenever you need
it.

It works for players who join successfully **and** for players who fail to
connect (bans, whitelist, server full, kicked during login, dropped/lost
connections). It is designed for server operators and admins who want a quick,
reliable way to look up *exactly* who connected, from where, and with what
client — without digging through noisy logs.

> No database. No web server. No data leaves your machine. Just a number on the
> console and a text (or JSON) file when you ask for one.

---

## How it works

**1. Someone connects.** Connection Verify prints two lines to the **console**:

```
Join Steve
Connection number 4821
```

If a connection **fails or is dropped**, you get a number for that too:

```
Connection failed: Steve (KICK_BANNED)
Connection number 7392
```

…including the nameless raw drops the server logs as
`/<ip>:<port> lost connection: …`, which have no Bukkit event at all:

```
Connection dropped: 203.0.113.7:51234 (Connection failed. Please try again or contact an administrator.) [Early failure]
Connection number 1574
```

The tag in brackets is a quick classification of **why** it dropped (e.g.
`Timeout`, `Connection reset`, `Early failure`); the saved report adds the full
reason text, a plain‑English likely cause, and any exception from the log.

**2. You want the details.** Type the number in the console (or in‑game as an
operator):

```
cnt 4821
```

**3. Connection Verify writes a full report** to:

```
plugins/connection-verify/connection/4821.txt
```

That file contains *everything* the server can tell you about the connection.

---

## What's in a report

Each report is grouped into clearly labelled sections:

| Section | Examples of what it captures |
|---------|------------------------------|
| **Capture** | which event fired, thread, timestamp |
| **Identity** | name, display name, UUID, entity id |
| **Network** | IP, port, virtual host, client brand, protocol, ping, view/sim distance, transfer flag |
| **Client options** | locale, main hand, chat & particle visibility, skin parts, server‑listing/text‑filtering flags |
| **Session** | first join, first played, last login/seen, op, whitelist, ban, game mode |
| **Player state** | health, hunger, XP, movement, pose, air, fire, velocity, potion effects, scoreboard tags |
| **Location / World** | coordinates, respawn & death points, difficulty, weather, entity/player counts |
| **Result** *(failures)* | login stage, result/kick message, and — for drops — the disconnect reason, a *category* and *likely cause*, plus any exception from the log |
| **Server** | software, versions, MOTD, TPS, MSPT, tick state, loaded plugins |
| **Runtime** | Java, OS, CPU, memory, GC, threads, classes, uptime |
| **System / hardware** | CPU load, physical & swap memory, committed virtual memory, process id/CPU time, network interfaces, data model |
| **JVM details** | runtime/spec versions, VM flags, JIT compiler & compile time, memory managers & pools |

Every field is captured defensively: anything a particular server build can't
provide is recorded as `(unavailable: …)`, and empty values as `(unknown)`, so
a report is never broken by a single missing value.

Prefer machine‑readable logs? Set `file.format: json` and reports are written as
clean, pretty‑printed JSON instead.

---

## Which connections get a number?

A connection is rejected (or lost) at exactly **one** stage, so each attempt
gets **exactly one** number — no duplicates, and normal player quits are never
counted as failures.

| Stage | Covered cases |
|-------|---------------|
| Pre‑login | ban, whitelist, server full |
| Login | plugin disallows the login |
| Connection dropped | network errors, timeouts, generic *“lost connection”* disconnects after authentication but before joining |
| Raw drop | nameless sockets the server logs as `/<ip>:<port> lost connection: …` — closed before a profile is ever negotiated (e.g. *“Connection failed. Please try again or contact an administrator.”*) |

The first three stages come from Bukkit/Paper events. The **raw drop** stage has
no event at all — those connections die before a name or profile exists — so
Connection Verify reads them straight from the server log through a lightweight
Log4j2 watcher (see `logging.network-drops`). The leading `/` is what tells a
nameless raw drop apart from a named disconnect, so nothing is ever counted
twice.

> **Note:** a drop is only numbered if the server actually logs a
> `lost connection` line for it. A handshake closed so early that the server
> emits no log line at all still cannot be observed — a platform limitation, not
> a plugin bug.

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/cnt <number>` | Save the stored report for a connection number to a file. | `connectionverify.command.save` |
| `/connectionverify reload` | Reload `config.yml` and the language files. | `connectionverify.command.admin` |
| `/connectionverify info` | Show records in memory, language and log folder. | `connectionverify.command.admin` |
| `/connectionverify version` | Show version and build target. | `connectionverify.command.admin` |

Aliases for the admin command: `/cverify`, `/converify`.

## Permissions

| Node | Default | Description |
|------|---------|-------------|
| `connectionverify.command.save` | op | Use `/cnt`. |
| `connectionverify.command.admin` | op | Use `/connectionverify`. |
| `connectionverify.*` | op | All of the above. |

The `/cnt` command is intended for the **console** (which always has
permission) but also works for operators in‑game. Tab‑completion suggests every
connection number issued during the current server session.

---

## Configuration

The default `config.yml` is fully commented. Highlights:

- **`language`** – `en` and `ko` are bundled; drop a new file in `lang/` to add
  your own. Every console and command message is translatable.
- **`connection-number.length` / `allow-leading-zeros`** – control the look of
  the numbers (e.g. 4‑digit `0421` vs `1000`‑`9999`).
- **`logging.*` / `console.*`** – choose whether successful and/or failed
  connections are recorded and announced, and whether output is colored.
  `logging.network-drops` (on by default) additionally numbers nameless raw
  drops read from the server log.
- **`file.format`** – `text` or `json`. Plus `folder`, `overwrite-existing`,
  and `async-write` (writes happen off the main thread, so saving never lags
  your server).
- **`capture.*`** – switch any of the nine detail sections on or off.
- **`privacy.*`** – `mask-ip` (e.g. `203.0.x.x`), `mask-ip-segments`, and
  `hide-uuid` for GDPR‑conscious networks.
- **`records.max-in-memory` / `expire-after-minutes`** – bound how many numbers
  are kept in memory and for how long.
- **`update-checker.enabled`** – optional, **off by default**; when on, the
  plugin asks Modrinth once at startup whether a newer version exists. No
  information about your server is sent.

Edit the file and run `/connectionverify reload` — no restart required.

---

## Installation

1. Download the jar that matches your server version (see [Compatibility](#compatibility)).
2. Drop it into your server's `plugins/` folder.
3. Start the server. A default `config.yml` and `lang/` folder are created.

Connection numbers are kept in memory for the current server session; run
`/cnt <number>` while the server is still running to save a report to disk.

---

## Compatibility

Connection Verify is published as two builds from a single codebase:

| Build | Minecraft / Paper | Java |
|-------|-------------------|------|
| `connection-verify-<version>-mc1.21.jar` | 1.21.4 – 1.21.x | 21+ |
| `connection-verify-<version>-mc26.jar` | 26.x | 25+ |

Pick the jar for your server's version. Both are built and released
automatically.

---

## Building from source

Requires Git and Maven. JDK 21 builds the 1.21 line; JDK 25 builds the 26 line.

```bash
# 26.x build (default, needs JDK 25)
mvn clean package

# 1.21.x build (needs JDK 21+)
mvn clean package -Dpaper.version=1.21.4-R0.1-SNAPSHOT \
                  -Dmaven.compiler.release=21 \
                  -Dapi.version=1.21 -Dtarget.id=mc1.21 \
                  -Dmc.range="1.21.4 - 1.21.x"
```

Jars are produced in `target/`.

### Branches

| Branch | Purpose |
|--------|---------|
| `main` | Latest code (tracks the newest supported line). |
| `26.x` | The Minecraft 26.x line. |
| `1.21.x` | The Minecraft 1.21.x line. |

---

## Versioning

The plugin version (`MAJOR.MINOR.PATCH`) lives in `pom.xml` as `<revision>` and
is **bumped automatically by CI** on every push to `main`, based on how many
files changed in that push:

| Files changed | Result |
|---------------|--------|
| 1 – 21 | patch + 1 (e.g. `2.0.0` → `2.0.1`) |
| 22 – 104 | minor + 1, patch → 0 (e.g. `2.0.5` → `2.1.0`) |
| 105+, or when minor would reach 10 | major + 1, minor & patch → 0 (e.g. `2.9.5` → `3.0.0`) |

`plugin.yml` and `build-info.properties` derive the version from `pom.xml`, so
it is defined in exactly one place. The bump runs before the build, is committed
back to `main` (with a skip-CI marker so it doesn't start another run), and the
same version is applied to both jars and the release tag.

---

## License

Licensed under the **Apache License, Version 2.0**. Copyright 2026 neramc.
See [LICENSE.md](LICENSE.md) and [NOTICE](NOTICE).
