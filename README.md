# Elite Inventory Backups

A comprehensive Minecraft Forge mod that automatically backs up player inventories and data to protect against item loss. Features robust database support, modded inventory integration, and easy restoration commands.

## Features

- **Automatic Backups**: Create backups on player login, logout, and death events
- **Database Support**: Choose between H2 (local file-based) or MySQL/MariaDB for storage
- **Modded Integration**: Built-in support for popular mods like Curios
- **Sequential Backup System**: Numbered backups (1, 2, 3...) for easy management
- **Comprehensive Data Storage**: Backs up main inventory, armor, offhand, ender chest, experience, position, and modded inventories
- **Manual Backup Creation**: Create backups on-demand via commands
- **Easy Restoration**: Restore any backup with simple commands
- **Backup Limits**: Configurable maximum backups per player with automatic cleanup
- **Permission System**: Supports LuckPerms and FTB Ranks with OP fallback
- **View System**: Preview backup contents before restoring

## Backup Data Stored

For each backup, the mod stores:
- **Standard Inventories**: Main inventory (hotbar + storage), armor slots, offhand, ender chest
- **Player Data**: Experience level/progress, position (world, x, y, z coordinates)
- **Event Information**: Backup type (login/logout/death/manual), timestamp, cause of death (if applicable)
- **Modded Inventories**: Curios items
- **Generic NBT**: Full player NBT data as fallback for unsupported mods

## Commands

All commands use the base `/eib` (Elite Inventory Backups) and require admin permissions.

| Command | Description | Example |
|---------|-------------|---------|
| `/eib backup <player>` | Create a manual backup for a player | `/eib backup Steve` |
| `/eib list <player> [page]` | List all backups for a player | `/eib list Steve` |
| `/eib view <player> <backup#> [section]` | View backup contents in GUI | `/eib view Steve 5 main` |
| `/eib restore <player> <backup#> [section]` | Restore a backup (or specific section) | `/eib restore Steve 3` |
| `/eib removeall <player>` | Remove all backups for a player | `/eib removeall Steve` |

### Command Details

#### View Sections
When using `/eib view`, you can specify sections:
- `main` - Main inventory (hotbar + storage slots)
- `armor` - Armor slots (helmet, chestplate, leggings, boots)
- `offhand` - Offhand slot
- `enderchest` - Ender chest contents
- `curios` - Curios items (if Curios mod is installed)

#### Restore Sections
The restore command supports the same sections as view. If no section is specified, all sections are restored.

## Configuration

Configuration file: `config/eliteinventorybackups/config.toml`

### Database Settings

```toml
[database]
    # Database type: "H2" (local file) or "MYSQL" (external server)
    databaseType = "H2"
    
    [database.mysql]
        mysqlHost = "localhost"
        mysqlPort = 3306
        mysqlDatabase = "elite_inventory_backups"
        mysqlUsername = "eib_user"
        mysqlPassword = "password"
        mysqlUseSSL = false
        mysqlExtraParams = "serverTimezone=UTC"
```

### Backup Settings

```toml
[retention]
    # Maximum backups per player (0 = unlimited)
    maxBackupsPerPlayer = 24

[event_snapshots]
    # Enable automatic backups
    enableDeathSnapshots = true
    enableLoginSnapshots = true
    enableLogoutSnapshots = true

[mod_integrations]
    # Enable specific mod integrations
    enableCuriosBackup = true
    enableGenericNbtBackup = true
    autoDetectModdedInventories = true
```

## Database Options

### H2 Database (Default)
- **Pros**: No setup required, works out of the box
- **Cons**: Single-server only, limited concurrent access
- **Best for**: Single servers, testing, smaller communities
- **Storage**: `config/eliteinventorybackups/data/inventorybackups.mv.db`

### MySQL/MariaDB
- **Pros**: Multi-server support, better performance, professional backup tools
- **Cons**: Requires separate database server setup
- **Best for**: Network servers, larger communities, production environments

## Mod Integration

### Supported Mods
- **Curios**: Automatically backs up and restores rings, amulets, belts, and other curio items
- **Generic NBT**: Fallback system for any mod that stores data in player NBT

### Adding Custom Integrations
The mod includes a generic NBT backup system that captures most modded data automatically. For specific mod support, integrations can be added to the `integration` package.

## Permissions

Requires admin permissions for all commands. Supports:

- **LuckPerms**: `eliteinventorybackups.admin`
- **FTB Ranks**: `eliteinventorybackups.admin`
- **Vanilla**: OP level 2 (fallback)

```bash
# LuckPerms example
/lp user <player> permission set eliteinventorybackups.admin true

# FTB Ranks example
/ftbranks permission add <rank> eliteinventorybackups.admin
```

Console and command blocks always have full access.

## Installation

1. Download the appropriate version for your Minecraft/Forge version
2. Place the JAR file in your server's `mods` folder
3. (Optional) Install LuckPerms or FTB Ranks for better permission management
4. Start the server - configuration file will be created automatically
5. Configure database settings if using MySQL
6. Use `/eib backup <player>` to test the system

## Performance

- **Optimized Database Access**: Connection pooling and prepared statements
- **Shutdown Protection**: Prevents hanging during server shutdown
- **Backup Limits**: Automatic cleanup of old backups to prevent database bloat
- **Efficient Serialization**: Optimized NBT/JSON serialization for large inventories
- **Background Processing**: Non-blocking backup operations

## Version Support

- **Forge 1.19.2**: Fully supported
- **Forge 1.20.x**: Planned support
- **NeoForge 1.21.1**: Planned support

## Troubleshooting

### Common Issues
- **Database Connection Errors**: Check MySQL credentials and server availability
- **Permission Denied**: Ensure proper permission setup or OP status
- **Backup Not Creating**: Check config settings and server logs
- **Restoration Fails**: Verify backup exists and player is online

### MySQL Timezone Error
If you see timezone-related errors:
```toml
mysqlExtraParams = "serverTimezone=UTC"
```
Replace with your server's timezone (e.g., "America/New_York", "Europe/London").

## License

This project is licensed under the [MIT License](LICENSE).

