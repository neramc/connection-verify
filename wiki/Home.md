# Connection Verify

Welcome to the **Connection Verify** wiki.

Connection Verify is a lightweight, privacy-friendly **Paper** plugin that gives
every connection to your server a short **connection number** and lets you save
a complete, log-style report of that connection to a file on demand — for
players who join successfully *and* for those who fail or drop.

## Quick start

1. **[Install](Installation)** the jar that matches your server version.
2. Start the server — a connection number prints to the console on each join:
   ```
   Join Steve
   Connection number 4821
   ```
3. Save the full report:
   ```
   cnt 4821
   ```
   → `plugins/connection-verify/connection/4821.txt`

## Documentation

| Page | What it covers |
|------|----------------|
| [Installation](Installation) | Downloading and installing the right jar |
| [Configuration](Configuration) | Every `config.yml` option |
| [Commands & Permissions](Commands-and-Permissions) | `/cnt`, `/connectionverify`, permission nodes |
| [Connection Reports](Connection-Reports) | What is captured and where reports are saved |
| [Languages](Languages) | Switching language / adding a translation |
| [Compatibility](Compatibility) | Supported versions, branches, Java |
| [Building & Releasing](Building-and-Releasing) | Building locally, CI, Modrinth auto-deploy |
| [FAQ](FAQ) | Common questions and limitations |

## At a glance

- A number for **every** connection — success and failure.
- **Exhaustive** reports: identity, network, player state, attributes,
  equipment, world, deep **system & JVM** diagnostics, and more.
- **Text or JSON** output; optional **IP masking** and **UUID redaction**.
- **Localizable** (English & Korean bundled) with live `/connectionverify reload`.
- Async file writes — never lags the server.

> License: Apache-2.0 · Copyright 2026 neramc
