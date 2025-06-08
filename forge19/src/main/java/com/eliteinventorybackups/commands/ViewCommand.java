package com.eliteinventorybackups.commands;

import com.eliteinventorybackups.EliteInventoryBackups;
import com.eliteinventorybackups.database.DatabaseManager;
import com.eliteinventorybackups.model.BackupEntry;
import com.eliteinventorybackups.model.BackupSummary;
import com.eliteinventorybackups.util.InventorySerializer;
import com.eliteinventorybackups.util.PermissionUtil;
import com.eliteinventorybackups.integration.CuriosIntegration;
import com.eliteinventorybackups.BackupViewerContainer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;

import java.text.SimpleDateFormat;
import java.util.*;

public class ViewCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    // Store backup data for open inventories
    private static final Map<UUID, ViewerData> activeViewers = new HashMap<>();
    
    private static final SuggestionProvider<CommandSourceStack> BACKUP_NUMBER_SUGGESTIONS = (context, builder) -> {
        try {
            ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
            DatabaseManager dbManager = EliteInventoryBackups.getDatabaseManager();
            
            if (dbManager != null) {
                List<BackupSummary> summaries = dbManager.getBackupsSummaryForPlayer(targetPlayer.getUUID());
                for (BackupSummary summary : summaries) {
                    builder.suggest(summary.id());
                }
            }
        } catch (Exception e) {
            // If we can't get suggestions, just don't suggest anything
        }
        return builder.buildFuture();
    };
    
    private static final SuggestionProvider<CommandSourceStack> SECTION_SUGGESTIONS = (context, builder) -> {
        builder.suggest("main");
        builder.suggest("armor");
        builder.suggest("offhand");
        builder.suggest("enderchest");
        builder.suggest("curios");
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<CommandSourceStack> register(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("view")
            .requires(PermissionUtil::hasAdminPermission)
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("backupNumber", IntegerArgumentType.integer(1))
                    .suggests(BACKUP_NUMBER_SUGGESTIONS)
                    .executes(context -> {
                        // Default to main inventory
                        return openBackupView(context.getSource(), 
                            EntityArgument.getPlayer(context, "player"),
                            IntegerArgumentType.getInteger(context, "backupNumber"),
                            "main");
                    })
                    .then(Commands.argument("section", StringArgumentType.string())
                        .suggests(SECTION_SUGGESTIONS)
                        .executes(context -> {
                            return openBackupView(context.getSource(),
                                EntityArgument.getPlayer(context, "player"),
                                IntegerArgumentType.getInteger(context, "backupNumber"),
                                StringArgumentType.getString(context, "section"));
                        })
                    )
                )
            );
    }
    
    private static int openBackupView(CommandSourceStack source, ServerPlayer targetPlayer, int backupNumber, String section) {
        if (!(source.getEntity() instanceof ServerPlayer adminPlayer)) {
            source.sendFailure(Component.literal("This command can only be executed by a player."));
            return 0;
        }
        
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
        
        // Store backup data for this viewer
        ViewerData viewerData = new ViewerData(backupEntry, targetPlayer.getName().getString(), backupNumber);
        activeViewers.put(adminPlayer.getUUID(), viewerData);
        
        // Open the specified section
        return openInventorySection(adminPlayer, section, viewerData);
    }
    
    public static int openInventorySection(ServerPlayer adminPlayer, String section, ViewerData viewerData) {
        List<ItemStack> items;
        String displayName;
        
        switch (section.toLowerCase()) {
            case "main":
                items = InventorySerializer.deserializeStringToList(viewerData.backupEntry.inventoryMain());
                displayName = "Main Inventory (Unequipped Items)";
                break;
            case "armor":
                items = InventorySerializer.deserializeStringToList(viewerData.backupEntry.inventoryArmor());
                displayName = "Armor Slots (Equipped Armor)";
                break;
            case "offhand":
                items = InventorySerializer.deserializeStringToList(viewerData.backupEntry.inventoryOffhand());
                displayName = "Offhand Slot (Equipped in Offhand)";
                break;
            case "enderchest":
                items = InventorySerializer.deserializeStringToList(viewerData.backupEntry.inventoryEnderChest());
                displayName = "Ender Chest";
                break;
            case "curios":
                if (viewerData.backupEntry.inventoryCurios() != null && !viewerData.backupEntry.inventoryCurios().equals("{}")) {
                    items = getCuriosItems(viewerData.backupEntry.inventoryCurios());
                    displayName = "Curios Slots (Equipped Accessories)";
                } else {
                    adminPlayer.sendSystemMessage(Component.literal("No Curios data found in this backup."));
                    return 0;
                }
                break;
            default:
                adminPlayer.sendSystemMessage(Component.literal("Invalid section. Use: main, armor, offhand, enderchest, curios"));
                return 0;
        }
        
        // Create virtual chest inventory
        SimpleContainer container = new SimpleContainer(54); // Double chest size
        
        // Add navigation items in the bottom row
        addNavigationItems(container, section, viewerData);
        
        // Add the actual items (up to 45 slots, leaving bottom row for navigation)
        for (int i = 0; i < Math.min(items.size(), 45); i++) {
            ItemStack item = items.get(i);
            if (item != null && !item.isEmpty()) {
                container.setItem(i, item.copy());
            }
        }
        
        // Open the chest for the admin player
        MenuProvider menuProvider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal(String.format("%s #%d - %s", 
                    viewerData.playerName,
                    viewerData.backupNumber,
                    displayName));
            }
            
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
                return new BackupViewerContainer(containerId, playerInventory, container, viewerData);
            }
        };
        
        adminPlayer.openMenu(menuProvider);
        adminPlayer.sendSystemMessage(Component.literal(String.format("Opened %s for %s (Backup #%d). Items: %d", 
            displayName, viewerData.playerName, viewerData.backupNumber, items.size())));
        
        // Add section-specific explanatory messages
        switch (section.toLowerCase()) {
            case "main":
                adminPlayer.sendSystemMessage(Component.literal("§7This shows items that were in the player's main inventory (unequipped)."));
                break;
            case "armor":
                adminPlayer.sendSystemMessage(Component.literal("§7This shows items that were equipped in armor slots."));
                break;
            case "curios":
                adminPlayer.sendSystemMessage(Component.literal("§7This shows items that were equipped in Curios accessory slots."));
                break;
            case "offhand":
                adminPlayer.sendSystemMessage(Component.literal("§7This shows the item that was equipped in the offhand slot."));
                break;
            case "enderchest":
                adminPlayer.sendSystemMessage(Component.literal("§7This shows items that were stored in the ender chest."));
                break;
        }
        
        if (items.size() > 45) {
            adminPlayer.sendSystemMessage(Component.literal("§6Warning: This section has " + items.size() + 
                " items, but only showing first 45. Consider using commands for full access."));
        }
        
        return 1;
    }
    
    private static ItemStack createNavigationItem(net.minecraft.world.item.Item item, Component name) {
        ItemStack stack = new ItemStack(item);
        stack.setHoverName(name);
        
        // Hide all the annoying tooltip info (food, durability, etc.)
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt("HideFlags", 63); // Hide everything except custom name
        
        return stack;
    }
    
    private static ItemStack createCleanNavItem(Item item, Component name) {
        ItemStack stack = new ItemStack(item);
        stack.setHoverName(name);
        
        // Hide all default tooltip info (food stats, durability, etc.)
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt("HideFlags", 127); // Hide all flags
        
        return stack;
    }
    
    private static void addNavigationItems(SimpleContainer container, String currentSection, ViewerData viewerData) {
        // Navigation items in bottom row (slots 45-53)
        int navRow = 45;
        
        String baseCmd = "/eib view " + viewerData.playerName + " " + viewerData.backupNumber + " ";
        
        // Main inventory button
        ItemStack mainButton;
        if ("main".equals(currentSection)) {
            mainButton = createCleanNavItem(Items.CHEST, Component.literal("§e► Main Inventory ◄§r\n§7Currently viewing\n§7§oUnequipped items"));
        } else {
            mainButton = createCleanNavItem(Items.CHEST, Component.literal("§aMain Inventory§r\n§6Click to switch!\n§7§oUnequipped items"));
        }
        container.setItem(navRow, mainButton);
        
        // Armor button
        ItemStack armorButton;
        if ("armor".equals(currentSection)) {
            armorButton = createCleanNavItem(Items.IRON_CHESTPLATE, Component.literal("§e► Armor Slots ◄§r\n§7Currently viewing\n§7§oEquipped armor"));
        } else {
            armorButton = createCleanNavItem(Items.IRON_CHESTPLATE, Component.literal("§bArmor Slots§r\n§6Click to switch!\n§7§oEquipped armor"));
        }
        container.setItem(navRow + 1, armorButton);
        
        // Offhand button
        ItemStack offhandButton;
        if ("offhand".equals(currentSection)) {
            offhandButton = createCleanNavItem(Items.SHIELD, Component.literal("§e► Offhand Slot ◄§r\n§7Currently viewing\n§7§oEquipped in offhand"));
        } else {
            offhandButton = createCleanNavItem(Items.SHIELD, Component.literal("§dOffhand Slot§r\n§6Click to switch!\n§7§oEquipped in offhand"));
        }
        container.setItem(navRow + 2, offhandButton);
        
        // Ender chest button
        ItemStack enderButton;
        if ("enderchest".equals(currentSection)) {
            enderButton = createCleanNavItem(Items.ENDER_CHEST, Component.literal("§e► Ender Chest ◄§r\n§7Currently viewing\n§7§oEnder chest storage"));
        } else {
            enderButton = createCleanNavItem(Items.ENDER_CHEST, Component.literal("§5Ender Chest§r\n§6Click to switch!\n§7§oEnder chest storage"));
        }
        container.setItem(navRow + 3, enderButton);
        
        // Curios button (using a different item to avoid food stats)
        ItemStack curiosButton;
        if ("curios".equals(currentSection)) {
            curiosButton = createCleanNavItem(Items.GOLD_INGOT, Component.literal("§e► Curios Slots ◄§r\n§7Currently viewing\n§7§oEquipped accessories"));
        } else {
            curiosButton = createCleanNavItem(Items.GOLD_INGOT, Component.literal("§6Curios Slots§r\n§6Click to switch!\n§7§oEquipped accessories"));
        }
        container.setItem(navRow + 4, curiosButton);
        
        // Info item
        ItemStack infoButton = createCleanNavItem(Items.BOOK, 
            Component.literal("§eBackup Info§r\n§7• Click items above to take them\n§7• Click navigation buttons to switch\n§7• Current: §f" + currentSection));
        container.setItem(navRow + 7, infoButton);
        
        // Close instruction
        ItemStack closeButton = createCleanNavItem(Items.BARRIER, 
            Component.literal("§cClose Viewer§r\n§6Click to close!"));
        container.setItem(navRow + 8, closeButton);
    }
    
    private static List<ItemStack> getCuriosItems(String curiosData) {
        List<ItemStack> allItems = new ArrayList<>();
        
        try {
            if (curiosData.startsWith("{") && curiosData.endsWith("}")) {
                String content = curiosData.substring(1, curiosData.length() - 1);
                if (content.trim().isEmpty()) {
                    return allItems;
                }
                
                String[] pairs = content.split(",(?=\")");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("\":", 2);
                    if (keyValue.length == 2) {
                        String slotType = keyValue[0].substring(1); // Remove leading quote
                        String stacksData = keyValue[1];
                        
                        if (stacksData.startsWith("\"") && stacksData.endsWith("\"")) {
                            stacksData = stacksData.substring(1, stacksData.length() - 1);
                        }
                        stacksData = stacksData.replace("\\\"", "\"");
                        
                        List<ItemStack> slotItems = InventorySerializer.deserializeStringToList(stacksData);
                        
                        // Add slot type info to items
                        for (int i = 0; i < slotItems.size(); i++) {
                            ItemStack item = slotItems.get(i);
                            if (item != null && !item.isEmpty()) {
                                // Add lore indicating which Curios slot this came from
                                ItemStack itemWithLore = item.copy();
                                itemWithLore.setHoverName(Component.literal(item.getDisplayName().getString() + " §7(" + slotType + " #" + i + ")"));
                                allItems.add(itemWithLore);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse Curios items: {}", e.getMessage(), e);
        }
        
        return allItems;
    }
    
    public static void cleanupViewer(UUID playerUUID) {
        activeViewers.remove(playerUUID);
    }
    
    public static void cleanupAllViewers() {
        LOGGER.info("Cleaning up {} active backup viewers", activeViewers.size());
        activeViewers.clear();
    }
    
    public static ViewerData getViewerData(UUID playerUUID) {
        return activeViewers.get(playerUUID);
    }
    
    public static class ViewerData {
        public final BackupEntry backupEntry;
        public final String playerName;
        public final int backupNumber;
        
        public ViewerData(BackupEntry backupEntry, String playerName, int backupNumber) {
            this.backupEntry = backupEntry;
            this.playerName = playerName;
            this.backupNumber = backupNumber;
        }
    }
} 