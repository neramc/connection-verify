# FAQ

### Where are the reports saved?

`plugins/connection-verify/connection/<number>.txt` (or `.json`). Change the
folder/format in [Configuration](Configuration).

### I ran `cnt <number>` and it says "no connection found".

Connection numbers are kept **in memory** for the current server session, and
are bounded by `records.max-in-memory` / `records.expire-after-minutes`. Save
the report while the server is still running, and before the number is evicted.

### A player joined with the wrong version and got no number. Why?

Version mismatches (and other pre-authentication handshake failures) are closed
by the server before any plugin can observe them, so they cannot be numbered.
See [Connection Reports](Connection-Reports#which-connections-get-a-number).

### Does it work on Spigot/CraftBukkit?

No — it uses Paper-only APIs. Use Paper or a Paper fork (Purpur, etc.). See
[Compatibility](Compatibility).

### Which jar do I download?

`mc1.21` for Minecraft 1.21.4–1.21.x (Java 21+), `mc26` for 26.x (Java 25+).

### Does the plugin phone home / collect data?

No. Nothing leaves your machine unless you opt in to the update checker
(`update-checker.enabled: true`), which only asks Modrinth whether a newer
version exists — no server data is sent.

### How do I protect player IPs?

Set `privacy.mask-ip: true` (and optionally `privacy.hide-uuid: true`). See
[Configuration](Configuration#privacy).

### Will saving a report lag my server?

No. With `file.async-write: true` (default) files are written off the main
thread.

### How do I change the language?

Set `language` and reload. See [Languages](Languages).

### Can I get JSON instead of text reports?

Yes — set `file.format: json`.

### How do I report a bug or request a feature?

Open an issue: https://github.com/neramc/connection-verify/issues
