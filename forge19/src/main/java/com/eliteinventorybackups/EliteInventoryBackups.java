package com.eliteinventorybackups;

import com.eliteinventorybackups.config.ModConfig;
import com.eliteinventorybackups.database.DatabaseManager;
import com.eliteinventorybackups.network.PacketHandler;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod(EliteInventoryBackups.MODID)
public class EliteInventoryBackups {
    public static final String MODID = "eliteinventorybackups";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static DatabaseManager databaseManager;

    public EliteInventoryBackups() {
        LOGGER.info("Elite Inventory Backups is loading!");

        // Register config
        ModLoadingContext.get().registerConfig(Type.SERVER, ModConfig.SERVER_SPEC);

        // Initialize DatabaseManager *after* config is loaded, or make it config-aware
        // For now, we'll make DatabaseManager read from config upon instantiation.
        databaseManager = new DatabaseManager();
        
        // Register common setup method for tasks like packet registration
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        
        // Register server lifecycle events like ServerStoppingEvent on the FORGE event bus
        MinecraftForge.EVENT_BUS.register(this);
        
        // Event registration for PlayerEventHandler and CommandRegistry is handled by @Mod.EventBusSubscriber
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PacketHandler.register();
            LOGGER.info("Registered packet handlers.");
        });
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Elite Inventory Backups shutting down...");
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        LOGGER.info("Elite Inventory Backups has shut down.");
    }

    public static DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

//    private void setup(final FMLCommonSetupEvent event) {
//        // some preinit code
//        LOGGER.info("HELLO FROM PREINIT");
//        LOGGER.info("DIRT BLOCK >> {}", Blocks.DIRT.getRegistryName());
//    }
} 