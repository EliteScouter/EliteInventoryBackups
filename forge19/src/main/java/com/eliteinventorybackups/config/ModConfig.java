package com.eliteinventorybackups.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ModConfig {
    public static class Server {
        public final ForgeConfigSpec.EnumValue<DatabaseType> databaseType;
        public final ForgeConfigSpec.ConfigValue<String> mysqlHost;
        public final ForgeConfigSpec.IntValue mysqlPort;
        public final ForgeConfigSpec.ConfigValue<String> mysqlDatabase;
        public final ForgeConfigSpec.ConfigValue<String> mysqlUsername;
        public final ForgeConfigSpec.ConfigValue<String> mysqlPassword;
        public final ForgeConfigSpec.BooleanValue mysqlUseSSL;
        public final ForgeConfigSpec.ConfigValue<String> mysqlExtraParams;

        // Event Snapshot Settings
        public final ForgeConfigSpec.BooleanValue enableDeathSnapshots;
        public final ForgeConfigSpec.BooleanValue enableLoginSnapshots;
        public final ForgeConfigSpec.BooleanValue enableLogoutSnapshots;

        // Mod Integration Settings
        public final ForgeConfigSpec.BooleanValue enableCuriosBackup;
        public final ForgeConfigSpec.BooleanValue enableGenericNbtBackup;
        public final ForgeConfigSpec.BooleanValue enableSophisticatedBackpacksBackup;
        public final ForgeConfigSpec.BooleanValue enableIronBackpacksBackup;
        public final ForgeConfigSpec.BooleanValue enableColytraBackup;
        public final ForgeConfigSpec.BooleanValue autoDetectModdedInventories;

        // Backup Retention Settings
        public final ForgeConfigSpec.IntValue maxBackupsPerPlayer;

        Server(ForgeConfigSpec.Builder builder) {
            builder.comment("Database settings for Elite Inventory Backups").push("database");

            databaseType = builder
                .comment("Type of database to use. H2 is local file-based, MYSQL requires a separate MySQL/MariaDB server.")
                .defineEnum("databaseType", DatabaseType.H2);

            builder.push("mysql");
            mysqlHost = builder
                .comment("Hostname or IP address of the MySQL server.")
                .define("mysqlHost", "localhost");
            mysqlPort = builder
                .comment("Port number for the MySQL server.")
                .defineInRange("mysqlPort", 3306, 1, 65535);
            mysqlDatabase = builder
                .comment("Name of the database to use on the MySQL server.")
                .define("mysqlDatabase", "elite_inventory_backups");
            mysqlUsername = builder
                .comment("Username for connecting to MySQL.")
                .define("mysqlUsername", "eib_user");
            mysqlPassword = builder
                .comment("Password for connecting to MySQL.")
                .define("mysqlPassword", "password");
            mysqlUseSSL = builder
                .comment("Use SSL for MySQL connection? (true/false)")
                .define("mysqlUseSSL", false);
            mysqlExtraParams = builder
                .comment("Extra parameters for the MySQL JDBC URL (e.g., serverTimezone=UTC&autoReconnect=true)")
                .define("mysqlExtraParams", "serverTimezone=UTC");
            builder.pop(); // mysql

            builder.pop(); // database

            builder.comment("Backup retention settings").push("retention");

            maxBackupsPerPlayer = builder
                .comment("Maximum number of backups to keep per player. Set to 0 for unlimited.")
                .defineInRange("maxBackupsPerPlayer", 24, 0, Integer.MAX_VALUE);

            builder.pop(); // retention

            builder.comment("Event snapshot settings for controlling when backups are created").push("event_snapshots");

            enableDeathSnapshots = builder
                .comment("Enable automatic inventory backups when a player dies.")
                .define("enableDeathSnapshots", true);

            enableLoginSnapshots = builder
                .comment("Enable automatic inventory backups when a player logs in.")
                .define("enableLoginSnapshots", true);

            enableLogoutSnapshots = builder
                .comment("Enable automatic inventory backups when a player logs out.")
                .define("enableLogoutSnapshots", true);

            builder.pop(); // event_snapshots

            builder.comment("Mod integration settings for backing up modded inventories").push("mod_integrations");

            enableCuriosBackup = builder
                .comment("Enable backup of Curios items (rings, amulets, etc.). Requires Curios mod.")
                .define("enableCuriosBackup", true);

            enableGenericNbtBackup = builder
                .comment("Enable generic NBT backup as fallback for unsupported mods. Captures full player data.")
                .define("enableGenericNbtBackup", true);

            enableSophisticatedBackpacksBackup = builder
                .comment("Enable backup of Sophisticated Backpacks contents. Requires Sophisticated Backpacks mod.")
                .define("enableSophisticatedBackpacksBackup", true);

            enableIronBackpacksBackup = builder
                .comment("Enable backup of Iron Backpacks contents. Requires Iron Backpacks mod.")
                .define("enableIronBackpacksBackup", true);

            enableColytraBackup = builder
                .comment("Enable backup of Colytra items. Requires Colytra mod.")
                .define("enableColytraBackup", true);

            autoDetectModdedInventories = builder
                .comment("Automatically detect and backup modded inventories when possible.")
                .define("autoDetectModdedInventories", true);

            builder.pop(); // mod_integrations
        }
    }

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        final Pair<Server, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();
    }

    public enum DatabaseType {
        H2, MYSQL
    }
} 