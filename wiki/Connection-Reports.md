# Connection Reports

A report is the file written by `/cnt <number>`. It contains everything the
server could tell about that connection, grouped into sections.

## Where reports are saved

```
plugins/connection-verify/<file.folder>/<number>.<ext>
```

- `<file.folder>` defaults to `connection`
- `<ext>` is `txt` or `json` depending on `file.format`

## Which connections get a number

A connection is rejected/lost at exactly **one** stage, so each attempt yields
**exactly one** number (normal quits are never counted as failures).

| Stage | Event | Examples |
|-------|-------|----------|
| Successful join | `PlayerJoinEvent` | a normal join |
| Pre-login | `AsyncPlayerPreLoginEvent` | ban, whitelist, server full |
| Login | `PlayerLoginEvent` | plugin disallows the login |
| Connection dropped | `PlayerConnectionCloseEvent` | network errors, timeouts, generic disconnects after auth but before joining |

> **Limitation:** rejections during the *raw protocol handshake before
> authentication* (most notably an incompatible client/server version) are
> closed by the server before any plugin can observe them, so they cannot be
> assigned a number.

## Sections

| Section | Examples of fields |
|---------|--------------------|
| **Capture** | event, capturing thread, primary-thread flag |
| **Identity** | name, display/list name, UUID (+version), skin/cape texture URLs, profile signature |
| **Network** | IP, port, virtual host, brand, protocol, ping, view/sim distance, transfer flag |
| **Client options** | locale, main hand, chat & particle visibility, skin parts |
| **Session** | first join, first played, last login/seen, op, whitelist, ban, game mode |
| **Player state** | health, hunger, XP, movement, pose, air, fire, freeze, potion effects, … |
| **Attributes** | every applicable attribute (value + base) |
| **Equipment & inventory** | armor, hands, held slot, used slots, ender chest |
| **Location** | coordinates, facing, biome, light, chunk, respawn/death points |
| **World** | difficulty, weather, time, moon phase, border, counts |
| **Result** *(failures)* | login stage, result, kick message |
| **Server** | versions, MOTD, TPS, MSPT, op/whitelist/ban counts, worlds, plugins |
| **Runtime / Environment** | Java, OS, memory, GC, threads, classes, uptime |
| **System / hardware** | CPU load, physical & swap memory, committed virtual memory, process info, network interfaces |
| **JVM details** | runtime/spec versions, VM flags, JIT, memory managers & pools |

Each section can be toggled in [Configuration](Configuration) under `capture.*`.

## Defensive capture

Every field is read defensively. A value that is unavailable on the running
server build is shown as `(unavailable: <Reason>)`, and missing values as
`(unknown)`. A single failing field never aborts the report.

## Formats

- **`text`** — an aligned, human-readable report (default).
- **`json`** — pretty-printed JSON with the same data, ideal for log pipelines
  / SIEMs. Set `file.format: json`.

## Retention

Connection numbers live in memory only. Tune `records.max-in-memory` and
`records.expire-after-minutes` in [Configuration](Configuration). Save a report
with `/cnt` while the server is still running.
