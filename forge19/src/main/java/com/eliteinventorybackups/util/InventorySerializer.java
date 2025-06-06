package com.eliteinventorybackups.util;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class InventorySerializer {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Serializes a list of ItemStacks into a single NBT CompoundTag, then to a Mojangson string.
     */
    public static String serializeItemListToString(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return "{}"; // Empty NBT compound string
        }
        CompoundTag rootTag = new CompoundTag();
        ListTag itemListTag = new ListTag();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack != null && !stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putByte("Slot", (byte) i); // Store original slot, might be useful
                stack.save(itemTag); // ItemStack.save(CompoundTag) writes its data to the given tag
                itemListTag.add(itemTag);
            }
        }
        rootTag.put("Items", itemListTag);
        rootTag.putInt("Size", items.size()); // Store original size
        return rootTag.toString(); // Converts CompoundTag to Mojangson string
    }

    /**
     * Deserializes a Mojangson string (representing a CompoundTag with an "Items" ListTag)
     * back into a List of ItemStacks. The size of the list will match the original slot count,
     * with empty ItemStacks for empty slots.
     * Also handles legacy array formats for backward compatibility.
     */
    public static List<ItemStack> deserializeStringToList(String nbtString) {
        if (nbtString == null || nbtString.isEmpty() || nbtString.equals("{}")) {
            return new ArrayList<>();
        }
        
        // Check if this is a legacy array format like ["{...}", "{...}"]
        if (nbtString.startsWith("[") && nbtString.endsWith("]")) {
            return deserializeLegacyArrayFormat(nbtString);
        }
        
        try {
            CompoundTag rootTag = TagParser.parseTag(nbtString);
            ListTag itemListTag = rootTag.getList("Items", CompoundTag.TAG_COMPOUND);
            
            // Get the original size if stored, otherwise estimate
            int originalSize = rootTag.contains("Size") ? rootTag.getInt("Size") : 
                              (itemListTag.size() > 0 ? getMaxSlot(itemListTag) + 1 : 0);
            
            // Create list with correct size, filled with empty ItemStacks
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < originalSize; i++) {
                items.add(ItemStack.EMPTY);
            }
            
            // Place items in their correct slots
            for (int i = 0; i < itemListTag.size(); i++) {
                CompoundTag itemTag = itemListTag.getCompound(i);
                int slot = itemTag.getByte("Slot") & 0xFF; // Convert to unsigned
                if (slot < items.size()) {
                    items.set(slot, ItemStack.of(itemTag));
                } else {
                    // If slot is out of bounds, just add to end (fallback)
                    items.add(ItemStack.of(itemTag));
                }
            }
            return items;
        } catch (Exception e) {
            LOGGER.error("Failed to deserialize ItemStack list from NBT string: {}", nbtString, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Handle legacy array format like ["{...}", "{...}"] for backward compatibility
     */
    private static List<ItemStack> deserializeLegacyArrayFormat(String arrayString) {
        List<ItemStack> items = new ArrayList<>();
        try {
            // Remove the outer brackets
            String content = arrayString.substring(1, arrayString.length() - 1);
            if (content.trim().isEmpty()) {
                return items;
            }
            
            // Split by }, but be careful about nested NBT
            List<String> itemStrings = splitNbtArray(content);
            
            for (String itemStr : itemStrings) {
                String cleanItemStr = itemStr.trim();
                if (cleanItemStr.startsWith("\"") && cleanItemStr.endsWith("\"")) {
                    // Remove quotes and unescape
                    cleanItemStr = cleanItemStr.substring(1, cleanItemStr.length() - 1);
                    cleanItemStr = cleanItemStr.replace("\\\"", "\"");
                }
                
                if (!cleanItemStr.isEmpty() && !cleanItemStr.equals("{}")) {
                    ItemStack stack = deserializeItemStack(cleanItemStr);
                    items.add(stack);
                } else {
                    items.add(ItemStack.EMPTY);
                }
            }
            
            LOGGER.debug("Successfully parsed legacy array format with {} items", items.size());
            return items;
            
        } catch (Exception e) {
            LOGGER.error("Failed to parse legacy array format: {}", arrayString, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Split NBT array content while respecting nested structures
     */
    private static List<String> splitNbtArray(String content) {
        List<String> items = new ArrayList<>();
        int start = 0;
        int braceLevel = 0;
        boolean inQuotes = false;
        boolean escaped = false;
        
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"' && !escaped) {
                inQuotes = !inQuotes;
            }
            
            if (!inQuotes) {
                if (c == '{') {
                    braceLevel++;
                } else if (c == '}') {
                    braceLevel--;
                } else if (c == ',' && braceLevel == 0) {
                    // This is a top-level comma separator
                    String item = content.substring(start, i).trim();
                    if (!item.isEmpty()) {
                        items.add(item);
                    }
                    start = i + 1;
                }
            }
        }
        
        // Add the last item
        String lastItem = content.substring(start).trim();
        if (!lastItem.isEmpty()) {
            items.add(lastItem);
        }
        
        return items;
    }

    /**
     * Helper method to find the maximum slot number in the item list
     */
    private static int getMaxSlot(ListTag itemListTag) {
        int maxSlot = 0;
        for (int i = 0; i < itemListTag.size(); i++) {
            CompoundTag itemTag = itemListTag.getCompound(i);
            int slot = itemTag.getByte("Slot") & 0xFF;
            if (slot > maxSlot) {
                maxSlot = slot;
            }
        }
        return maxSlot;
    }

    /**
     * Serializes a single ItemStack to a string format
     */
    public static String serializeItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "{}";
        }
        try {
            CompoundTag tag = new CompoundTag();
            stack.save(tag);
            return tag.toString();
        } catch (Exception e) {
            LOGGER.error("Failed to serialize ItemStack: {}", stack, e);
            return "{}";
        }
    }

    /**
     * Deserializes a string back into an ItemStack
     */
    public static ItemStack deserializeItemStack(String nbtString) {
        if (nbtString == null || nbtString.isEmpty() || nbtString.equals("{}")) {
            return ItemStack.EMPTY;
        }
        try {
            CompoundTag tag = TagParser.parseTag(nbtString);
            return ItemStack.of(tag);
        } catch (Exception e) {
            LOGGER.error("Failed to deserialize ItemStack from NBT string: {}", nbtString, e);
            return ItemStack.EMPTY;
        }
    }

    // It might be better to serialize the player's inventory components (main, armor, offhand) individually
    // rather than one massive list, to make restoration more granular if needed.
    // For EnderChest, it's a separate inventory object usually.
}