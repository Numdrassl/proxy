# Numdrassl Permission System

This document lists all available permissions for the Numdrassl Proxy and explains their behavior.

## Wildcard Permissions (`*`)

Permissions in Numdrassl support wildcards (`*`).
Wildcards allow granting access to multiple commands or sub-permissions at once, based on the permission hierarchy. 

Wildcards are evaluated from left to right and only apply to the level they are defined on.

---

## General Commands

| Command                               | Aliases                             | Description                                  | Permission                        |
|---------------------------------------|-------------------------------------|----------------------------------------------|-----------------------------------|
| `/numdrassl`                          | `/proxy`, `/nd`                     | Numdrassl proxy management commands          | `numdrassl.command.numdrassl`     |
| `/proxy perm`                         | `/proxy permission`, `/proxy perms` | Permission management subcommand             | `numdrassl.command.permission`    |
| `/stop`                               | `/end`, `/shutdown`                 | Stop the proxy server                        | `numdrassl.command.stop`          |
| `/find`                               | `/where`, `/locate`                 | Shows which server a player is currently on  | `numdrassl.command.find`          |
| `/metrics`                            | `/perf`, `/performance`, `/stats`   | Show proxy performance metrics               | `numdrassl.command.metrics`       |
| `/sessions`                           | none                                | List all active player sessions              | `numdrassl.command.sessions`      |
| `/server`                             | `/srv`, `/send`                     | List available servers                       | `numdrassl.command.server`        |
| `/server <server-name>`               | `/srv`, `/send`                     | Transfer **yourself** to a server            | `numdrassl.command.server.self`   |
| `/server <server-name> <player-name>` | `/srv`, `/send`                     | Transfer **other players** to a server       | `numdrassl.command.server.others` |
| `/server <server-name> all`           | `/srv`, `/send`                     | Transfer **all players at once** to a server | `numdrassl.command.server.all`    |
| `/auth`                               | none                                | Manage proxy authentication with Hytale      | `Can only be used from console!`  |  

### Examples

| Permission Granted                | Effective Access                     |
|-----------------------------------|--------------------------------------|
| `numdrassl.command.server.self`   | Can transfer **self only**           |
| `numdrassl.command.server.others` | Can transfer **other players**       |
| `numdrassl.command.server.all`    | Can transfer **all players at once** |
| `numdrassl.command.server.*`      | Can transfer **self + others**       |
| `numdrassl.command.server`        | Can view **server list only**        |
| `numdrassl.command.*`             | Full access to **all commands**      |

---

## Default Permission Sets

### Default Player
```
none
```

### Administrator
```
numdrassl.command.numdrassl.permissions
numdrassl.command.*
```
