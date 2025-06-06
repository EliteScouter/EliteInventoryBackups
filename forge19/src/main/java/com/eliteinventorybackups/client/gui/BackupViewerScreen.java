package com.eliteinventorybackups.client.gui;

import com.eliteinventorybackups.integration.GenericNbtIntegration;
import com.eliteinventorybackups.network.OpenBackupViewerGUIPacket;
import com.eliteinventorybackups.util.InventorySerializer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.eliteinventorybackups.network.PacketHandler;
import com.eliteinventorybackups.network.RequestTakeItemPacket;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class BackupViewerScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

    private final OpenBackupViewerGUIPacket packetData;
    private List<ItemStack> mainInventory = new ArrayList<>();
    private List<ItemStack> armorInventory = new ArrayList<>();
    private List<ItemStack> offhandInventory = new ArrayList<>();
    private List<ItemStack> enderChestInventory = new ArrayList<>();
    private Map<String, List<ItemStack>> curiosInventory = new HashMap<>();
    private Map<String, List<ItemStack>> genericModdedInventory = new HashMap<>();

    // Layout constants
    private static final int ITEM_RENDER_SIZE = 18;
    private static final int ITEMS_PER_ROW = 9;
    private static final int PADDING = 5;

    public BackupViewerScreen(OpenBackupViewerGUIPacket packetData) {
        super(Component.literal("Backup Viewer - " + packetData.getTargetPlayerName()));
        this.packetData = packetData;
        deserializeInventories();
    }

    private void deserializeInventories() {
        try {
            mainInventory = InventorySerializer.deserializeStringToList(packetData.getSerializedMainInventory());
            armorInventory = InventorySerializer.deserializeStringToList(packetData.getSerializedArmorInventory());
            offhandInventory = InventorySerializer.deserializeStringToList(packetData.getSerializedOffhandInventory());
            enderChestInventory = InventorySerializer.deserializeStringToList(packetData.getSerializedEnderChestInventory());
            
            // Deserialize Curios data
            curiosInventory = parseCuriosData(packetData.getSerializedCuriosInventory());
            
            // Deserialize generic modded items from NBT
            genericModdedInventory = GenericNbtIntegration.extractModdedItems(packetData.getSerializedPlayerNbt());
        } catch (Exception e) {
            LOGGER.error("Error deserializing inventories for GUI: {}", e.getMessage(), e);
            // Optionally close screen or show error message
        }
    }
    
    /**
     * Parse Curios data from the serialized string format
     */
    private Map<String, List<ItemStack>> parseCuriosData(String curiosData) {
        Map<String, List<ItemStack>> result = new HashMap<>();
        
        if (curiosData == null || curiosData.trim().equals("{}") || curiosData.trim().isEmpty()) {
            return result;
        }
        
        try {
            // Simple JSON-like parsing for our specific format
            String content = curiosData.trim();
            if (content.startsWith("{") && content.endsWith("}")) {
                content = content.substring(1, content.length() - 1); // Remove outer braces
                
                // Split by slot types (simplified parser)
                String[] parts = content.split("\",\"");
                for (String part : parts) {
                    String[] keyValue = part.split("\":", 2);
                    if (keyValue.length == 2) {
                        String slotType = keyValue[0].replace("\"", "");
                        String inventoryData = keyValue[1];
                        
                        List<ItemStack> stacks = InventorySerializer.deserializeStringToList(inventoryData);
                        result.put(slotType, stacks);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse Curios data: {}", e.getMessage());
        }
        
        return result;
    }

    @Override
    protected void init() {
        super.init();
        // Add buttons or other widgets if needed, e.g., "Close", "Restore All"
        this.addRenderableWidget(new Button(this.width / 2 - 100, this.height - 30, 200, 20, Component.literal("Close"), button -> this.onClose()));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTicks);

        // Title
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        String dateStr = DATE_FORMAT.format(new Date(packetData.getTimestamp()));
        drawString(poseStack, this.font, "Backup ID: " + packetData.getBackupId() + ", Event: " + packetData.getEventType() + ", Date: " + dateStr, 10, 30, 0xA0A0A0);

        int currentY = 50;
        currentY = renderInventorySection(poseStack, "Main Inventory", mainInventory, 10, currentY, mouseX, mouseY, "main");
        currentY = renderInventorySection(poseStack, "Armor", armorInventory, 10, currentY, mouseX, mouseY, "armor");
        currentY = renderInventorySection(poseStack, "Off-hand", offhandInventory, 10, currentY, mouseX, mouseY, "offhand");
        currentY = renderInventorySection(poseStack, "Ender Chest", enderChestInventory, 10, currentY, mouseX, mouseY, "enderchest");
        currentY = renderCuriosSection(poseStack, 10, currentY, mouseX, mouseY);
        currentY = renderGenericModdedSection(poseStack, 10, currentY, mouseX, mouseY);

        // Render tooltips for items
        renderTooltips(poseStack, mouseX, mouseY);
    }

    private int renderInventorySection(PoseStack poseStack, String title, List<ItemStack> items, int x, int y, int mouseX, int mouseY, String invType) {
        drawString(poseStack, this.font, title, x, y, 0xFFFFFF);
        y += (font.lineHeight + PADDING);
        
        if (items.isEmpty()) {
            drawString(poseStack, this.font, " (Empty)", x + font.width(title), y - (font.lineHeight + PADDING), 0x808080);
        }

        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            int itemX = x + (i % ITEMS_PER_ROW) * ITEM_RENDER_SIZE;
            int itemY = y + (i / ITEMS_PER_ROW) * ITEM_RENDER_SIZE;

            if (stack != null && !stack.isEmpty()) {
                // Render item background (like a slot)
                fill(poseStack, itemX -1, itemY -1, itemX + ITEM_RENDER_SIZE -1 , itemY + ITEM_RENDER_SIZE -1, 0xFF8B8B8B); // Slot background
                fill(poseStack, itemX, itemY, itemX + ITEM_RENDER_SIZE - 2, itemY + ITEM_RENDER_SIZE - 2, 0xFF373737); // Inner slot color
                
                this.itemRenderer.renderAndDecorateItem(stack, itemX, itemY);
                this.itemRenderer.renderGuiItemDecorations(this.font, stack, itemX, itemY, null);
            }
        }
        int rows = (int)Math.ceil((double)items.size() / ITEMS_PER_ROW);
        if (items.isEmpty()) rows = 1; // Min height for the label
        return y + rows * ITEM_RENDER_SIZE + PADDING * 2;
    }
    
    private int renderCuriosSection(PoseStack poseStack, int x, int y, int mouseX, int mouseY) {
        if (curiosInventory.isEmpty()) {
            return y; // Don't render anything if no Curios
        }
        
        drawString(poseStack, this.font, "Curios", x, y, 0xFFFFFF);
        y += (font.lineHeight + PADDING);
        
        for (Map.Entry<String, List<ItemStack>> entry : curiosInventory.entrySet()) {
            String slotType = entry.getKey();
            List<ItemStack> items = entry.getValue();
            
            if (items.isEmpty()) continue;
            
            // Render slot type name
            drawString(poseStack, this.font, "  " + formatSlotTypeName(slotType), x, y, 0xCCCCCC);
            y += (font.lineHeight + PADDING);
            
            // Render items for this slot type
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack == null || stack.isEmpty()) continue;
                
                int itemX = x + 20 + (i % ITEMS_PER_ROW) * ITEM_RENDER_SIZE; // Indent items slightly
                int itemY = y + (i / ITEMS_PER_ROW) * ITEM_RENDER_SIZE;

                // Render item background (like a slot)
                fill(poseStack, itemX - 1, itemY - 1, itemX + ITEM_RENDER_SIZE - 1, itemY + ITEM_RENDER_SIZE - 1, 0xFF8B8B8B);
                fill(poseStack, itemX, itemY, itemX + ITEM_RENDER_SIZE - 2, itemY + ITEM_RENDER_SIZE - 2, 0xFF373737);
                
                this.itemRenderer.renderAndDecorateItem(stack, itemX, itemY);
                this.itemRenderer.renderGuiItemDecorations(this.font, stack, itemX, itemY, null);
            }
            
            int rows = (int)Math.ceil((double)items.size() / ITEMS_PER_ROW);
            if (rows == 0) rows = 1;
            y += rows * ITEM_RENDER_SIZE + PADDING;
        }
        
        return y + PADDING;
    }
    
    /**
     * Format slot type names to be more user-friendly
     */
    private String formatSlotTypeName(String slotType) {
        if (slotType == null) return "Unknown";
        
        // Convert camelCase or snake_case to Title Case
        String formatted = slotType.replace("_", " ");
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (char c : formatted.toCharArray()) {
            if (Character.isWhitespace(c)) {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        
        return result.toString();
    }
    
    private int renderGenericModdedSection(PoseStack poseStack, int x, int y, int mouseX, int mouseY) {
        if (genericModdedInventory.isEmpty()) {
            return y; // Don't render anything if no generic modded items
        }
        
        drawString(poseStack, this.font, "Generic Modded Items", x, y, 0xFFFFFF);
        y += (font.lineHeight + PADDING);
        
        for (Map.Entry<String, List<ItemStack>> entry : genericModdedInventory.entrySet()) {
            String itemType = entry.getKey();
            List<ItemStack> items = entry.getValue();
            
            if (items.isEmpty()) continue;
            
            // Render item type name
            drawString(poseStack, this.font, "  " + formatSlotTypeName(itemType), x, y, 0xCCCCCC);
            y += (font.lineHeight + PADDING);
            
            // Render items for this type
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack == null || stack.isEmpty()) continue;
                
                int itemX = x + 20 + (i % ITEMS_PER_ROW) * ITEM_RENDER_SIZE; // Indent items slightly
                int itemY = y + (i / ITEMS_PER_ROW) * ITEM_RENDER_SIZE;

                // Render item background (like a slot)
                fill(poseStack, itemX - 1, itemY - 1, itemX + ITEM_RENDER_SIZE - 1, itemY + ITEM_RENDER_SIZE - 1, 0xFF8B8B8B);
                fill(poseStack, itemX, itemY, itemX + ITEM_RENDER_SIZE - 2, itemY + ITEM_RENDER_SIZE - 2, 0xFF373737);
                
                this.itemRenderer.renderAndDecorateItem(stack, itemX, itemY);
                this.itemRenderer.renderGuiItemDecorations(this.font, stack, itemX, itemY, null);
            }
            
            int rows = (int)Math.ceil((double)items.size() / ITEMS_PER_ROW);
            if (rows == 0) rows = 1;
            y += rows * ITEM_RENDER_SIZE + PADDING;
        }
        
        return y + PADDING;
    }
    
    private void renderTooltips(PoseStack poseStack, int mouseX, int mouseY) {
        int currentY = 50;
        currentY = checkAndRenderTooltipForSection(poseStack, mainInventory, 10, currentY, mouseX, mouseY, "main");
        currentY = checkAndRenderTooltipForSection(poseStack, armorInventory, 10, currentY, mouseX, mouseY, "armor");
        currentY = checkAndRenderTooltipForSection(poseStack, offhandInventory, 10, currentY, mouseX, mouseY, "offhand");
        currentY = checkAndRenderTooltipForSection(poseStack, enderChestInventory, 10, currentY, mouseX, mouseY, "enderchest");
        checkAndRenderCuriosTooltips(poseStack, 10, currentY, mouseX, mouseY);
        checkAndRenderGenericModdedTooltips(poseStack, 10, currentY, mouseX, mouseY);
    }

    private int checkAndRenderTooltipForSection(PoseStack poseStack, List<ItemStack> items, int x, int y, int mouseX, int mouseY, String invType) {
        y += (font.lineHeight + PADDING); // Adjust for title height
         for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            int itemX = x + (i % ITEMS_PER_ROW) * ITEM_RENDER_SIZE;
            int itemY = y + (i / ITEMS_PER_ROW) * ITEM_RENDER_SIZE;

            if (stack != null && !stack.isEmpty() && 
                mouseX >= itemX && mouseX < itemX + ITEM_RENDER_SIZE -2 &&
                mouseY >= itemY && mouseY < itemY + ITEM_RENDER_SIZE -2) {
                this.renderTooltip(poseStack, stack, mouseX, mouseY);
                break; // Only render one tooltip at a time
            }
        }
        int rows = (int)Math.ceil((double)items.size() / ITEMS_PER_ROW);
        if (items.isEmpty()) rows = 1;
        return y + rows * ITEM_RENDER_SIZE + PADDING * 2;
    }
    
    private void checkAndRenderCuriosTooltips(PoseStack poseStack, int x, int startY, int mouseX, int mouseY) {
        if (curiosInventory.isEmpty()) return;
        
        int y = startY + (font.lineHeight + PADDING); // Adjust for "Curios" title
        
        for (Map.Entry<String, List<ItemStack>> entry : curiosInventory.entrySet()) {
            List<ItemStack> items = entry.getValue();
            if (items.isEmpty()) continue;
            
            y += (font.lineHeight + PADDING); // Slot type name height
            
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack == null || stack.isEmpty()) continue;
                
                int itemX = x + 20 + (i % ITEMS_PER_ROW) * ITEM_RENDER_SIZE;
                int itemY = y + (i / ITEMS_PER_ROW) * ITEM_RENDER_SIZE;

                if (mouseX >= itemX && mouseX < itemX + ITEM_RENDER_SIZE - 2 &&
                    mouseY >= itemY && mouseY < itemY + ITEM_RENDER_SIZE - 2) {
                    this.renderTooltip(poseStack, stack, mouseX, mouseY);
                    return; // Only render one tooltip at a time
                }
            }
            
            int rows = (int)Math.ceil((double)items.size() / ITEMS_PER_ROW);
            if (rows == 0) rows = 1;
            y += rows * ITEM_RENDER_SIZE + PADDING;
        }
    }

    private void checkAndRenderGenericModdedTooltips(PoseStack poseStack, int x, int startY, int mouseX, int mouseY) {
        if (genericModdedInventory.isEmpty()) return;
        
        int y = startY + (font.lineHeight + PADDING); // Adjust for "Generic Modded Items" title
        
        for (Map.Entry<String, List<ItemStack>> entry : genericModdedInventory.entrySet()) {
            List<ItemStack> items = entry.getValue();
            if (items.isEmpty()) continue;
            
            y += (font.lineHeight + PADDING); // Item type name height
            
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack == null || stack.isEmpty()) continue;
                
                int itemX = x + 20 + (i % ITEMS_PER_ROW) * ITEM_RENDER_SIZE;
                int itemY = y + (i / ITEMS_PER_ROW) * ITEM_RENDER_SIZE;

                if (mouseX >= itemX && mouseX < itemX + ITEM_RENDER_SIZE - 2 &&
                    mouseY >= itemY && mouseY < itemY + ITEM_RENDER_SIZE - 2) {
                    this.renderTooltip(poseStack, stack, mouseX, mouseY);
                    return; // Only render one tooltip at a time
                }
            }
            
            int rows = (int)Math.ceil((double)items.size() / ITEMS_PER_ROW);
            if (rows == 0) rows = 1;
            y += rows * ITEM_RENDER_SIZE + PADDING;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            // Check main inventory items
            if (checkClickOnItem(mainInventory, 10, 50 + font.lineHeight + PADDING, mouseX, mouseY, "main")) return true;
            // Check armor items (adjust Y based on main inventory rendering)
            int armorY = 50 + font.lineHeight + PADDING + (int)Math.ceil((double)mainInventory.size() / ITEMS_PER_ROW) * ITEM_RENDER_SIZE + PADDING * 2 + font.lineHeight + PADDING;
            if (checkClickOnItem(armorInventory, 10, armorY, mouseX, mouseY, "armor")) return true;
            // Check offhand items (adjust Y...)
            int offhandY = armorY + (int)Math.ceil((double)armorInventory.size() / ITEMS_PER_ROW) * ITEM_RENDER_SIZE + PADDING * 2 + font.lineHeight + PADDING;
            if (checkClickOnItem(offhandInventory, 10, offhandY, mouseX, mouseY, "offhand")) return true;
            // Check ender chest items (adjust Y...)
            int enderchestY = offhandY + (int)Math.ceil((double)offhandInventory.size() / ITEMS_PER_ROW) * ITEM_RENDER_SIZE + PADDING * 2 + font.lineHeight + PADDING;
            if (checkClickOnItem(enderChestInventory, 10, enderchestY, mouseX, mouseY, "enderchest")) return true;
            // Check Curios items
            int curiosY = enderchestY + (int)Math.ceil((double)enderChestInventory.size() / ITEMS_PER_ROW) * ITEM_RENDER_SIZE + PADDING * 2;
            if (checkClickOnCuriosItems(10, curiosY, mouseX, mouseY)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean checkClickOnItem(List<ItemStack> items, int x, int yStartSection, double mouseX, double mouseY, String invType) {
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack == null || stack.isEmpty()) continue;

            int itemX = x + (i % ITEMS_PER_ROW) * ITEM_RENDER_SIZE;
            int itemY = yStartSection + (i / ITEMS_PER_ROW) * ITEM_RENDER_SIZE;

            if (mouseX >= itemX && mouseX < itemX + ITEM_RENDER_SIZE - 2 && 
                mouseY >= itemY && mouseY < itemY + ITEM_RENDER_SIZE - 2) {
                
                LOGGER.info("Clicked on item in backup GUI: Slot {}, Type: {}, Item: {}", i, invType, stack.getDisplayName().getString());
                PacketHandler.sendToServer(new RequestTakeItemPacket(packetData.getBackupId(), invType, i));
                return true;
            }
        }
        return false;
    }
    
    private boolean checkClickOnCuriosItems(int x, int startY, double mouseX, double mouseY) {
        if (curiosInventory.isEmpty()) return false;
        
        int y = startY + (font.lineHeight + PADDING); // Adjust for "Curios" title
        
        for (Map.Entry<String, List<ItemStack>> entry : curiosInventory.entrySet()) {
            String slotType = entry.getKey();
            List<ItemStack> items = entry.getValue();
            if (items.isEmpty()) continue;
            
            y += (font.lineHeight + PADDING); // Slot type name height
            
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                if (stack == null || stack.isEmpty()) continue;
                
                int itemX = x + 20 + (i % ITEMS_PER_ROW) * ITEM_RENDER_SIZE;
                int itemY = y + (i / ITEMS_PER_ROW) * ITEM_RENDER_SIZE;

                if (mouseX >= itemX && mouseX < itemX + ITEM_RENDER_SIZE - 2 && 
                    mouseY >= itemY && mouseY < itemY + ITEM_RENDER_SIZE - 2) {
                    
                    LOGGER.info("Clicked on Curios item: Slot {}, Type: {}, Item: {}", i, slotType, stack.getDisplayName().getString());
                    // Send request with slotType as the inventory type for Curios
                    PacketHandler.sendToServer(new RequestTakeItemPacket(packetData.getBackupId(), "curios:" + slotType, i));
                    return true;
                }
            }
            
            int rows = (int)Math.ceil((double)items.size() / ITEMS_PER_ROW);
            if (rows == 0) rows = 1;
            y += rows * ITEM_RENDER_SIZE + PADDING;
        }
        
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false; // This allows game to continue in background if needed, though not typical for such a screen
    }
} 