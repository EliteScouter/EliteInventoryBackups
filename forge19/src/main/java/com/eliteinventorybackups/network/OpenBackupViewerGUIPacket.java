package com.eliteinventorybackups.network;

import com.eliteinventorybackups.client.ClientPacketHandler;
import com.eliteinventorybackups.client.gui.BackupViewerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenBackupViewerGUIPacket {

    private final int backupId;
    private final String targetPlayerName;
    private final long timestamp;
    private final String eventType;
    private final String serializedMainInventory;
    private final String serializedArmorInventory;
    private final String serializedOffhandInventory;
    private final String serializedEnderChestInventory;
    private final String serializedCuriosInventory;
    private final String serializedPlayerNbt;
    // Add other relevant details like cause of death, world, coords if needed directly by GUI
    // For now, keeping it to essentials for opening the screen.

    public OpenBackupViewerGUIPacket(int backupId, String targetPlayerName, long timestamp, String eventType,
                                     String serializedMainInventory, String serializedArmorInventory,
                                     String serializedOffhandInventory, String serializedEnderChestInventory,
                                     String serializedCuriosInventory, String serializedPlayerNbt) {
        this.backupId = backupId;
        this.targetPlayerName = targetPlayerName;
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.serializedMainInventory = serializedMainInventory;
        this.serializedArmorInventory = serializedArmorInventory;
        this.serializedOffhandInventory = serializedOffhandInventory;
        this.serializedEnderChestInventory = serializedEnderChestInventory;
        this.serializedCuriosInventory = serializedCuriosInventory;
        this.serializedPlayerNbt = serializedPlayerNbt;
    }

    public static void encode(OpenBackupViewerGUIPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.backupId);
        buf.writeUtf(msg.targetPlayerName);
        buf.writeLong(msg.timestamp);
        buf.writeUtf(msg.eventType);
        buf.writeUtf(msg.serializedMainInventory);
        buf.writeUtf(msg.serializedArmorInventory);
        buf.writeUtf(msg.serializedOffhandInventory);
        buf.writeUtf(msg.serializedEnderChestInventory);
        buf.writeUtf(msg.serializedCuriosInventory != null ? msg.serializedCuriosInventory : "{}");
        buf.writeUtf(msg.serializedPlayerNbt != null ? msg.serializedPlayerNbt : "{}");
    }

    public static OpenBackupViewerGUIPacket decode(FriendlyByteBuf buf) {
        return new OpenBackupViewerGUIPacket(
            buf.readInt(),
            buf.readUtf(),
            buf.readLong(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readUtf()
        );
    }

    public static void handle(OpenBackupViewerGUIPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // This code is executed on the client side
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                //Minecraft.getInstance().setScreen(new BackupViewerScreen(msg)); // Pass the whole packet
                ClientPacketHandler.handleOpenBackupViewerScreen(msg); // Delegate to a client-side handler
            });
        });
        ctx.get().setPacketHandled(true);
    }
    
    // Getters for the GUI to use
    public int getBackupId() { return backupId; }
    public String getTargetPlayerName() { return targetPlayerName; }
    public long getTimestamp() { return timestamp; }
    public String getEventType() { return eventType; }
    public String getSerializedMainInventory() { return serializedMainInventory; }
    public String getSerializedArmorInventory() { return serializedArmorInventory; }
    public String getSerializedOffhandInventory() { return serializedOffhandInventory; }
    public String getSerializedEnderChestInventory() { return serializedEnderChestInventory; }
    public String getSerializedCuriosInventory() { return serializedCuriosInventory; }
    public String getSerializedPlayerNbt() { return serializedPlayerNbt; }
} 