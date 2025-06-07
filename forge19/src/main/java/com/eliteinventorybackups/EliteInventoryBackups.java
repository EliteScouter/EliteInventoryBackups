package com.eliteinventorybackups;

import com.eliteinventorybackups.config.ModConfig;
import com.eliteinventorybackups.database.DatabaseManager;
import com.eliteinventorybackups.commands.ViewCommand;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;

import org.slf4j.Logger;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod(EliteInventoryBackups.MODID)
public class EliteInventoryBackups {
    public static final String MODID = "eliteinventorybackups";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static DatabaseManager databaseManager;

    public EliteInventoryBackups() {
        LOGGER.info("Elite Inventory Backups is loading!");

        // Register config in custom location
        ModLoadingContext.get().registerConfig(Type.COMMON, ModConfig.SERVER_SPEC, "eliteinventorybackups/config.toml");

        // DatabaseManager will be initialized when server starts and config is loaded
        
        // No packet registration needed - server-side only
        
        // Register server lifecycle events like ServerStoppingEvent on the FORGE event bus
        MinecraftForge.EVENT_BUS.register(this);
        
        // Event registration for PlayerEventHandler and CommandRegistry is handled by @Mod.EventBusSubscriber
    }



    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Server starting, initializing DatabaseManager with config...");
        // Config is now loaded, safe to initialize DatabaseManager
        if (databaseManager == null) {
            databaseManager = new DatabaseManager();
            LOGGER.info("DatabaseManager initialized successfully.");
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Elite Inventory Backups shutting down...");
        
        try {
            // Clean up ViewCommand resources
            ViewCommand.cleanupAllViewers();
            LOGGER.info("ViewCommand cleanup completed.");
        } catch (Exception e) {
            LOGGER.error("Error during ViewCommand cleanup", e);
        }
        
        try {
            // Shutdown database manager
            if (databaseManager != null) {
                databaseManager.shutdown();
                LOGGER.info("DatabaseManager shutdown completed.");
            }
        } catch (Exception e) {
            LOGGER.error("Error during DatabaseManager shutdown", e);
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