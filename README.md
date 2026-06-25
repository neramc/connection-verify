# Connection Verify

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

* When a connection **fails** (ban, whitelist, server full, kicked during
  login, ...), a connection number is printed as well:

  ```
  Connection failed: Steve (KICK_BANNED)
  Connection number 7392
  ```

* Typing `cnt <number>` in the console writes the full connection details and
  metadata for that number to a text file:

  ```
  cnt 4821
  ```

  creates

  ```
  plugins/connection-verify/connection/4821.txt
  ```

Each log file contains everything captured for that connection: identity
(name, UUID, display name, entity id), network details (IP, port, virtual host,
client brand, protocol version, ping, locale), session data (first join, first
played, last login, op/whitelist status, game mode), player state, location,
the join message, the login result/kick message for failures, and server
context.

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

## Building

Requires JDK 21 and Maven.

```bash
mvn clean package
```

The plugin jar is produced at `target/connection-verify-1.21.10.jar`. Drop it
into your server's `plugins/` folder and restart.

## License

Licensed under the Apache License 2.0 — see [LICENSE.md](LICENSE.md).
