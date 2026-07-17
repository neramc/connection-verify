# Connection Verify — Fabric mod (server + client companion)

A [Fabric](https://fabricmc.net/) mod for **Minecraft 1.21.4** that brings the
Connection Verify idea to the modded world, on **both** sides:

- **Server side** — numbers every connection on join, prints the number to the
  console, and writes a full report with `/cnt <number>` (op level 3). Reports
  land in `<world|run>/connection-verify/<number>.txt`.
- **Client companion** — when you join a server that runs this mod, it gathers
  advanced client-only details the server cannot otherwise see (loaded mod list,
  FPS, view distance, client brand, OS, Java, memory) and sends them over a
  custom payload channel; the server merges them into that connection's report.
  It stays silent on vanilla or modless servers.

The client half is optional: install it only where you want the extra detail.
The server half works on its own.

## Console output

```
Join Steve
Connection number 4821
```

## Build

Requires JDK 21 (Minecraft 1.21.4). From this directory:

```bash
gradle build      # or ../../gradlew if you add a wrapper
# -> build/libs/connection-verify-fabric-<version>.jar
```

Drop the jar in `mods/` alongside **Fabric API**.

## Networking

A single client → server custom payload, `connectionverify:client_info`,
registered on both sides. The client only sends when
`ClientPlayNetworking.canSend(...)` reports the server can receive it.
