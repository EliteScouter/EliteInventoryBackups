package com.eliteinventorybackups.network;

import com.eliteinventorybackups.EliteInventoryBackups;
import com.eliteinventorybackups.database.DatabaseManager;
import com.eliteinventorybackups.model.BackupEntry;
import com.eliteinventorybackups.util.InventorySerializer;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.Supplier;

public class RequestTakeItemPacket {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final int backupId;
    private final String inventoryType;
    private final int slotIndex;

    public RequestTakeItemPacket(int backupId, String inventoryType, int slotIndex) {
        this.backupId = backupId;
        this.inventoryType = inventoryType;
        this.slotIndex = slotIndex;
    }

    public static void encode(RequestTakeItemPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.backupId);
        buf.writeUtf(msg.inventoryType);
        buf.writeInt(msg.slotIndex);
    }

    public static RequestTakeItemPacket decode(FriendlyByteBuf buf) {
        return new RequestTakeItemPacket(buf.readInt(), buf.readUtf(), buf.readInt());
    }

    public static void handle(RequestTakeItemPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                LOGGER.warn("RequestTakeItemPacket received from null sender.");
                return;
            }

            DatabaseManager dbManager = EliteInventoryBackups.getDatabaseManager();
            if (dbManager == null) {
                player.sendSystemMessage(Component.literal("Error: DatabaseManager not available on server."));
                LOGGER.error("DatabaseManager is null while handling RequestTakeItemPacket.");
                return;
            }

            BackupEntry backupEntry = dbManager.getBackupById(msg.backupId);
            if (backupEntry == null) {
                player.sendSystemMessage(Component.literal("Error: Backup ID " + msg.backupId + " not found."));
                LOGGER.warn("Could not find backup ID {} for RequestTakeItemPacket from {}", msg.backupId, player.getName().getString());
                return;
            }

            String serializedInventory;
            switch (msg.inventoryType.toLowerCase()) {
                case "main": serializedInventory = backupEntry.inventoryMain(); break;
                case "armor": serializedInventory = backupEntry.inventoryArmor(); break;
                case "offhand": serializedInventory = backupEntry.inventoryOffhand(); break;
                case "enderchest": serializedInventory = backupEntry.inventoryEnderChest(); break;
                default:
                    // Check if it's a Curios inventory type (format: "curios:slottype")
                    if (msg.inventoryType.startsWith("curios:")) {
                        String slotType = msg.inventoryType.substring(7); // Remove "curios:" prefix
                        serializedInventory = getCuriosSlotInventory(backupEntry.inventoryCurios(), slotType);
                        if (serializedInventory == null) {
                            player.sendSystemMessage(Component.literal("Error: Curios slot type '" + slotType + "' not found in backup."));
                            LOGGER.warn("Curios slot type '{}' not found in backup ID {} for player {}", slotType, msg.backupId, player.getName().getString());
                            return;
                        }
                        break;
                    }
                    player.sendSystemMessage(Component.literal("Error: Invalid inventory type '" + msg.inventoryType + "'."));
                    LOGGER.warn("Invalid inventory type '{}' in RequestTakeItemPacket from {}", msg.inventoryType, player.getName().getString());
                    return;
            }

            if (serializedInventory == null || serializedInventory.isEmpty() || serializedInventory.equals("{}")) {
                player.sendSystemMessage(Component.literal("Error: Selected inventory section is empty or not found in backup."));
                return;
            }

            List<ItemStack> items = InventorySerializer.deserializeStringToList(serializedInventory);
            if (msg.slotIndex < 0 || msg.slotIndex >= items.size()) {
                player.sendSystemMessage(Component.literal("Error: Invalid slot index " + msg.slotIndex + " for the selected inventory section."));
                LOGGER.warn("Invalid slot index {} (size {}) for inv type '{}' in RequestTakeItemPacket from {}", msg.slotIndex, items.size(), msg.inventoryType, player.getName().getString());
                return;
            }

            ItemStack itemToGive = items.get(msg.slotIndex);
            if (itemToGive == null || itemToGive.isEmpty()) {
                player.sendSystemMessage(Component.literal("Error: The selected slot is empty in the backup."));
                return;
            }

            // Give a copy to the player
            player.getInventory().placeItemBackInInventory(itemToGive.copy());
            player.sendSystemMessage(Component.literal("Took item: " + itemToGive.getDisplayName().getString()));
            LOGGER.info("Player {} took item {} (slot {}, type {}) from backup ID {}", 
                player.getName().getString(), itemToGive.getDisplayName().getString(), msg.slotIndex, msg.inventoryType, msg.backupId);

        });
        ctx.get().setPacketHandled(true);
    }
    
    /**
     * Extract a specific slot type's inventory from Curios data
     */
    private static String getCuriosSlotInventory(String curiosData, String slotType) {
        if (curiosData == null || curiosData.trim().equals("{}") || curiosData.trim().isEmpty()) {
            return null;
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
                        String currentSlotType = keyValue[0].replace("\"", "");
                        if (currentSlotType.equals(slotType)) {
                            return keyValue[1]; // Return the serialized inventory data for this slot type
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse Curios data for slot type '{}': {}", slotType, e.getMessage());
        }
        
        return null;
    }
} 