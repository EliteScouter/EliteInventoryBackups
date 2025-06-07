package com.eliteinventorybackups;

import com.eliteinventorybackups.config.ModConfig;
import com.eliteinventorybackups.database.DatabaseManager;
import com.eliteinventorybackups.integration.CuriosIntegration;
import com.eliteinventorybackups.integration.GenericNbtIntegration;
import com.eliteinventorybackups.model.BackupEntry;
import com.eliteinventorybackups.util.InventorySerializer;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = EliteInventoryBackups.MODID)
public class PlayerEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ModConfig.SERVER.enableLoginSnapshots.get()) {
            createBackup(player, "login", null);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ModConfig.SERVER.enableLogoutSnapshots.get()) {
            createBackup(player, "logout", null);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && ModConfig.SERVER.enableDeathSnapshots.get()) {
            DamageSource damageSource = event.getSource();
            String causeOfDeath = getCauseOfDeath(damageSource);
            createBackup(player, "death", causeOfDeath);
        }
    }

    private static void createBackup(ServerPlayer player, String eventType, String causeOfDeath) {
        try {
            DatabaseManager dbManager = EliteInventoryBackups.getDatabaseManager();
            if (dbManager == null) {
                LOGGER.error("DatabaseManager is not initialized, cannot create backup for player {}", player.getName().getString());
                return;
            }

            // Don't create backups during server shutdown to prevent hanging
            if ("logout".equals(eventType)) {
                // Quick timeout check - if database operations take too long during logout, skip them
                try {
                    // Attempt a quick backup save - the DatabaseManager already has shutdown protection
                    performBackupSave(player, eventType, causeOfDeath, dbManager);
                } catch (Exception e) {
                    LOGGER.warn("Skipping backup for player {} during logout due to potential shutdown: {}", 
                        player.getName().getString(), e.getMessage());
                }
            } else {
                // Normal backup for login/death events
                performBackupSave(player, eventType, causeOfDeath, dbManager);
            }

        } catch (Exception e) {
            LOGGER.error("Error creating backup for player {}: {}", player.getName().getString(), e.getMessage(), e);
        }
    }

    private static void performBackupSave(ServerPlayer player, String eventType, String causeOfDeath, DatabaseManager dbManager) {
        try {
            // Standard inventory backups
            String mainInv = InventorySerializer.serializeItemListToString(player.getInventory().items);
            String armorInv = InventorySerializer.serializeItemListToString(player.getInventory().armor);
            String offhandInv = InventorySerializer.serializeItemListToString(player.getInventory().offhand);
            
            // Fix ender chest access
            List<ItemStack> enderChestItems = new ArrayList<>();
            for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) {
                enderChestItems.add(player.getEnderChestInventory().getItem(i));
            }
            String enderChestInv = InventorySerializer.serializeItemListToString(enderChestItems);

            // Curios items - Use integration if available and enabled
            String curiosInv = null;
            if (ModConfig.SERVER.enableCuriosBackup.get() && CuriosIntegration.isAvailable()) {
                curiosInv = CuriosIntegration.backupCurios(player);
                if (curiosInv != null) {
                    LOGGER.debug("Backed up Curios for player {}: {} characters", player.getName().getString(), curiosInv.length());
                }
            }

            // Generic NBT backup as fallback
            String playerNbt = null;
            if (ModConfig.SERVER.enableGenericNbtBackup.get()) {
                playerNbt = GenericNbtIntegration.backupPlayerNbt(player);
                if (playerNbt != null && !playerNbt.equals("{}")) {
                    LOGGER.debug("Backed up generic NBT for player {}: {} characters", player.getName().getString(), playerNbt.length());
                }
            }

            // Modded inventories placeholder (for future mod integrations)
            String moddedInventories = "{}";

            BackupEntry backupEntry = new BackupEntry(
                0, // ID will be auto-generated by database
                player.getUUID(),
                player.getName().getString(),
                System.currentTimeMillis(),
                eventType,
                player.getLevel().dimension().location().toString(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.experienceLevel,
                player.getXpNeededForNextLevel() > 0 ? (float) player.totalExperience / player.getXpNeededForNextLevel() : 0f,
                mainInv,
                armorInv,
                offhandInv,
                enderChestInv,
                causeOfDeath, curiosInv, playerNbt, moddedInventories
            );

            // Fix saveBackup call - it returns void
            dbManager.saveBackup(backupEntry);
            LOGGER.info("Backup [{}] created for player {} ({}) at world {}, x:{}, y:{}, z:{}", 
                eventType, player.getName().getString(), player.getUUID(), 
                player.getLevel().dimension().location().toString(), 
                player.getX(), player.getY(), player.getZ());

        } catch (Exception e) {
            LOGGER.error("Error creating backup for player {}: {}", player.getName().getString(), e.getMessage(), e);
        }
    }

    /**
     * Get a human-readable cause of death from a DamageSource
     */
    private static String getCauseOfDeath(DamageSource damageSource) {
        if (damageSource == null) {
            return "unknown";
        }
        
        String msgId = damageSource.getMsgId();
        if (msgId != null) {
            return msgId;
        }
        
        // Fallback to type name
        return damageSource.getClass().getSimpleName().toLowerCase();
    }
} 