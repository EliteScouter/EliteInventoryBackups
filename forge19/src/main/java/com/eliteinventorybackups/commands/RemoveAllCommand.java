package com.eliteinventorybackups.commands;

import com.eliteinventorybackups.EliteInventoryBackups;
import com.eliteinventorybackups.database.DatabaseManager;
import com.eliteinventorybackups.util.PermissionUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public class RemoveAllCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static LiteralArgumentBuilder<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("removeall")
            .requires(PermissionUtil::hasAdminPermission)
            .then(Commands.argument("player", EntityArgument.player())
                .executes(context -> removeAllBackups(context.getSource(), EntityArgument.getPlayer(context, "player")))
            );
    }

    private static int removeAllBackups(CommandSourceStack source, ServerPlayer targetPlayer) {
        DatabaseManager dbManager = EliteInventoryBackups.getDatabaseManager();

        if (dbManager == null) {
            source.sendFailure(Component.literal("DatabaseManager not initialized."));
            return 0;
        }

        String playerName = targetPlayer.getName().getString();
        int deletedCount = dbManager.removeAllBackupsForPlayer(targetPlayer.getUUID());

        if (deletedCount == 0) {
            source.sendSuccess(Component.literal("No backups found for player ")
                .append(Component.literal(playerName).withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.YELLOW)))
                .append(Component.literal(".")), false);
        } else {
            source.sendSuccess(Component.literal("Successfully removed ")
                .append(Component.literal(String.valueOf(deletedCount)).withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.RED)))
                .append(Component.literal(" backup(s) for player "))
                .append(Component.literal(playerName).withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.YELLOW)))
                .append(Component.literal(".")), false);
            
            LOGGER.info("Admin {} removed {} backup(s) for player {} ({})", 
                source.getTextName(), deletedCount, playerName, targetPlayer.getUUID());
        }

        return 1;
    }
} 