# Compatibility

Connection Verify supports a wide range of Minecraft versions across both the
old (`1.21.x`) and new (`26.x`) Paper versioning schemes, built from a single
codebase using only stable APIs.

## Builds

| Build | Minecraft | Java | `api-version` |
|-------|-----------|------|---------------|
| `mc1.21` | 1.21.4 – 1.21.x | 21+ | `1.21` |
| `mc26` | 26.2 | 25+ | `26.2` |

Download the jar matching your server. A `mc26` jar will not run on Java 21, and
a `mc1.21` jar targets the 1.21 API. The `mc26` jar declares `api-version: 26.2`,
so Paper will not load it on an older 26.x server.

## Server software

Built against the **Paper** API and published for the **paper**, **purpur** and
**folia** loaders:

- **Paper / Purpur / Pufferfish** — run the jar unchanged (Pufferfish has no
  Modrinth loader tag of its own; it uses `paper`).
- **Folia** — fully supported. The plugin uses Paper's region schedulers
  (`GlobalRegionScheduler` / `AsyncScheduler`) throughout and declares
  `folia-supported: true`, so it is Folia-native.
- **Not supported:** Spigot/CraftBukkit (the plugin uses Paper-only APIs —
  Adventure, MiniMessage, client options, connection events), proxies
  (Velocity/BungeeCord/Waterfall — a different platform and API), and mod
  loaders (Fabric/Forge/NeoForge/Quilt — a plugin cannot load as a mod).
  Supporting those would require separate, purpose-built editions.

## Branches

| Branch | Purpose |
|--------|---------|
| `main` | Latest code (tracks the newest supported line). |
| `26.x` | The Minecraft 26.x line. |
| `1.21.x` | The Minecraft 1.21.x line. |

The source is identical across branches; only the build target (`paper.version`,
`maven.compiler.release`, `api.version`) differs, set via Maven properties.

## Notes

- Nameless raw drops the server logs as `/<ip>:<port> lost connection: …` are
  numbered via the `logging.network-drops` Log4j2 watcher. Only handshakes
  closed before the server writes **any** log line (e.g. some incompatible-client
  cases) cannot be numbered — see
  [Connection Reports](Connection-Reports#which-connections-get-a-number).
- Fields not present on a given build degrade to `(unavailable: …)` rather than
  breaking a report.
