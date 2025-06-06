# EliteHolograms - Permission System

EliteHolograms supports modern permission systems like **LuckPerms** and **FTB Ranks**, with automatic fallback to vanilla OP system.

## How It Works

1. **Permission System Detected**: Uses permission nodes (recommended)
2. **No Permission System**: Falls back to OP level 2 (vanilla behavior)

## Permission Nodes

| Permission Node | Description | Commands |
|----------------|-------------|----------|
| `eliteholograms.create` | Create new holograms | `/eh create` |
| `eliteholograms.delete` | Delete holograms | `/eh delete` |
| `eliteholograms.edit` | Edit hologram content | `/eh addline`, `/eh setline`, `/eh removeline`, `/eh insertline`, `/eh movehere` |
| `eliteholograms.list` | List holograms | `/eh list` |
| `eliteholograms.info` | View hologram info | `/eh info` |
| `eliteholograms.near` | View nearby holograms | `/eh near` |
| `eliteholograms.teleport` | Teleport to holograms | `/eh teleport` |
| `eliteholograms.admin` | Administrative commands | `/eh reload` |

## Permission Groups (Recommended)

### Basic User
```
eliteholograms.list
eliteholograms.info
eliteholograms.near
```

### Hologram Builder
```
eliteholograms.create
eliteholograms.edit
eliteholograms.list
eliteholograms.info
eliteholograms.near
eliteholograms.teleport
```

### Hologram Admin
```
eliteholograms.*
```

## Setup Examples

### LuckPerms
```bash
# Create a hologram builder group
/lp creategroup hologram-builder

# Give permissions to the group
/lp group hologram-builder permission set eliteholograms.create true
/lp group hologram-builder permission set eliteholograms.edit true
/lp group hologram-builder permission set eliteholograms.list true
/lp group hologram-builder permission set eliteholograms.info true
/lp group hologram-builder permission set eliteholograms.near true
/lp group hologram-builder permission set eliteholograms.teleport true

# Add a player to the group
/lp user <player> parent add hologram-builder

# Or give permission directly to a player
/lp user <player> permission set eliteholograms.create true
```

### FTB Ranks
```bash
# Set up permissions in the FTB Ranks config or through commands
# Example: Give Builder rank hologram permissions
/ftbranks permission add Builder eliteholograms.create
/ftbranks permission add Builder eliteholograms.edit
/ftbranks permission add Builder eliteholograms.list
```

## Wildcard Permissions

### Full Access
```
eliteholograms.*
```

### Read-Only Access  
```
eliteholograms.list
eliteholograms.info
eliteholograms.near
```

## Fallback Behavior

If **no permission system** is detected:
- **OP Level 2** required for all commands
- Same as vanilla Minecraft operator permissions
- Use `/op <player>` to grant access

## Console Commands

Console/Command blocks always have **full access** regardless of permission settings.

## Troubleshooting

### Commands Not Working
1. Check if permission system is detected in server logs
2. Verify player has correct permission nodes
3. Test with OP to confirm mod is working
4. Check permission system documentation

### Permission System Not Detected
- Ensure LuckPerms/FTB Ranks is properly installed
- Check server logs for detection messages
- Mod will fall back to OP system automatically

## Supported Permission Systems

- ✅ **LuckPerms** - Full support
- ✅ **FTB Ranks** - Full support  
- ✅ **Vanilla OP** - Fallback support
- 🔄 **Other systems** - Can be added upon request

## Migration

### From OP-Only to Permission System
1. Install LuckPerms or FTB Ranks
2. Restart server (mod will auto-detect)
3. Set up permission nodes as shown above
4. Remove OP from players who should only have limited access

### Security Note
Permission nodes provide **granular control** - much safer than giving full OP access! 