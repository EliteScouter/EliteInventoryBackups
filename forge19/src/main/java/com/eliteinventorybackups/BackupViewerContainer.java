package com.eliteinventorybackups;

import com.eliteinventorybackups.commands.ViewCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class BackupViewerContainer extends ChestMenu {
    private final ViewCommand.ViewerData viewerData;
    
    public BackupViewerContainer(int containerId, Inventory playerInventory, Container container, ViewCommand.ViewerData viewerData) {
        super(MenuType.GENERIC_9x6, containerId, playerInventory, container, 6);
        this.viewerData = viewerData;
    }
    
    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            super.clicked(slotId, dragType, clickType, player);
            return;
        }
        
        // Check if clicking on navigation buttons (bottom row, slots 45-53)
        if (slotId >= 45 && slotId <= 53) {
            // Handle navigation clicks
            String newSection = null;
            switch (slotId) {
                case 45: // Main inventory
                    newSection = "main";
                    break;
                case 46: // Armor
                    newSection = "armor";
                    break;
                case 47: // Offhand
                    newSection = "offhand";
                    break;
                case 48: // Ender chest
                    newSection = "enderchest";
                    break;
                case 49: // Curios
                    newSection = "curios";
                    break;
                case 53: // Close button
                    serverPlayer.closeContainer();
                    ViewCommand.cleanupViewer(serverPlayer.getUUID());
                    return;
                case 52: // Info button - do nothing
                    return;
                default:
                    // Other navigation slots - do nothing
                    return;
            }
            
            if (newSection != null) {
                // Close current inventory and open new section
                serverPlayer.closeContainer();
                ViewCommand.openInventorySection(serverPlayer, newSection, viewerData);
            }
            return; // Don't call super.clicked for navigation items
        }
        
        // For regular inventory slots (0-44), allow normal interaction
        super.clicked(slotId, dragType, clickType, player);
    }
    
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Prevent shift-clicking navigation items
        if (index >= 45 && index <= 53) {
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, index);
    }
    
    // Prevent taking navigation items
    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        if (slot.index >= 45 && slot.index <= 53) {
            return false; // Can't take navigation items
        }
        return super.canTakeItemForPickAll(stack, slot);
    }
} 