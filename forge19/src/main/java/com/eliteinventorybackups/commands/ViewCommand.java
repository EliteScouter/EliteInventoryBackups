package com.eliteinventorybackups.commands;

import com.eliteinventorybackups.EliteInventoryBackups;
import com.eliteinventorybackups.database.DatabaseManager;
import com.eliteinventorybackups.model.BackupEntry;
import com.eliteinventorybackups.model.BackupSummary;
import com.eliteinventorybackups.network.OpenBackupViewerGUIPacket;
import com.eliteinventorybackups.network.PacketHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.eliteinventorybackups.util.PermissionUtil;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ViewCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static final SuggestionProvider<CommandSourceStack> BACKUP_ID_SUGGESTIONS = (context, builder) -> {
        try {
            // Get the player argument from the context
            ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
            DatabaseManager dbManager = EliteInventoryBackups.getDatabaseManager();
            
            if (dbManager != null) {
                List<BackupSummary> summaries = dbManager.getBackupsSummaryForPlayer(targetPlayer.getUUID());
                for (BackupSummary summary : summaries) {
                    builder.suggest(summary.id());
                }
            }
        } catch (Exception e) {
            // If we can't get the player or database, just don't suggest anything
        }
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("view")
            .requires(PermissionUtil::hasAdminPermission)
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("backupId", IntegerArgumentType.integer(1))
                    .suggests(BACKUP_ID_SUGGESTIONS)
                    .executes(context -> {
                        CommandSourceStack source = context.getSource();
                        ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
                        int backupId = IntegerArgumentType.getInteger(context, "backupId");

                        ServerPlayer adminPlayer;
                        try {
                            adminPlayer = source.getPlayerOrException();
                        } catch (CommandSyntaxException e) {
                            source.sendFailure(Component.literal("This command must be run by a player."));
                            return 0;
                        }

                        DatabaseManager dbManager = EliteInventoryBackups.getDatabaseManager();
                        if (dbManager == null) {
                            source.sendFailure(Component.literal("DatabaseManager not initialized."));
                            return 0;
                        }

                        BackupEntry backupEntry = dbManager.getBackupById(backupId);

                        if (backupEntry == null) {
                            source.sendFailure(Component.literal("Backup with ID " + backupId + " not found."));
                            return 0;
                        }
                        
                        // Ensure the backup belongs to the specified target player (optional, but good for consistency)
                        if (!backupEntry.playerUuid().equals(targetPlayer.getUUID())) {
                            source.sendFailure(Component.literal("Backup ID " + backupId + " does not belong to player " + targetPlayer.getName().getString() + "."));
                            return 0;
                        }

                        // Send packet to the admin player to open the GUI
                        OpenBackupViewerGUIPacket packet = new OpenBackupViewerGUIPacket(
                            backupEntry.id(), // Assuming BackupEntry has an ID, which it should from the DB table
                            backupEntry.playerName(),
                            backupEntry.timestamp(),
                            backupEntry.eventType(),
                            backupEntry.inventoryMain(),
                            backupEntry.inventoryArmor(),
                            backupEntry.inventoryOffhand(),
                            backupEntry.inventoryEnderChest(),
                            backupEntry.inventoryCurios(),
                            backupEntry.playerNbt()
                        );
                        PacketHandler.sendToPlayer(packet, adminPlayer);
                        
                        source.sendSuccess(Component.literal("Opening backup viewer for " + targetPlayer.getName().getString() + " (ID: " + backupId + ")."), false);
                        return 1;
                    })
                )
            );
    }
} 