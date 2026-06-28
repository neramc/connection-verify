# Configuration

All settings live in `plugins/connection-verify/config.yml`. After editing, run
`/connectionverify reload` (no restart needed).

## General

| Key | Default | Description |
|-----|---------|-------------|
| `config-version` | `1` | Internal; do not edit. Lets the plugin migrate old configs. |
| `language` | `en` | Language code; a matching `lang/<code>.yml` must exist. See [Languages](Languages). |
| `debug` | `false` | Extra diagnostic logging. |

## Connection number

| Key | Default | Description |
|-----|---------|-------------|
| `connection-number.length` | `4` | Number of digits (1–9). |
| `connection-number.allow-leading-zeros` | `true` | `0421` style vs `1000`–`9999`. |

## Logging & console

| Key | Default | Description |
|-----|---------|-------------|
| `logging.successful-connections` | `true` | Record (and number) successful joins. |
| `logging.failed-connections` | `true` | Record (and number) failed/dropped attempts. |
| `logging.network-drops` | `true` | Also number nameless raw drops the server logs as `/<ip>:<port> lost connection: …` (no Bukkit event; read from the log via a Log4j2 watcher). Needs `failed-connections`. |
| `console.announce-successful` | `true` | Print the number for successful joins. |
| `console.announce-failed` | `true` | Print the number for failed connections. |
| `console.use-colors` | `true` | Colored console output (disable for plain logs). |

## Saved files

| Key | Default | Description |
|-----|---------|-------------|
| `file.folder` | `connection` | Sub-folder under the data folder. |
| `file.format` | `text` | `text` or `json`. |
| `file.overwrite-existing` | `true` | Allow `/cnt` to overwrite an existing file. |
| `file.async-write` | `true` | Write off the main thread (recommended). |

## Capture sections

Toggle any section on/off. See [Connection Reports](Connection-Reports).

| Key | Default |
|-----|---------|
| `capture.identity` | `true` |
| `capture.network` | `true` |
| `capture.client-options` | `true` |
| `capture.session` | `true` |
| `capture.player-state` | `true` |
| `capture.location` | `true` |
| `capture.world` | `true` |
| `capture.server` | `true` |
| `capture.runtime` | `true` |
| `capture.system` | `true` |

## Privacy

| Key | Default | Description |
|-----|---------|-------------|
| `privacy.mask-ip` | `false` | Mask IPs (e.g. `203.0.x.x`). |
| `privacy.mask-ip-segments` | `2` | Trailing IPv4 segments to hide (1–4). |
| `privacy.hide-uuid` | `false` | Redact UUIDs in output and logs. |

## Retention & updates

| Key | Default | Description |
|-----|---------|-------------|
| `records.max-in-memory` | `1000` | Max records kept in memory (0 = unlimited; oldest evicted first). |
| `records.expire-after-minutes` | `0` | Forget records older than this (0 = whole session). |
| `update-checker.enabled` | `false` | Opt-in startup check against Modrinth (no server data sent). |
