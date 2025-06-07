package com.eliteinventorybackups;

import com.eliteinventorybackups.commands.ViewCommand;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = EliteInventoryBackups.MODID)
public class ViewerInventoryHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Note: We'll implement navigation through a custom container menu instead of events
    // The current approach with PlayerContainerEvent.Pick doesn't exist in this Forge version
    
    @SubscribeEvent
    public static void onInventoryClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        // Clean up viewer data when inventory is closed
        ViewCommand.cleanupViewer(player.getUUID());
    }
    
    @SubscribeEvent 
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Clean up viewer data when player logs out
            ViewCommand.cleanupViewer(player.getUUID());
        }
    }
} 