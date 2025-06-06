package com.eliteinventorybackups.util;

import com.eliteinventorybackups.config.ModConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public class NbtBackupUtil {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Backup player's NBT data as a fallback for unsupported mods
     */
    public static String backupPlayerNbt(ServerPlayer player) {
        if (!ModConfig.SERVER.enableGenericNbtBackup.get()) {
            return null;
        }

        try {
            CompoundTag playerTag = new CompoundTag();
            player.save(playerTag);
            
            // Remove position data (we store that separately)
            playerTag.remove("Pos");
            playerTag.remove("Dimension");
            
            // Remove basic inventory data (we store that separately too)
            playerTag.remove("Inventory");
            playerTag.remove("EnderItems");
            
            // Keep everything else (modded data, capabilities, etc.)
            return playerTag.toString();
        } catch (Exception e) {
            LOGGER.error("Failed to backup player NBT for {}: {}", player.getName().getString(), e.getMessage());
            return null;
        }
    }

    /**
     * Restore player NBT data
     */
    public static boolean restorePlayerNbt(ServerPlayer player, String nbtData) {
        if (nbtData == null || nbtData.isEmpty()) {
            return false;
        }

        try {
            CompoundTag playerTag = NbtUtils.snbtToStructure(nbtData);
            
            // Only restore non-inventory related data to avoid conflicts
            CompoundTag currentTag = new CompoundTag();
            player.save(currentTag);
            
            // Preserve current position and basic inventories
            CompoundTag pos = currentTag.getCompound("Pos");
            CompoundTag inventory = currentTag.getCompound("Inventory");
            CompoundTag enderItems = currentTag.getCompound("EnderItems");
            String dimension = currentTag.getString("Dimension");
            
            // Load the backup data
            player.load(playerTag);
            
            // Restore preserved data
            if (!pos.isEmpty()) {
                currentTag.put("Pos", pos);
            }
            if (!inventory.isEmpty()) {
                currentTag.put("Inventory", inventory);
            }
            if (!enderItems.isEmpty()) {
                currentTag.put("EnderItems", enderItems);
            }
            if (!dimension.isEmpty()) {
                currentTag.putString("Dimension", dimension);
            }
            
            LOGGER.debug("Restored NBT data for player {}", player.getName().getString());
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to restore player NBT for {}: {}", player.getName().getString(), e.getMessage());
            return false;
        }
    }
} 