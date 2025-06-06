package com.eliteinventorybackups.commands;

import com.eliteinventorybackups.PlayerEventHandler; // We'll need a way to trigger backup logic, similar to event handlers
import com.eliteinventorybackups.EliteInventoryBackups;
import com.eliteinventorybackups.config.ModConfig;
import com.eliteinventorybackups.database.DatabaseManager;
import com.eliteinventorybackups.integration.CuriosIntegration;
import com.eliteinventorybackups.model.BackupEntry;
import com.eliteinventorybackups.util.InventorySerializer;
import com.eliteinventorybackups.util.NbtBackupUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.eliteinventorybackups.util.PermissionUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;

public class BackupCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static LiteralArgumentBuilder<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("backup")
            .requires(PermissionUtil::hasAdminPermission) // Updated permission check
            .then(Commands.argument("player", EntityArgument.player())
                .executes(context -> {
                    ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
                    CommandSourceStack source = context.getSource();

                    if (targetPlayer != null) {
                        try {
                            // Re-use or adapt the backup logic from PlayerEventHandler
                            // For directness, we can duplicate parts of performBackup here or refactor performBackup to be more generally callable
                            DatabaseManager dbManager = EliteInventoryBackups.getDatabaseManager();
                            if (dbManager == null) {
                                source.sendFailure(Component.literal("DatabaseManager not initialized."));
                                return 0;
                            }

                            UUID playerUuid = targetPlayer.getUUID();
                            String playerName = targetPlayer.getName().getString();
                            long timestamp = System.currentTimeMillis();
                            String world = targetPlayer.level.dimension().location().toString();
                            double posX = targetPlayer.getX();
                            double posY = targetPlayer.getY();
                            double posZ = targetPlayer.getZ();
                            int experienceLevel = targetPlayer.experienceLevel;
                            float experienceProgress = targetPlayer.experienceProgress;

                            String mainInv = InventorySerializer.serializeItemListToString(targetPlayer.getInventory().items);
                            String armorInv = InventorySerializer.serializeItemListToString(targetPlayer.getInventory().armor);
                            String offhandInv = InventorySerializer.serializeItemListToString(targetPlayer.getInventory().offhand);
                            
                            List<ItemStack> enderChestItems = new ArrayList<>();
                            for (int i = 0; i < targetPlayer.getEnderChestInventory().getContainerSize(); i++) {
                                enderChestItems.add(targetPlayer.getEnderChestInventory().getItem(i));
                            }
                            String enderChestInv = InventorySerializer.serializeItemListToString(enderChestItems);

                            // Curios items - Use integration if available and enabled
                            String curiosInv = null;
                            if (ModConfig.SERVER.enableCuriosBackup.get() && CuriosIntegration.isAvailable()) {
                                curiosInv = CuriosIntegration.backupCurios(targetPlayer);
                                if (curiosInv != null) {
                                    LOGGER.debug("Backed up Curios for player {}: {} characters", targetPlayer.getName().getString(), curiosInv.length());
                                }
                            }

                            // Generic NBT backup as fallback
                            String playerNbt = null;
                            if (ModConfig.SERVER.enableGenericNbtBackup.get()) {
                                playerNbt = NbtBackupUtil.backupPlayerNbt(targetPlayer);
                            }

                            // Modded inventories placeholder (for future mod integrations)
                            String moddedInventories = "{}";

                            BackupEntry entry = new BackupEntry(
                                0, // ID is auto-generated by DB
                                playerUuid, playerName, timestamp, "manual", world,
                                posX, posY, posZ, experienceLevel, experienceProgress,
                                mainInv, armorInv, offhandInv, enderChestInv,
                                null, curiosInv, playerNbt, moddedInventories // No cause of death for manual backup
                            );

                            dbManager.saveBackup(entry);
                            source.sendSuccess(Component.literal("Successfully created backup for " + playerName), true);
                            LOGGER.info("Manual backup created for player {} by command.", playerName);
                            return 1;
                        } catch (Exception e) {
                            LOGGER.error("Failed to create manual backup for player {}: {}", targetPlayer.getName().getString(), e.getMessage(), e);
                            source.sendFailure(Component.literal("Failed to create backup: " + e.getMessage()));
                            return 0;
                        }
                    } else {
                        source.sendFailure(Component.literal("Player not found."));
                        return 0;
                    }
                })
            );
    }
} 