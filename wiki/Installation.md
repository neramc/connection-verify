# Installation

## 1. Pick the right jar

Connection Verify ships two builds from one codebase. Download the one that
matches your server from the
[**Releases**](https://github.com/neramc/connection-verify/releases) page (or
from Modrinth):

| File | Minecraft / Paper | Java |
|------|-------------------|------|
| `connection-verify-<version>-mc1.21.jar` | 1.21.4 – 1.21.x | 21+ |
| `connection-verify-<version>-mc26.jar` | 26.x | 25+ |

Using the wrong jar will simply fail to load (a 26.x jar needs Java 25; a 1.21
jar targets the 1.21 API). See [Compatibility](Compatibility) for details.

## 2. Install

1. Stop the server (or prepare for a restart).
2. Drop the jar into your server's `plugins/` folder.
3. Start the server.

On first start the plugin creates:

```
plugins/connection-verify/
├── config.yml          # all settings (see Configuration)
├── lang/
│   ├── en.yml
│   └── ko.yml
└── connection/         # saved reports land here
```

## 3. Verify it works

When a player joins, the console shows two lines:

```
Join <player>
Connection number 1234
```

Run `cnt 1234` in the console to write the report to
`plugins/connection-verify/connection/1234.txt`.

## Updating

Replace the jar with the new version and restart. Your `config.yml` and `lang/`
files are preserved; any new message keys fall back to the bundled English
defaults automatically.

> Tip: enable the opt-in update checker (`update-checker.enabled: true`) to be
> told on startup when a newer version is available.
