# Connection Verify — BungeeCord edition ("control tower")

A [BungeeCord](https://www.spigotmc.org/wiki/bungeecord/) / Waterfall proxy
plugin that acts as a **network control tower**: it numbers every connection
that flows through the proxy, tracks which backend server each player is routed
to, and archives a full report on demand.

It shares its core with the [Velocity edition](../velocity/README.md) (see
[`proxy/common`](../common)) and is a companion to the
[Connection Verify server plugin](../../README.md).

## What it does

On the proxy console:

```
Join Steve
Connection number 4821
Steve => lobby [#4821]
Steve => survival (from lobby) [#4821]
Steve disconnected [#4821]
```

- **Numbers every proxy connection** — successful logins, plus logins denied by
  another plugin.
- **Routing view** — logs which backend each player connects to and every switch.
- **`/cnt <number>`** — writes a full report (identity, network, the proxy's
  network-wide server map, runtime & system info) to
  `plugins/ConnectionVerify/connection/<number>.txt`.
- **Config** — `plugins/ConnectionVerify/config.properties`.

## Build

```bash
mvn -f proxy/pom.xml clean package   # builds common + velocity + bungee
# -> proxy/bungee/target/connection-verify-bungee-<version>.jar
```

Requires Java 17+. Drop the jar into the proxy's `plugins/` folder.

## Permissions

| Node | Purpose |
|------|---------|
| `connectionverify.command.save` | Use `/cnt` (the console always may). |
