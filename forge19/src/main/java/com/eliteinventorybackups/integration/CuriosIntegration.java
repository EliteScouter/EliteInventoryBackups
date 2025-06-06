package com.eliteinventorybackups.integration;

import com.eliteinventorybackups.util.InventorySerializer;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Integration with the Curios mod for backing up and restoring trinket/accessory items.
 * Uses reflection to maintain compatibility without hard dependencies.
 */
public class CuriosIntegration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CURIOS_MOD_ID = "curios";
    
    private static boolean isAvailable = false;
    private static Class<?> curiosApiClass;
    private static Method getCuriosInventoryMethod;
    private static Object curiosHelper; // For storing static helper instance
    
    static {
        initialize();
    }
    
    private static void initialize() {
        try {
            if (!ModList.get().isLoaded(CURIOS_MOD_ID)) {
                LOGGER.debug("Curios mod not found, disabling integration");
                return;
            }
            
            // Load the correct Curios API classes based on actual examination
            curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            
            // Get the getCuriosHelper method which returns ICuriosHelper
            Method getCuriosHelperMethod = curiosApiClass.getMethod("getCuriosHelper");
            curiosHelper = getCuriosHelperMethod.invoke(null);
            
            // Get the getCuriosHandler method from ICuriosHelper
            getCuriosInventoryMethod = curiosHelper.getClass().getMethod("getCuriosHandler", net.minecraft.world.entity.LivingEntity.class);
            
            isAvailable = true;
            LOGGER.info("Curios integration initialized successfully using getCuriosHelper");
            
        } catch (Exception e) {
            LOGGER.warn("Failed to initialize Curios integration: {}", e.getMessage());
            isAvailable = false;
        }
    }
    
    /**
     * Check if Curios is available and integration is working
     */
    public static boolean isAvailable() {
        return isAvailable;
    }
    
    /**
     * Backup all Curios items for a player
     * @param player The player to backup Curios for
     * @return Serialized Curios inventory data, or null if not available
     */
    public static String backupCurios(ServerPlayer player) {
        if (!isAvailable) {
            return null;
        }
        
        try {
            // Call getCuriosHandler on the helper with the player
            Object curiosHandlerResult = getCuriosInventoryMethod.invoke(curiosHelper, player);
            
            if (curiosHandlerResult == null) {
                return "{}"; // Empty curios
            }
            
            // Handle LazyOptional result
            Object curiosHandler = null;
            try {
                Method orElseMethod = curiosHandlerResult.getClass().getMethod("orElse", Object.class);
                curiosHandler = orElseMethod.invoke(curiosHandlerResult, (Object) null);
            } catch (Exception e) {
                curiosHandler = curiosHandlerResult;
            }
            
            if (curiosHandler == null) {
                return "{}";
            }
            
            // Get all curios slots using getCurios() method
            Method getCuriosMethod = curiosHandler.getClass().getMethod("getCurios");
            Object curiosMap = getCuriosMethod.invoke(curiosHandler);
            
            Map<String, List<ItemStack>> curiosSlots = new HashMap<>();
            
            if (curiosMap instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, ?> slots = (Map<String, ?>) curiosMap;
                
                for (String slotType : slots.keySet()) {
                    Object slotHandler = slots.get(slotType);
                    if (slotHandler != null) {
                        List<ItemStack> slotStacks = extractStacksFromSlot(slotHandler);
                        if (!slotStacks.isEmpty()) {
                            curiosSlots.put(slotType, slotStacks);
                        }
                    }
                }
            }
            
            return serializeCuriosData(curiosSlots);
            
        } catch (Exception e) {
            LOGGER.error("Failed to backup Curios for player {}: {}", player.getName().getString(), e.getMessage(), e);
            return null;
        }
    }
    
    private static List<ItemStack> extractStacksFromSlot(Object slotHandler) {
        List<ItemStack> stacks = new ArrayList<>();
        try {
            // Get the stacks handler using getStacks()
            Method getStacksMethod = slotHandler.getClass().getMethod("getStacks");
            Object stacksHandler = getStacksMethod.invoke(slotHandler);
            
            if (stacksHandler != null) {
                // Get size using getSlots()
                Method getSlotsMethod = stacksHandler.getClass().getMethod("getSlots");
                int size = (Integer) getSlotsMethod.invoke(stacksHandler);
                
                // Get each stack using getStackInSlot(int)
                Method getStackInSlotMethod = stacksHandler.getClass().getMethod("getStackInSlot", int.class);
                for (int i = 0; i < size; i++) {
                    ItemStack stack = (ItemStack) getStackInSlotMethod.invoke(stacksHandler, i);
                    stacks.add(stack != null ? stack : ItemStack.EMPTY);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to extract stacks from slot: {}", e.getMessage());
        }
        return stacks;
    }
    
    /**
     * Restore Curios items for a player
     * @param player The player to restore Curios for
     * @param curiosData Serialized Curios data
     * @return true if restoration was successful
     */
    public static boolean restoreCurios(ServerPlayer player, String curiosData) {
        if (!isAvailable || curiosData == null || curiosData.isEmpty() || curiosData.equals("{}")) {
            return false;
        }
        
        try {
            // Parse the curios data
            Map<String, List<ItemStack>> curiosSlots = deserializeCuriosData(curiosData);
            if (curiosSlots.isEmpty()) {
                return true;
            }
            
            // Get Curios handler using the same method as backup
            Object curiosHandlerResult = getCuriosInventoryMethod.invoke(curiosHelper, player);
            
            if (curiosHandlerResult == null) {
                return false;
            }
            
            // Handle LazyOptional result
            Object curiosHandler = null;
            try {
                Method orElseMethod = curiosHandlerResult.getClass().getMethod("orElse", Object.class);
                curiosHandler = orElseMethod.invoke(curiosHandlerResult, (Object) null);
            } catch (Exception e) {
                curiosHandler = curiosHandlerResult;
            }
            
            if (curiosHandler == null) {
                return false;
            }
            
            // Get curios map using getCurios()
            Method getCuriosMethod = curiosHandler.getClass().getMethod("getCurios");
            Object curiosMap = getCuriosMethod.invoke(curiosHandler);
            
            if (curiosMap instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, ?> slots = (Map<String, ?>) curiosMap;
                
                // Restore each slot type
                for (String slotType : curiosSlots.keySet()) {
                    Object slotHandler = slots.get(slotType);
                    if (slotHandler != null) {
                        restoreStacksToSlot(slotHandler, curiosSlots.get(slotType));
                    }
                }
            }
            
            syncCuriosToClient(player);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to restore Curios for player {}: {}", player.getName().getString(), e.getMessage(), e);
            return false;
        }
    }
    
    private static void restoreStacksToSlot(Object slotHandler, List<ItemStack> stacks) {
        try {
            // Get the stacks handler using getStacks()
            Method getStacksMethod = slotHandler.getClass().getMethod("getStacks");
            Object stacksHandler = getStacksMethod.invoke(slotHandler);
            
            if (stacksHandler != null) {
                // Set each stack using setStackInSlot(int, ItemStack)
                Method setStackInSlotMethod = stacksHandler.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);
                for (int i = 0; i < stacks.size(); i++) {
                    setStackInSlotMethod.invoke(stacksHandler, i, stacks.get(i));
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to restore stacks to slot: {}", e.getMessage());
        }
    }
    
    /**
     * Get a specific Curios item by slot type and index
     * @param player The player to get the item from
     * @param slotIdentifier Slot identifier in format "slottype:index" or just "slottype" for index 0
     * @return The ItemStack in that slot, or empty stack if not found
     */
    public static ItemStack getCuriosItem(ServerPlayer player, String slotIdentifier) {
        if (!isAvailable) {
            return ItemStack.EMPTY;
        }
        
        try {
            String[] parts = slotIdentifier.split(":");
            String slotType = parts[0];
            int index = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            
            // Get curiosHandler using the same approach
            Object curiosHandlerResult = getCuriosInventoryMethod.invoke(curiosHelper, player);
            
            if (curiosHandlerResult == null) {
                return ItemStack.EMPTY;
            }
            
            Object curiosHandler = null;
            try {
                Method orElseMethod = curiosHandlerResult.getClass().getMethod("orElse", Object.class);
                curiosHandler = orElseMethod.invoke(curiosHandlerResult, (Object) null);
            } catch (Exception e) {
                curiosHandler = curiosHandlerResult;
            }
            
            if (curiosHandler == null) {
                return ItemStack.EMPTY;
            }
            
            // Get curios map
            Method getCuriosMethod = curiosHandler.getClass().getMethod("getCurios");
            Object curiosMap = getCuriosMethod.invoke(curiosHandler);
            
            if (curiosMap instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, ?> slots = (Map<String, ?>) curiosMap;
                
                Object slotHandler = slots.get(slotType);
                if (slotHandler != null) {
                    Method getStacksMethod = slotHandler.getClass().getMethod("getStacks");
                    Object stacksHandler = getStacksMethod.invoke(slotHandler);
                    
                    if (stacksHandler != null) {
                        Method getStackInSlotMethod = stacksHandler.getClass().getMethod("getStackInSlot", int.class);
                        return (ItemStack) getStackInSlotMethod.invoke(stacksHandler, index);
                    }
                }
            }
            
        } catch (Exception e) {
            LOGGER.debug("Failed to get Curios item: {}", e.getMessage());
        }
        
        return ItemStack.EMPTY;
    }
    
    /**
     * Set a specific Curios item by slot type and index
     * @param player The player to set the item for
     * @param slotIdentifier Slot identifier in format "slottype:index" or just "slottype" for index 0
     * @param stack The ItemStack to set
     * @return true if successful
     */
    public static boolean setCuriosItem(ServerPlayer player, String slotIdentifier, ItemStack stack) {
        if (!isAvailable) {
            return false;
        }
        
        try {
            String[] parts = slotIdentifier.split(":");
            String slotType = parts[0];
            int index = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            
            // Get curiosHandler using the same approach
            Object curiosHandlerResult = getCuriosInventoryMethod.invoke(curiosHelper, player);
            
            if (curiosHandlerResult == null) {
                return false;
            }
            
            Object curiosHandler = null;
            try {
                Method orElseMethod = curiosHandlerResult.getClass().getMethod("orElse", Object.class);
                curiosHandler = orElseMethod.invoke(curiosHandlerResult, (Object) null);
            } catch (Exception e) {
                curiosHandler = curiosHandlerResult;
            }
            
            if (curiosHandler == null) {
                return false;
            }
            
            // Get curios map
            Method getCuriosMethod = curiosHandler.getClass().getMethod("getCurios");
            Object curiosMap = getCuriosMethod.invoke(curiosHandler);
            
            if (curiosMap instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, ?> slots = (Map<String, ?>) curiosMap;
                
                Object slotHandler = slots.get(slotType);
                if (slotHandler != null) {
                    Method getStacksMethod = slotHandler.getClass().getMethod("getStacks");
                    Object stacksHandler = getStacksMethod.invoke(slotHandler);
                    
                    if (stacksHandler != null) {
                        Method setStackInSlotMethod = stacksHandler.getClass().getMethod("setStackInSlot", int.class, ItemStack.class);
                        setStackInSlotMethod.invoke(stacksHandler, index, stack);
                        syncCuriosToClient(player);
                        return true;
                    }
                }
            }
            
        } catch (Exception e) {
            LOGGER.debug("Failed to set Curios item: {}", e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Sync Curios to client (trigger a network update)
     */
    private static void syncCuriosToClient(ServerPlayer player) {
        try {
            // Try to trigger a network sync
            Class<?> networkHandlerClass = Class.forName("top.theillusivec4.curios.common.network.NetworkHandler");
            Method sendToPlayerMethod = networkHandlerClass.getMethod("sendToPlayer", Object.class, ServerPlayer.class);
            
            // Create a sync packet
            Class<?> syncPacketClass = Class.forName("top.theillusivec4.curios.common.network.server.SPacketSyncStack");
            Object syncPacket = syncPacketClass.getConstructor(ServerPlayer.class).newInstance(player);
            
            sendToPlayerMethod.invoke(null, syncPacket, player);
            
        } catch (Exception e) {
            LOGGER.debug("Failed to sync Curios to client: {}", e.getMessage());
        }
    }
    
    /**
     * Serialize curios data to JSON-like string format
     */
    private static String serializeCuriosData(Map<String, List<ItemStack>> curiosSlots) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        
        boolean first = true;
        for (Map.Entry<String, List<ItemStack>> entry : curiosSlots.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            
            sb.append("\"").append(entry.getKey()).append("\":[");
            
            List<ItemStack> stacks = entry.getValue();
            for (int i = 0; i < stacks.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                // Escape quotes in the NBT string before wrapping in JSON quotes
                String nbtString = InventorySerializer.serializeItemStack(stacks.get(i));
                String escapedNbtString = nbtString.replace("\"", "\\\"");
                sb.append("\"").append(escapedNbtString).append("\"");
            }
            sb.append("]");
        }
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Deserialize curios data from JSON-like string format
     */
    private static Map<String, List<ItemStack>> deserializeCuriosData(String curiosData) {
        Map<String, List<ItemStack>> curiosSlots = new HashMap<>();
        
        try {
            // Simple JSON-like parsing
            if (curiosData.startsWith("{") && curiosData.endsWith("}")) {
                String content = curiosData.substring(1, curiosData.length() - 1);
                if (content.trim().isEmpty()) {
                    return curiosSlots;
                }
                
                String[] pairs = content.split(",(?=\")");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("\":\\[", 2);
                    if (keyValue.length == 2) {
                        String slotType = keyValue[0].substring(1); // Remove leading quote
                        String stacksData = keyValue[1];
                        if (stacksData.endsWith("]")) {
                            stacksData = stacksData.substring(0, stacksData.length() - 1);
                        }
                        
                        List<ItemStack> stacks = new ArrayList<>();
                        if (!stacksData.trim().isEmpty()) {
                            String[] stackArray = stacksData.split("\",\"");
                            for (String stackStr : stackArray) {
                                // Remove quotes and unescape the NBT string
                                String cleanStackStr = stackStr.replace("\"", "");
                                String unescapedNbtString = cleanStackStr.replace("\\\"", "\"");
                                ItemStack stack = InventorySerializer.deserializeItemStack(unescapedNbtString);
                                stacks.add(stack);
                            }
                        }
                        
                        curiosSlots.put(slotType, stacks);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to deserialize Curios data: {}", e.getMessage());
        }
        
        return curiosSlots;
    }
} 