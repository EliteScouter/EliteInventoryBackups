package com.eliteinventorybackups.commands;

import com.eliteinventorybackups.EliteInventoryBackups;
import com.eliteinventorybackups.database.DatabaseManager;
import com.eliteinventorybackups.model.BackupSummary;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.eliteinventorybackups.util.PermissionUtil;
import net.minecraft.network.chat.MutableComponent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class ListCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss");
    private static final int ENTRIES_PER_PAGE = 10;

    public static LiteralArgumentBuilder<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("list")
            .requires(PermissionUtil::hasAdminPermission)
            .then(Commands.argument("player", EntityArgument.player())
                .executes(context -> listBackups(context.getSource(), EntityArgument.getPlayer(context, "player"), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(context -> listBackups(context.getSource(), EntityArgument.getPlayer(context, "player"), IntegerArgumentType.getInteger(context, "page")))
                )
            );
    }

    private static int listBackups(CommandSourceStack source, ServerPlayer targetPlayer, int page) {
        DatabaseManager dbManager = EliteInventoryBackups.getDatabaseManager();

        if (dbManager == null) {
            source.sendFailure(Component.literal("DatabaseManager not initialized."));
            return 0;
        }

        UUID playerUuid = targetPlayer.getUUID();
        List<BackupSummary> summaries = dbManager.getBackupsSummaryForPlayer(playerUuid);

        if (summaries.isEmpty()) {
            source.sendSuccess(Component.literal("No backups found for " + targetPlayer.getName().getString() + "."), false);
            return 1;
        }

        int totalPages = (int) Math.ceil((double) summaries.size() / ENTRIES_PER_PAGE);
        if (page > totalPages) {
            source.sendFailure(Component.literal("Page " + page + " does not exist. Maximum page: " + totalPages));
            return 0;
        }

        int startIndex = (page - 1) * ENTRIES_PER_PAGE;
        int endIndex = Math.min(startIndex + ENTRIES_PER_PAGE, summaries.size());

        // Simple header
        source.sendSuccess(Component.literal("Backups for ").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.GRAY))
            .append(Component.literal(targetPlayer.getName().getString()).withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.WHITE)))
            .append(Component.literal(" (").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.GRAY)))
            .append(Component.literal(String.valueOf(summaries.size())).withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.GOLD)))
            .append(Component.literal(" total) - Page ").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.GRAY)))
            .append(Component.literal(String.valueOf(page)).withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.YELLOW)))
            .append(Component.literal("/").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.GRAY)))
            .append(Component.literal(String.valueOf(totalPages)).withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.YELLOW))), false);

        // Backup entries
        for (int i = startIndex; i < endIndex; i++) {
            BackupSummary summary = summaries.get(i);
            String formattedDate = DATE_FORMAT.format(new Date(summary.timestamp()));
            
            // Format event type with colors
            Component eventType = formatEventType(summary.eventType());
            
            Component message = Component.literal("#" + summary.id()).withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.YELLOW))
                .append(Component.literal(" • ").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.DARK_GRAY)))
                .append(eventType)
                .append(Component.literal(" • ").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.DARK_GRAY)))
                .append(Component.literal(formattedDate).withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.GRAY)))
                .append(Component.literal(" • ").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.DARK_GRAY)))
                .append(Component.literal(getWorldDisplayName(summary.world())).withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.GREEN)));
            
            // Add click and hover events
            Style clickableStyle = Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/eib view " + targetPlayer.getName().getString() + " " + summary.id()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    Component.literal("Click to view backup #" + summary.id()).withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.AQUA))));
            
            source.sendSuccess(message.copy().setStyle(clickableStyle), false);
        }

        // Navigation footer
        if (totalPages > 1) {
            MutableComponent footer = Component.literal("");
            
            if (page > 1) {
                footer = footer.append(Component.literal("← Previous").withStyle(Style.EMPTY
                    .withColor(net.minecraft.ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/eib list " + targetPlayer.getName().getString() + " " + (page - 1)))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Go to page " + (page - 1))))));
            } else {
                footer = footer.append(Component.literal("← Previous").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.DARK_GRAY)));
            }
            
            footer = footer.append(Component.literal("   ").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.GRAY)));
            
            if (page < totalPages) {
                footer = footer.append(Component.literal("Next →").withStyle(Style.EMPTY
                    .withColor(net.minecraft.ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/eib list " + targetPlayer.getName().getString() + " " + (page + 1)))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Go to page " + (page + 1))))));
            } else {
                footer = footer.append(Component.literal("Next →").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.DARK_GRAY)));
            }
            
            footer = footer.append(Component.literal("   (").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.DARK_GRAY)))
                .append(Component.literal(String.valueOf(startIndex + 1)).withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.WHITE)))
                .append(Component.literal("-").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.DARK_GRAY)))
                .append(Component.literal(String.valueOf(endIndex)).withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.WHITE)))
                .append(Component.literal(" of ").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.DARK_GRAY)))
                .append(Component.literal(String.valueOf(summaries.size())).withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.WHITE)))
                .append(Component.literal(")").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.DARK_GRAY)));
            
            source.sendSuccess(footer, false);
        }

        return 1;
    }

    private static Component formatEventType(String eventType) {
        return switch (eventType.toLowerCase()) {
            case "death" -> Component.literal("💀 Death").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.RED));
            case "login" -> Component.literal("🟢 Login").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.GREEN));
            case "logout" -> Component.literal("🔴 Logout").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.GRAY));
            case "manual" -> Component.literal("⚙️ Manual").withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.BLUE));
            default -> Component.literal("❓ " + eventType).withStyle(Style.EMPTY.withColor(net.minecraft.ChatFormatting.WHITE));
        };
    }

    private static String getWorldDisplayName(String world) {
        if (world == null) return "Unknown";
        return switch (world) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "End";
            default -> world.replace("minecraft:", "").replace("_", " ");
        };
    }
} 