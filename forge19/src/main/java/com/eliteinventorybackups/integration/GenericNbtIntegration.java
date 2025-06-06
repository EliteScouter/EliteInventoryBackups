package com.eliteinventorybackups.integration;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.*;

/**
 * Generic NBT integration for capturing and restoring all player data as a fallback.
 * This ensures that any modded inventories not covered by specific integrations are preserved.
 */
public class GenericNbtIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // NBT paths to exclude from generic backup (handled by specific integrations)
    private static final Set<String> EXCLUDED_NBT_PATHS = Set.of(
        "Inventory",           // Vanilla main inventory
        "EnderItems",          // Vanilla ender chest
        "curios:inventory"     // Curios data (handled by CuriosIntegration)
    );
    
    /**
     * Backup all player NBT data, excluding paths handled by specific integrations
     * @param player The player to backup
     * @return Serialized player NBT data, or null if failed
     */
    public static String backupPlayerNbt(ServerPlayer player) {
        try {
            // Get full player NBT
            CompoundTag fullNbt = new CompoundTag();
            player.save(fullNbt);
            
            // Filter out data handled by specific integrations
            CompoundTag filteredNbt = filterNbtData(fullNbt);
            
            // Only return if there's actual modded data
            if (hasModdedInventoryData(filteredNbt)) {
                return filteredNbt.toString();
            } else {
                return "{}"; // No modded data to backup
            }
            
        } catch (Exception e) {
            LOGGER.error("Failed to backup player NBT for {}: {}", player.getName().getString(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Restore player NBT data, being careful not to overwrite vanilla or specifically handled data
     * @param player The player to restore to
     * @param nbtData Serialized NBT data
     * @return true if restoration was successful
     */
    public static boolean restorePlayerNbt(ServerPlayer player, String nbtData) {
        if (nbtData == null || nbtData.isEmpty() || nbtData.equals("{}")) {
            return true; // No data to restore is considered success
        }
        
        try {
            CompoundTag nbtToRestore = TagParser.parseTag(nbtData);
            
            // Get current player NBT
            CompoundTag currentNbt = new CompoundTag();
            player.save(currentNbt);
            
            // Merge the modded data into current NBT, avoiding conflicts
            mergeNbtData(currentNbt, nbtToRestore);
            
            // Restore the merged NBT to the player
            player.load(currentNbt);
            
            LOGGER.debug("Successfully restored generic NBT data for player {}", player.getName().getString());
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to restore player NBT for {}: {}", player.getName().getString(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Extract modded inventory items from NBT data for GUI display
     * @param nbtData Serialized NBT data
     * @return Map of mod ID to list of items found in that mod's data
     */
    public static Map<String, List<ItemStack>> extractModdedItems(String nbtData) {
        Map<String, List<ItemStack>> moddedItems = new HashMap<>();
        
        if (nbtData == null || nbtData.isEmpty() || nbtData.equals("{}")) {
            return moddedItems;
        }
        
        try {
            CompoundTag nbt = TagParser.parseTag(nbtData);
            
            // Look for common modded inventory patterns
            extractItemsFromNbt(nbt, "", moddedItems);
            
        } catch (Exception e) {
            LOGGER.debug("Failed to extract modded items from NBT: {}", e.getMessage());
        }
        
        return moddedItems;
    }
    
    /**
     * Filter NBT data to remove paths handled by specific integrations
     */
    private static CompoundTag filterNbtData(CompoundTag originalNbt) {
        CompoundTag filtered = originalNbt.copy();
        
        // Remove excluded paths
        for (String excludedPath : EXCLUDED_NBT_PATHS) {
            removeNbtPath(filtered, excludedPath);
        }
        
        return filtered;
    }
    
    /**
     * Remove a specific NBT path from the compound tag
     */
    private static void removeNbtPath(CompoundTag nbt, String path) {
        if (path.contains(":")) {
            // Handle namespaced paths like "curios:inventory"
            String[] parts = path.split(":", 2);
            if (nbt.contains(parts[0], Tag.TAG_COMPOUND)) {
                CompoundTag subTag = nbt.getCompound(parts[0]);
                subTag.remove(parts[1]);
                if (subTag.isEmpty()) {
                    nbt.remove(parts[0]);
                }
            }
        } else {
            // Handle simple paths
            nbt.remove(path);
        }
    }
    
    /**
     * Check if the NBT contains any modded inventory data worth backing up
     */
    private static boolean hasModdedInventoryData(CompoundTag nbt) {
        // Look for common indicators of modded inventory data
        for (String key : nbt.getAllKeys()) {
            if (key.contains(":") || // Namespaced keys indicate mod data
                key.toLowerCase().contains("inventory") ||
                key.toLowerCase().contains("items") ||
                key.toLowerCase().contains("container")) {
                return true;
            }
        }
        
        // Check for any compound tags that might contain modded data
        for (String key : nbt.getAllKeys()) {
            Tag tag = nbt.get(key);
            if (tag instanceof CompoundTag && hasModdedInventoryData((CompoundTag) tag)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Merge NBT data, prioritizing existing data to avoid conflicts
     */
    private static void mergeNbtData(CompoundTag target, CompoundTag source) {
        for (String key : source.getAllKeys()) {
            if (!EXCLUDED_NBT_PATHS.contains(key)) {
                Tag sourceTag = source.get(key);
                
                if (target.contains(key) && sourceTag instanceof CompoundTag && target.get(key) instanceof CompoundTag) {
                    // Recursively merge compound tags
                    mergeNbtData(target.getCompound(key), (CompoundTag) sourceTag);
                } else if (!target.contains(key)) {
                    // Only add if not already present (avoid overwriting)
                    target.put(key, sourceTag.copy());
                }
            }
        }
    }
    
    /**
     * Recursively extract ItemStacks from NBT data
     */
    private static void extractItemsFromNbt(CompoundTag nbt, String currentPath, Map<String, List<ItemStack>> result) {
        for (String key : nbt.getAllKeys()) {
            String fullPath = currentPath.isEmpty() ? key : currentPath + "." + key;
            Tag tag = nbt.get(key);
            
            if (tag instanceof CompoundTag) {
                CompoundTag compound = (CompoundTag) tag;
                
                // Check if this looks like an ItemStack
                if (compound.contains("id") && compound.contains("Count")) {
                    try {
                        ItemStack stack = ItemStack.of(compound);
                        if (!stack.isEmpty()) {
                            String modId = extractModId(fullPath, stack);
                            result.computeIfAbsent(modId, k -> new ArrayList<>()).add(stack);
                        }
                    } catch (Exception e) {
                        // Not a valid ItemStack, continue
                    }
                }
                
                // Recursively search compound tags
                extractItemsFromNbt(compound, fullPath, result);
                
            } else if (tag instanceof ListTag) {
                ListTag list = (ListTag) tag;
                
                // Check if this is a list of ItemStacks
                for (int i = 0; i < list.size(); i++) {
                    Tag element = list.get(i);
                    if (element instanceof CompoundTag) {
                        CompoundTag compound = (CompoundTag) element;
                        if (compound.contains("id") && compound.contains("Count")) {
                            try {
                                ItemStack stack = ItemStack.of(compound);
                                if (!stack.isEmpty()) {
                                    String modId = extractModId(fullPath, stack);
                                    result.computeIfAbsent(modId, k -> new ArrayList<>()).add(stack);
                                }
                            } catch (Exception e) {
                                // Not a valid ItemStack, continue
                            }
                        } else {
                            // Recursively search compound tags in lists
                            extractItemsFromNbt(compound, fullPath + "[" + i + "]", result);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Extract mod ID from path or ItemStack
     */
    private static String extractModId(String path, ItemStack stack) {
        // Try to get mod ID from item registry name
        String itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()) != null ? 
                       net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString() : 
                       stack.getItem().toString();
        
        if (itemId.contains(":")) {
            String modId = itemId.split(":", 2)[0];
            if (!modId.equals("minecraft")) {
                return modId;
            }
        }
        
        // Try to extract from NBT path
        if (path.contains(":")) {
            String pathModId = path.split(":", 2)[0];
            if (!pathModId.equals("minecraft")) {
                return pathModId;
            }
        }
        
        // Fallback to generic
        return "unknown_mod";
    }
    
    /**
     * Check if generic NBT integration is available (always true for this fallback)
     */
    public static boolean isAvailable() {
        return true;
    }
} 