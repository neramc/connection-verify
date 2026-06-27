# Compatibility

Connection Verify supports a wide range of Minecraft versions across both the
old (`1.21.x`) and new (`26.x`) Paper versioning schemes, built from a single
codebase using only stable APIs.

## Builds

| Build | Minecraft / Paper | Java | `api-version` |
|-------|-------------------|------|---------------|
| `mc1.21` | 1.21.4 – 1.21.x | 21+ | `1.21` |
| `mc26` | 26.x | 25+ | `26.2` |

Download the jar matching your server. A `mc26` jar will not run on Java 21, and
a `mc1.21` jar targets the 1.21 API.

## Server software

Built against the **Paper** API. Paper forks (Purpur, Pufferfish, etc.) are
expected to work. Spigot/CraftBukkit are **not** supported — the plugin uses
Paper-only APIs (Adventure, MiniMessage, client options, connection events).

## Branches

| Branch | Purpose |
|--------|---------|
| `main` | Latest code (tracks the newest supported line). |
| `26.x` | The Minecraft 26.x line. |
| `1.21.x` | The Minecraft 1.21.x line. |

The source is identical across branches; only the build target (`paper.version`,
`maven.compiler.release`, `api.version`) differs, set via Maven properties.

## Notes

- Failures during the **pre-authentication handshake** (e.g. an incompatible
  client version) cannot be assigned a number — see
  [Connection Reports](Connection-Reports#which-connections-get-a-number).
- Fields not present on a given build degrade to `(unavailable: …)` rather than
  breaking a report.
