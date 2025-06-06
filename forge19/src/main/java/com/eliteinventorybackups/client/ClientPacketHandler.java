package com.eliteinventorybackups.client;

import com.eliteinventorybackups.client.gui.BackupViewerScreen;
import com.eliteinventorybackups.network.OpenBackupViewerGUIPacket;
import net.minecraft.client.Minecraft;

public class ClientPacketHandler {

    public static void handleOpenBackupViewerScreen(OpenBackupViewerGUIPacket packet) {
        // This method is called on the client side to open the backup viewer GUI
        Minecraft.getInstance().setScreen(new BackupViewerScreen(packet));
    }
} 