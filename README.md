# Connection Verify

[![Build](https://github.com/neramc/connection-verify/actions/workflows/build.yml/badge.svg)](https://github.com/neramc/connection-verify/actions/workflows/build.yml)

A state-of-the-art **Paper** plugin that lets server operators verify the
connection information of every player who joins — or fails to join — the
server.

- **Target Minecraft / Paper version:** `1.21.10`
- **Plugin version:** `1.21.10`
- **Default language:** English

## What it does

* When a player **successfully connects**, the console prints the join line with
  a random 4-digit **connection number** right underneath it (console only):

  ```
  Join Steve
  Connection number 4821
  ```

* When a connection **fails**, a connection number is printed as well:

  ```
  Connection failed: Steve (KICK_BANNED)
  Connection number 7392
  ```

  Failures are caught at every stage the Paper API exposes:

  | Stage | Event | Examples |
  |-------|-------|----------|
  | Pre-login | `AsyncPlayerPreLoginEvent` | ban, whitelist, server full |
  | Login | `PlayerLoginEvent` | plugin denials |
  | Validation | `PlayerConnectionValidateLoginEvent` | login/configuration validation |
  | Connection dropped | `PlayerConnectionCloseEvent` | `lost connection: ...`, network errors, timeouts, generic "Connection failed" disconnects after authentication but before joining |

  Each failed attempt yields **exactly one** connection number: a connection is
  rejected at a single stage, the connection-close catch-all skips players who
  actually joined (normal quits), and it de-duplicates against any failure
  already reported by the stages above.

  > Note: rejections that happen during the *raw protocol handshake, before
  > authentication* — most notably an incompatible client/server version — are
  > closed by the server before any plugin-observable event fires, so they
  > cannot be assigned a number.

* Typing `cnt <number>` in the console writes the full connection details and
  metadata for that number to a text file:

  ```
  cnt 4821
  ```

  creates

  ```
  plugins/connection-verify/connection/4821.txt
  ```

Each log file is exhaustive — it records **everything** observable about the
connection, grouped into sections:

* **Capture** — event type, capturing thread, primary-thread flag.
* **Identity** — name, display name, UUID, entity id.
* **Profile** (failures) — Mojang profile name/id, completeness, textures.
* **Network** — socket address, IP, port, virtual host, raw/real IP, hostname,
  client brand, protocol version, ping, transfer flag, client/effective/
  simulation view distances.
* **Client options** — locale, main hand, chat & particle visibility, chat
  colors, server-listing & text-filtering flags, skin parts.
* **Session** — first join, first played, last login/seen, op, whitelist, ban,
  game mode.
* **Player state** — health/max health, hunger, saturation, exhaustion, xp,
  movement speeds, flight, sneaking/sprinting, pose, air, fire, fall distance,
  velocity, current input, spawn reason, scoreboard tags, potion effects.
* **Location / World** — coordinates, respawn & last-death locations, world
  difficulty, time, weather, pvp, entity/player counts, spawn.
* **Result** (failures) — login stage, result, kick message.
* **Server** — software, versions, MOTD, bind address, online mode, whitelist,
  hardcore, game mode, distances, throttle, TPS, MSPT, tick manager, plugins.
* **Runtime / Environment** — Java/JVM, OS, CPU, memory, uptime, working dir.

Every field is captured defensively: a getter that is unavailable on a given
server build is recorded as `(unavailable: …)` and missing values as
`(unknown)`, so a log is never aborted by a single field.

## Commands & permissions

| Command         | Description                                              | Permission             | Default |
|-----------------|----------------------------------------------------------|------------------------|---------|
| `cnt <number>`  | Save the connection log for a 4-digit number to a file.  | `connectionverify.cnt` | op      |

The command is intended for the **console** (which always has permission) but
also works in-game for operators. Tab-completion suggests every connection
number recorded during the current server session.

## Configuration

`plugins/connection-verify/config.yml`:

```yaml
# Print a "Connection number" line under the join message for successful joins.
log-successful-connections: true

# Print a "Connection number" line for denied connection attempts.
log-failed-connections: true

# Sub-folder (inside this plugin's data folder) where logs are written.
log-folder: connection
```

## How connection numbers work

Connection numbers are generated per connection attempt and kept **in memory**
for the lifetime of the server session. Run `cnt <number>` while the server is
still running to persist that connection's details to disk. Numbers are not
retained across restarts.

## Download

Pre-built jars are published automatically to
[**Releases**](https://github.com/neramc/connection-verify/releases). Grab
`connection-verify-<version>.jar`, drop it into your server's `plugins/` folder
and restart.

## Building

Requires JDK 21 and Maven.

```bash
mvn clean package
```

The plugin jar is produced at `target/connection-verify-1.21.10.jar`.

## Continuous integration & releases

Every push to `main` and every pull request is built with JDK 21 by the
[`Build`](.github/workflows/build.yml) GitHub Actions workflow, which uploads
the compiled jar as a workflow artifact.

When a build of `main` succeeds, the jar is published to GitHub Releases under
the tag `v<plugin-version>` (e.g. `v1.21.10`). The release for the current
version is refreshed on each successful `main` build, so bumping the
`<version>` in `pom.xml` is what creates a brand-new release.

## License

Licensed under the Apache License 2.0 — see [LICENSE.md](LICENSE.md).
