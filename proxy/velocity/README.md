# Connection Verify — Velocity edition ("control tower")

A [Velocity](https://papermc.io/software/velocity) proxy plugin that acts as a
**network control tower**: it numbers every connection that flows through the
proxy, tracks which backend server each player is routed to, and archives a full
report on demand.

It is a companion to the [Connection Verify server plugin](../../README.md);
run the plugin on your backends and this on the proxy for connection numbers at
both layers.

## What it does

On the proxy console:

```
Join Steve
Connection number 4821
Steve ⇒ lobby [#4821]
Steve ⇒ survival (from lobby) [#4821]
Steve disconnected (SUCCESSFUL_LOGIN) [#4821]
```

- **Numbers every proxy connection** — successful logins *and* failures/denials.
- **Routing view** — logs which backend each player connects to and every switch.
- **`/cnt <number>`** — writes a full report (identity, network, the proxy's
  network-wide server map, runtime & system info) to
  `plugins/connectionverify/connection/<number>.txt`.
- **Config** — `plugins/connectionverify/config.properties` (number length,
  console toggles, IP masking, retention).

## Build

```bash
mvn -f proxy/pom.xml clean package   # builds common + velocity + bungee
# -> proxy/velocity/target/connection-verify-velocity-<version>.jar
```

Requires Java 17+ (Velocity's minimum). Drop the jar into the proxy's
`plugins/` folder.

## Permissions

| Node | Purpose |
|------|---------|
| `connectionverify.command.save` | Use `/cnt` (the console always has it). |
