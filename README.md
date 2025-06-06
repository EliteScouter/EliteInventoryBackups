# Elite Holograms

A lightweight and powerful Minecraft mod for creating and managing holographic displays in-game. Inspired by AdvancedHolograms, Elite Holograms offers robust features for Forge (1.19.2, 1.20.x) and NeoForge (1.21.1).

## Features

- Create persistent holograms that stay loaded across server restarts.
- Manage multiple lines (add, insert, remove, set).
- Position control (create at your location, teleport to holograms).
- **Comprehensive built-in placeholder system** with server and player-specific variables, including `%player_rank%`.
- **Advanced permission system** with support for LuckPerms, FTB Ranks, or operator (OP level 2) fallback.
- Easy-to-use commands with **tab completion** and intuitive syntax.
- Performance optimized for servers with **proper shutdown handling** and efficient hologram rendering.
- Support for Minecraft 1.19.2 (Forge), 1.20.x (Forge), and 1.21.1 (NeoForge).

## Placeholder System

Elite Holograms includes a built-in placeholder system that updates in real-time. No external placeholder API is required.

### Server Placeholders
These show the same information for all players:
- `%players%` - Current online player count.
- `%maxplayers%` - Server maximum players.
- `%tps%` - Server TPS (Ticks Per Second).
- `%uptime%` - Server uptime in HH:MM:SS format.
- `%memory%` - Memory usage in "used/max MB (percentage%)" format.
- `%server_time%` - Real world time in HH:MM:SS format.

### Player-Specific Placeholders
These show different information for each player viewing the hologram:
- `%player%` - Player's display name.
- `%player_rank%` - Player's rank (from LuckPerms/FTB Ranks, or "OP"/"Player").
- `%player_health%` - Current/max health (e.g., "20.0/20.0").
- `%player_level%` - Experience level.
- `%player_world%` - World name (Overworld/Nether/End/custom).
- `%player_coords%` - X, Y, Z coordinates.
- `%player_gamemode%` - Game mode (Creative/Survival/Adventure/Spectator).

### Example Usage
```
/eh create welcome &bWelcome &f%player%&b to the server!
/eh addline welcome &7Your rank: &e%player_rank%
/eh addline welcome &7Players online: &a%players%&7/&a%maxplayers%
/eh addline welcome &7Server TPS: &a%tps%
```

## Commands

**Permissions:** Commands require either specific permission nodes (if using a supported permission mod like LuckPerms or FTB Ranks) or operator (OP level 2) status. See the "Permissions" section below or `PERMISSIONS.md` for details.

| Command                     | Description                                  | Permission Node Suffix |
| --------------------------- | -------------------------------------------- | ---------------------- |
| `/eh create <id> <text...>` | Create a new hologram at your location       | `.create`              |
| `/eh list [page]`           | List all holograms on the server             | `.list`                |
| `/eh delete <id>`           | Delete a hologram                            | `.delete`              |
| `/eh addline <id> <text...>`| Add a line to a hologram                     | `.edit`                |
| `/eh setline <id> <#> <text...>`| Change the text on a specific line         | `.edit`                |
| `/eh removeline <id> <#>`   | Remove a line from a hologram                | `.edit`                |
| `/eh insertline <id> <#> <text...>`| Insert a line at a specific position  | `.edit`                |
| `/eh movehere <id>`         | Move a hologram to your current location     | `.edit` (as it modifies) |
| `/eh near [page]`           | List nearby holograms                        | `.near`                |
| `/eh reload`                | Reload holograms from storage                | `.admin`               |
| `/eh teleport <id>`         | Teleport to a hologram                       | `.teleport`            |
| `/eh copy <source_id> <new_id>`| Copy an existing hologram to a new one    | `.create` (as it creates) |
| `/eh info <id>`             | Display information about a hologram         | `.info`                |

All commands support **tab completion** for hologram IDs and relevant parameters. The base permission is `eliteholograms`. For example, to use `/eh create`, a player would need `eliteholograms.create`.

## Permissions

Elite Holograms features a flexible permission system:

- **Supported Systems:** Automatically detects and integrates with LuckPerms and FTB Ranks.
- **Fallback:** If no supported permission mod is found, commands default to requiring operator (OP level 2) status.
- **Granular Nodes:** Assign specific permissions for different actions. The base node is `eliteholograms`.
    - `eliteholograms.create` - Allows creation of holograms (`/eh create`, `/eh copy`).
    - `eliteholograms.delete` - Allows deletion of holograms (`/eh delete`).
    - `eliteholograms.edit` - Allows modification of existing holograms (`/eh addline`, `/eh setline`, `/eh removeline`, `/eh insertline`, `/eh movehere`).
    - `eliteholograms.list` - Allows listing all holograms (`/eh list`).
    - `eliteholograms.info` - Allows viewing detailed hologram information (`/eh info`).
    - `eliteholograms.near` - Allows listing nearby holograms (`/eh near`).
    - `eliteholograms.teleport` - Allows teleporting to holograms (`/eh teleport`).
    - `eliteholograms.admin` - Allows administrative actions like `/eh reload`.
- **Console & Command Blocks:** Always have full permission to execute hologram commands.

For a detailed guide on setting up permissions, please see `PERMISSIONS.md`.

## Installation

1. Download the latest version for your Minecraft version (Forge/NeoForge) from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/elite-holograms) (link to be added).
2. Place the JAR file in your server's `mods` folder.
3. If using, ensure LuckPerms or FTB Ranks is installed for granular permissions.
4. Restart the server.
5. Use `/eh create` to start creating holograms!

## Performance

- Optimized background thread management for placeholder updates and hologram visibility.
- Proper server shutdown handling to prevent hanging and ensure data is saved.
- Efficient placeholder processing with robust error handling.
- Memory-conscious hologram rendering.
- Improved hologram entity synchronization for smoother multiplayer visibility, especially on NeoForge 1.21.1.

## Version Support

- **Forge19**: Minecraft 1.19.2 (EliteHolograms version `1.19.2-1.0.3`)
- **Forge20**: Minecraft 1.20.x (EliteHolograms version `1.20.x-1.0.3`)
- **Neo21**: Minecraft 1.21.1 (EliteHolograms version `1.21.1-1.0.2`)

All versions aim for feature parity, including the advanced permission system and full placeholder support.

## License

This project is licensed under the [MIT License](LICENSE).

