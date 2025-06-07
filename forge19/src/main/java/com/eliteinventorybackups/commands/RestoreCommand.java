package com.eliteinventorybackups.commands;

import com.eliteinventorybackups.EliteInventoryBackups;
import com.eliteinventorybackups.database.DatabaseManager;
import com.eliteinventorybackups.model.BackupEntry;
import com.eliteinventorybackups.model.BackupSummary;
import com.eliteinventorybackups.util.InventorySerializer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.eliteinventorybackups.util.PermissionUtil;
import com.eliteinventorybackups.config.ModConfig;
import com.eliteinventorybackups.integration.CuriosIntegration;
import com.eliteinventorybackups.integration.GenericNbtIntegration;

import java.util.List;

public class RestoreCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static final SuggestionProvider<CommandSourceStack> BACKUP_NUMBER_SUGGESTIONS = (context, builder) -> {
        try {
            // Get the player argument from the context
            ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
            DatabaseManager dbManager = EliteInventoryBackups.getDatabaseManager();
            
            if (dbManager != null) {
                List<BackupSummary> summaries = dbManager.getBackupsSummaryForPlayer(targetPlayer.getUUID());
                for (BackupSummary summary : summaries) {
                    builder.suggest(summary.id()); // This now contains backup_number, not database id
                }
            }
        } catch (Exception e) {
            // If we can't get the player or database, just don't suggest anything
        }
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("restore")
            .requires(PermissionUtil::hasAdminPermission)
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("backupNumber", IntegerArgumentType.integer(1))
                    .suggests(BACKUP_NUMBER_SUGGESTIONS)
                    .executes(context -> {
                        CommandSourceStack source = context.getSource();
                        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
                        int backupNumber = IntegerArgumentType.getInteger(context, "backupNumber");

                        DatabaseManager dbManager = EliteInventoryBackups.getDatabaseManager();
                        if (dbManager == null) {
                            source.sendFailure(Component.literal("DatabaseManager not initialized."));
                            return 0;
                        }

                        BackupEntry backupEntry = dbManager.getBackupByNumber(targetPlayer.getUUID(), backupNumber);
                        if (backupEntry == null) {
                            source.sendFailure(Component.literal("Backup #" + backupNumber + " not found for player " + targetPlayer.getName().getString() + "."));
                            return 0;
                        }

                        try {
                            // Clear current player state
                            targetPlayer.getInventory().clearContent();
                            targetPlayer.getEnderChestInventory().clearContent();
                            targetPlayer.setExperienceLevels(0);
                            targetPlayer.setExperiencePoints(0); // Also clears progress

                            // Restore Inventories
                            List<ItemStack> mainInv = InventorySerializer.deserializeStringToList(backupEntry.inventoryMain());
                            List<ItemStack> armorInv = InventorySerializer.deserializeStringToList(backupEntry.inventoryArmor());
                            List<ItemStack> offhandInv = InventorySerializer.deserializeStringToList(backupEntry.inventoryOffhand());
                            List<ItemStack> enderChestInv = InventorySerializer.deserializeStringToList(backupEntry.inventoryEnderChest());

                            // Main inventory (slots 0-35)
                            for (int i = 0; i < mainInv.size() && i < targetPlayer.getInventory().items.size(); i++) {
                                targetPlayer.getInventory().setItem(i, mainInv.get(i));
                            }
                            // Armor inventory (slots 36-39 for player inv, but armor list is 0-3)
                            for (int i = 0; i < armorInv.size() && i < targetPlayer.getInventory().armor.size(); i++) {
                                targetPlayer.getInventory().armor.set(i, armorInv.get(i));
                            }
                            // Offhand inventory (slot 40 for player inv, but offhand list is 0)
                            if (!offhandInv.isEmpty() && targetPlayer.getInventory().offhand.size() > 0) {
                                targetPlayer.getInventory().offhand.set(0, offhandInv.get(0));
                            }
                            // Ender Chest
                            for (int i = 0; i < enderChestInv.size() && i < targetPlayer.getEnderChestInventory().getContainerSize(); i++) {
                                targetPlayer.getEnderChestInventory().setItem(i, enderChestInv.get(i));
                            }

                            // Restore Experience
                            targetPlayer.setExperienceLevels(backupEntry.experienceLevel());
                            targetPlayer.setExperiencePoints((int)(backupEntry.experienceProgress() * targetPlayer.getXpNeededForNextLevel()));
                            
                            // Restore Curios if available and enabled
                            if (ModConfig.SERVER.enableCuriosBackup.get() && CuriosIntegration.isAvailable() && backupEntry.inventoryCurios() != null) {
                                LOGGER.info("Attempting to restore Curios for player {}. Curios data: {}", 
                                    targetPlayer.getName().getString(), 
                                    backupEntry.inventoryCurios().length() > 100 ? 
                                        backupEntry.inventoryCurios().substring(0, 100) + "..." : 
                                        backupEntry.inventoryCurios());
                                
                                boolean curiosRestored = CuriosIntegration.restoreCurios(targetPlayer, backupEntry.inventoryCurios());
                                if (curiosRestored) {
                                    LOGGER.info("Successfully restored Curios for player {}", targetPlayer.getName().getString());
                                } else {
                                    LOGGER.warn("Failed to restore Curios for player {}", targetPlayer.getName().getString());
                                }
                            } else {
                                LOGGER.info("Curios restoration skipped for player {}. Enabled: {}, Available: {}, Data: {}", 
                                    targetPlayer.getName().getString(),
                                    ModConfig.SERVER.enableCuriosBackup.get(),
                                    CuriosIntegration.isAvailable(),
                                    backupEntry.inventoryCurios() != null ? "present" : "null");
                            }
                            
                            // Restore Generic NBT if available and enabled
                            if (ModConfig.SERVER.enableGenericNbtBackup.get() && backupEntry.playerNbt() != null) {
                                boolean nbtRestored = GenericNbtIntegration.restorePlayerNbt(targetPlayer, backupEntry.playerNbt());
                                if (nbtRestored) {
                                    LOGGER.debug("Successfully restored generic NBT for player {}", targetPlayer.getName().getString());
                                } else {
                                    LOGGER.warn("Failed to restore generic NBT for player {}", targetPlayer.getName().getString());
                                }
                            }
                            
                            // Refresh/update client
                            targetPlayer.inventoryMenu.broadcastChanges(); // For main inventory + armor + offhand
                            // Ender chest updates are usually handled, but can be forced if needed.
                            targetPlayer.containerMenu.broadcastChanges(); // General update for player container

                            source.sendSuccess(Component.literal("Successfully restored backup #" + backupNumber + " for player " + targetPlayer.getName().getString()), true);
                            LOGGER.info("Player {} restored {} from backup #{}", source.getTextName(), targetPlayer.getName().getString(), backupNumber);
                            return 1;

                        } catch (Exception e) {
                            LOGGER.error("Error restoring backup #{} for player {}: {}", backupNumber, targetPlayer.getName().getString(), e.getMessage(), e);
                            source.sendFailure(Component.literal("An error occurred during restore: " + e.getMessage()));
                            return 0;
                        }
                    })
                )
            );
    }
} 