package com.eliteinventorybackups.network;

import com.eliteinventorybackups.EliteInventoryBackups;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.server.level.ServerPlayer;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(EliteInventoryBackups.MODID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void register() {
        INSTANCE.registerMessage(id++, OpenBackupViewerGUIPacket.class, 
            OpenBackupViewerGUIPacket::encode, 
            OpenBackupViewerGUIPacket::decode, 
            OpenBackupViewerGUIPacket::handle);
        
        INSTANCE.registerMessage(id++, RequestTakeItemPacket.class, 
            RequestTakeItemPacket::encode, 
            RequestTakeItemPacket::decode, 
            RequestTakeItemPacket::handle);
        
        // Register other packets here
        // INSTANCE.registerMessage(id++, RequestTakeItemPacket.class, ...);
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
} 