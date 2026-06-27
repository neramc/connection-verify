# Commands & Permissions

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `cnt <number>` | Save the stored report for a connection number to a file. | `connectionverify.command.save` |
| `/connectionverify reload` | Reload `config.yml` and the language files. | `connectionverify.command.admin` |
| `/connectionverify info` | Show records in memory, language and log folder. | `connectionverify.command.admin` |
| `/connectionverify version` | Show version and build target. | `connectionverify.command.admin` |

Admin command aliases: `/cverify`, `/converify`.

### `cnt`

Primarily intended for the **console** (which always has permission), but
operators can also use it in-game. The argument must be exactly
`connection-number.length` digits. Tab-completion suggests every connection
number issued during the current server session.

```
cnt 4821
→ Saved connection log 4821 [SUCCESS, 7421 bytes] -> .../connection/4821.txt
```

## Permissions

| Node | Default | Grants |
|------|---------|--------|
| `connectionverify.command.save` | op | `/cnt` |
| `connectionverify.command.admin` | op | `/connectionverify` |
| `connectionverify.*` | op | All of the above |

Set defaults or grant to roles with your permission manager (LuckPerms, etc.).
For example, to let a "moderator" group save reports but not reload:

```
/lp group moderator permission set connectionverify.command.save true
```
