package com.eliteinventorybackups.database;

import com.eliteinventorybackups.EliteInventoryBackups;
import com.eliteinventorybackups.config.ModConfig;
import com.eliteinventorybackups.model.BackupEntry;
import com.eliteinventorybackups.model.BackupSummary;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class DatabaseManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String H2_DB_SUBFOLDER = "eliteinventorybackups" + File.separator + "data";
    private static final String H2_DB_NAME = "inventorybackups";
    private String jdbcUrl;
    private String username;
    private String password;

    public DatabaseManager() {
        ModConfig.DatabaseType dbType = ModConfig.SERVER.databaseType.get();
        LOGGER.info("Initializing DatabaseManager with type: {}", dbType);

        if (dbType == ModConfig.DatabaseType.MYSQL) {
            String host = ModConfig.SERVER.mysqlHost.get();
            int port = ModConfig.SERVER.mysqlPort.get();
            String database = ModConfig.SERVER.mysqlDatabase.get();
            this.username = ModConfig.SERVER.mysqlUsername.get();
            this.password = ModConfig.SERVER.mysqlPassword.get();
            boolean useSSL = ModConfig.SERVER.mysqlUseSSL.get();
            String extraParams = ModConfig.SERVER.mysqlExtraParams.get();

            // Automatically create the database if it doesn't exist
            createDatabaseIfNeeded(host, port, database, this.username, this.password, useSSL);

            this.jdbcUrl = buildMysqlUrl(host, port, database, useSSL, extraParams);
            LOGGER.info("Attempting to connect to MySQL with URL: {}", this.jdbcUrl);
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                LOGGER.error("MySQL JDBC driver not found.", e);
            }
        } else {
            try {
                File dbDir = new File("." + File.separator + "config" + File.separator + H2_DB_SUBFOLDER);
                if (!dbDir.exists()) {
                    if (!dbDir.mkdirs()) {
                        LOGGER.error("Could not create H2 database directory: {}", dbDir.getAbsolutePath());
                        this.jdbcUrl = "jdbc:h2:mem:" + H2_DB_NAME + "_fallback";
                    } else {
                        this.jdbcUrl = "jdbc:h2:" + dbDir.getAbsolutePath() + File.separator + H2_DB_NAME + ";DB_CLOSE_DELAY=-1";
                    }
                } else {
                    this.jdbcUrl = "jdbc:h2:" + dbDir.getAbsolutePath() + File.separator + H2_DB_NAME + ";DB_CLOSE_DELAY=-1";
                }
                LOGGER.info("Configured to use H2 database: {}", this.jdbcUrl);
            } catch (Exception e) {
                LOGGER.error("Failed to set up H2 database path an_error_occurred", e);
                this.jdbcUrl = "jdbc:h2:mem:" + H2_DB_NAME + "_errorfallback";
            }
        }
        initializeDatabase();
    }

    private Connection getConnection() throws SQLException {
        try {
            if (ModConfig.SERVER.databaseType.get() == ModConfig.DatabaseType.MYSQL) {
                return DriverManager.getConnection(jdbcUrl, username, password);
            } else {
                return DriverManager.getConnection(jdbcUrl);
            }
        } catch (SQLException e) {
            // Check for the specific timezone error and provide a user-friendly message.
            Throwable cause = e.getCause();
            while (cause != null) {
                if (cause instanceof java.time.zone.ZoneRulesException) {
                    LOGGER.error("############################################################");
                    LOGGER.error("###           INVALID DATABASE TIMEZONE                  ###");
                    LOGGER.error("############################################################");
                    LOGGER.error("The 'mysqlExtraParams' in your config has an invalid timezone ID.");
                    LOGGER.error("You have: '{}'", ModConfig.SERVER.mysqlExtraParams.get());
                    LOGGER.error("Replace the timezone (e.g., 'PST') with a valid IANA ID like 'America/Los_Angeles', 'Europe/London', or simply 'UTC'.");
                    LOGGER.error("The mod cannot connect to the database until this is fixed.");
                    LOGGER.error("############################################################");
                    break; // Exit the loop once the specific cause is found.
                }
                cause = cause.getCause();
            }
            // Re-throw the original exception to let the calling method handle it.
            throw e;
        }
    }

    private void initializeDatabase() {
        ModConfig.DatabaseType dbType = ModConfig.SERVER.databaseType.get();
        String createTableSql;
        
        if (dbType == ModConfig.DatabaseType.MYSQL) {
            createTableSql = """
            CREATE TABLE IF NOT EXISTS player_backups (
                id INT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(255),
                backup_number INT NOT NULL,
                timestamp BIGINT NOT NULL,
                event_type VARCHAR(50),
                world VARCHAR(255),
                pos_x DOUBLE,
                pos_y DOUBLE,
                pos_z DOUBLE,
                experience_level INT,
                experience_progress FLOAT,
                inventory_main LONGTEXT,
                inventory_armor LONGTEXT,
                inventory_offhand LONGTEXT,
                inventory_enderchest LONGTEXT,
                cause_of_death TEXT,
                inventory_curios LONGTEXT,
                player_nbt LONGTEXT,
                modded_inventories LONGTEXT,
                INDEX idx_player_uuid (player_uuid),
                INDEX idx_timestamp (timestamp),
                INDEX idx_player_backup (player_uuid, backup_number),
                UNIQUE KEY unique_player_backup (player_uuid, backup_number)
            );
            """;
        } else {
            // H2 Database syntax
            createTableSql = """
            CREATE TABLE IF NOT EXISTS player_backups (
                id INT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(255),
                backup_number INT NOT NULL,
                timestamp BIGINT NOT NULL,
                event_type VARCHAR(50),
                world VARCHAR(255),
                pos_x DOUBLE,
                pos_y DOUBLE,
                pos_z DOUBLE,
                experience_level INT,
                experience_progress FLOAT,
                inventory_main TEXT,
                inventory_armor TEXT,
                inventory_offhand TEXT,
                inventory_enderchest TEXT,
                cause_of_death TEXT,
                inventory_curios TEXT,
                player_nbt TEXT,
                modded_inventories TEXT
            );
            """;
        }

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            
            // Handle migration for existing installations - add backup_number column if it doesn't exist
            try {
                if (dbType == ModConfig.DatabaseType.MYSQL) {
                    stmt.execute("ALTER TABLE player_backups ADD COLUMN backup_number INT NOT NULL DEFAULT 0");
                    LOGGER.info("Added backup_number column to existing MySQL table.");
                } else {
                    stmt.execute("ALTER TABLE player_backups ADD COLUMN backup_number INT NOT NULL DEFAULT 0");
                    LOGGER.info("Added backup_number column to existing H2 table.");
                }
            } catch (SQLException e) {
                // Column probably already exists, this is fine
                LOGGER.debug("backup_number column already exists or migration not needed: {}", e.getMessage());
            }
            
            // Create indexes separately for H2
            if (dbType != ModConfig.DatabaseType.MYSQL) {
                try {
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_uuid ON player_backups (player_uuid);");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_timestamp ON player_backups (timestamp);");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_backup ON player_backups (player_uuid, backup_number);");
                    stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS unique_player_backup ON player_backups (player_uuid, backup_number);");
                } catch (SQLException e) {
                    LOGGER.debug("Index creation failed (may already exist): {}", e.getMessage());
                }
            }
            
            // Migrate existing data - set backup_number for records that have 0
            migrateExistingBackupNumbers();
            
            LOGGER.info("Database table 'player_backups' initialized successfully.");
        } catch (SQLException e) {
            LOGGER.error("Could not initialize database table 'player_backups'. Full error: ", e);
        }
    }

    /**
     * Saves a pre-constructed backup entry to the database.
     * This is the core save method.
     * @param entry The BackupEntry to save.
     */
    public void saveBackup(BackupEntry entry) {
        // Get the next backup number for this player
        int backupNumber = getNextBackupNumber(entry.playerUuid());
        
        String insertSql = """
        INSERT INTO player_backups (
            player_uuid, player_name, backup_number, timestamp, event_type, world, 
            pos_x, pos_y, pos_z, experience_level, experience_progress, 
            inventory_main, inventory_armor, inventory_offhand, inventory_enderchest, 
            cause_of_death, inventory_curios, player_nbt, modded_inventories
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSql)) {

            pstmt.setString(1, entry.playerUuid().toString());
            pstmt.setString(2, entry.playerName());
            pstmt.setInt(3, backupNumber);
            pstmt.setLong(4, entry.timestamp());
            pstmt.setString(5, entry.eventType());
            pstmt.setString(6, entry.world());
            pstmt.setDouble(7, entry.posX());
            pstmt.setDouble(8, entry.posY());
            pstmt.setDouble(9, entry.posZ());
            pstmt.setInt(10, entry.experienceLevel());
            pstmt.setFloat(11, entry.experienceProgress());
            pstmt.setString(12, entry.inventoryMain());
            pstmt.setString(13, entry.inventoryArmor());
            pstmt.setString(14, entry.inventoryOffhand());
            pstmt.setString(15, entry.inventoryEnderChest());
            pstmt.setString(16, entry.causeOfDeath());
            pstmt.setString(17, entry.inventoryCurios());
            pstmt.setString(18, entry.playerNbt());
            pstmt.setString(19, entry.moddedInventories());

            pstmt.executeUpdate();

            // After saving, enforce the backup limit for the player.
            enforceBackupLimit(entry.playerUuid().toString());

            LOGGER.debug("Saved backup #{} for player {} ({}) at {}", backupNumber, entry.playerName(), entry.playerUuid(), entry.timestamp());
        } catch (SQLException e) {
            LOGGER.error("Could not save backup for player {}. Full error: ", entry.playerName(), e);
        }
    }

    /**
     * Gets the next sequential backup number for a player.
     * @param playerUuid The player's UUID
     * @return The next backup number (1-based)
     */
    private int getNextBackupNumber(UUID playerUuid) {
        String sql = "SELECT COALESCE(MAX(backup_number), 0) + 1 FROM player_backups WHERE player_uuid = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, playerUuid.toString());
            try (var rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Could not get next backup number for player {}. Full error: ", playerUuid, e);
        }
        
        return 1; // Fallback to 1 if query fails
    }

    /**
     * Migrates existing backup data to assign sequential backup numbers.
     */
    private void migrateExistingBackupNumbers() {
        String selectSql = "SELECT player_uuid FROM player_backups WHERE backup_number = 0 GROUP BY player_uuid";
        
        try (Connection conn = getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
            
            try (var rs = selectStmt.executeQuery()) {
                while (rs.next()) {
                    String playerUuid = rs.getString("player_uuid");
                    migrateSinglePlayerBackupNumbers(playerUuid);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Could not migrate existing backup numbers. Full error: ", e);
        }
    }

    /**
     * Migrates backup numbers for a single player.
     */
    private void migrateSinglePlayerBackupNumbers(String playerUuid) {
        String selectPlayerBackupsSql = "SELECT id FROM player_backups WHERE player_uuid = ? AND backup_number = 0 ORDER BY timestamp ASC";
        String updateSql = "UPDATE player_backups SET backup_number = ? WHERE id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectPlayerBackupsSql);
             PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
            
            selectStmt.setString(1, playerUuid);
            try (var rs = selectStmt.executeQuery()) {
                int backupNumber = 1;
                while (rs.next()) {
                    int backupId = rs.getInt("id");
                    updateStmt.setInt(1, backupNumber);
                    updateStmt.setInt(2, backupId);
                    updateStmt.executeUpdate();
                    backupNumber++;
                }
            }
            
            LOGGER.info("Migrated backup numbers for player {}", playerUuid);
        } catch (SQLException e) {
            LOGGER.error("Could not migrate backup numbers for player {}. Full error: ", playerUuid, e);
        }
    }
    
    public List<BackupSummary> getBackupsSummaryForPlayer(UUID playerUuid) {
        List<BackupSummary> summaries = new ArrayList<>();
        String querySql = "SELECT backup_number, timestamp, event_type, world FROM player_backups WHERE player_uuid = ? ORDER BY backup_number DESC";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(querySql)) {
            
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                summaries.add(new BackupSummary(
                    rs.getInt("backup_number"),
                    rs.getLong("timestamp"),
                    rs.getString("event_type"),
                    rs.getString("world")
                ));
            }
        } catch (SQLException e) {
            LOGGER.error("Could not retrieve backup summaries for player UUID {}: {}", playerUuid, e.getMessage(), e);
        }
        return summaries;
    }

    public BackupEntry getBackupByNumber(UUID playerUuid, int backupNumber) {
        String querySql = "SELECT * FROM player_backups WHERE player_uuid = ? AND backup_number = ?";
        BackupEntry entry = null;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(querySql)) {
            
            pstmt.setString(1, playerUuid.toString());
            pstmt.setInt(2, backupNumber);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                entry = new BackupEntry(
                    rs.getInt("id"),
                    UUID.fromString(rs.getString("player_uuid")),
                    rs.getString("player_name"),
                    rs.getLong("timestamp"),
                    rs.getString("event_type"),
                    rs.getString("world"),
                    rs.getDouble("pos_x"),
                    rs.getDouble("pos_y"),
                    rs.getDouble("pos_z"),
                    rs.getInt("experience_level"),
                    rs.getFloat("experience_progress"),
                    rs.getString("inventory_main"),
                    rs.getString("inventory_armor"),
                    rs.getString("inventory_offhand"),
                    rs.getString("inventory_enderchest"),
                    rs.getString("cause_of_death"),
                    rs.getString("inventory_curios"),
                    rs.getString("player_nbt"),
                    rs.getString("modded_inventories")
                );
            }
        } catch (SQLException e) {
            LOGGER.error("Could not retrieve backup #{} for player {}: {}", backupNumber, playerUuid, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Could not parse UUID for backup #{} for player {}: {}", backupNumber, playerUuid, e.getMessage(), e);
        }
        return entry;
    }

    // Keep the old method for backward compatibility but mark it as deprecated
    @Deprecated
    public BackupEntry getBackupById(int backupId) {
        String querySql = "SELECT * FROM player_backups WHERE id = ?";
        BackupEntry entry = null;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(querySql)) {
            
            pstmt.setInt(1, backupId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                entry = new BackupEntry(
                    rs.getInt("id"),
                    UUID.fromString(rs.getString("player_uuid")),
                    rs.getString("player_name"),
                    rs.getLong("timestamp"),
                    rs.getString("event_type"),
                    rs.getString("world"),
                    rs.getDouble("pos_x"),
                    rs.getDouble("pos_y"),
                    rs.getDouble("pos_z"),
                    rs.getInt("experience_level"),
                    rs.getFloat("experience_progress"),
                    rs.getString("inventory_main"),
                    rs.getString("inventory_armor"),
                    rs.getString("inventory_offhand"),
                    rs.getString("inventory_enderchest"),
                    rs.getString("cause_of_death"),
                    rs.getString("inventory_curios"),
                    rs.getString("player_nbt"),
                    rs.getString("modded_inventories")
                );
            }
        } catch (SQLException e) {
            LOGGER.error("Could not retrieve backup with ID {}: {}", backupId, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Could not parse UUID for backup ID {}: {}", backupId, e.getMessage(), e);
        }
        return entry;
    }

    public void shutdown() {
        LOGGER.info("DatabaseManager shutting down. If using a connection pool, it would be closed here.");
        // For basic DriverManager usage, individual connections are closed via try-with-resources.
        // If a connection pool (e.g., HikariCP) were implemented, this is where pool.close() would go.
    }

    // TODO: Method for serializing/deserializing inventory (ItemStack lists) to/from String (JSON or NBT string)

    /**
     * Connects to the MySQL server and creates the specified database if it doesn't already exist.
     */
    private void createDatabaseIfNeeded(String host, int port, String dbName, String user, String pass, boolean useSSL) {
        // JDBC URL without a specific database. We connect to the server itself.
        String tempUrl = String.format("jdbc:mysql://%s:%d?allowPublicKeyRetrieval=true&useSSL=%b&connectTimeout=5000", host, port, useSSL);
        
        LOGGER.info("Verifying database '{}' exists...", dbName);

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            LOGGER.error("MySQL JDBC driver not found, cannot create database.", e);
            return; // Cannot proceed
        }

        try (Connection conn = DriverManager.getConnection(tempUrl, user, pass);
             Statement stmt = conn.createStatement()) {
            
            // Use backticks to safely handle db names that might be reserved words
            String createDbSql = "CREATE DATABASE IF NOT EXISTS `" + dbName + "`";
            stmt.executeUpdate(createDbSql);
            LOGGER.info("Database '{}' is ready.", dbName);

        } catch (SQLException e) {
            LOGGER.error("Failed to automatically create MySQL database '{}'.", dbName, e);
            LOGGER.error("Please check your MySQL connection settings and ensure the user '{}' has 'CREATE' privileges.", user);
        }
    }

    /**
     * Builds the MySQL JDBC URL with all necessary parameters.
     * @return The full JDBC URL string.
     */
    private String buildMysqlUrl(String host, int port, String database, boolean useSSL, String extraParams) {
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(database);

        // Build parameters
        List<String> params = new ArrayList<>();
        params.add("useSSL=" + useSSL);
        params.add("allowPublicKeyRetrieval=true"); // Required for modern MySQL/MariaDB
        params.add("connectTimeout=5000"); // 5-second connection timeout

        if (extraParams != null && !extraParams.trim().isEmpty()) {
            params.add(extraParams);
        }

        urlBuilder.append("?").append(String.join("&", params));
        return urlBuilder.toString();
    }

    /**
     * Checks if a player has more backups than the configured limit and deletes the oldest if necessary.
     * @param playerUuid The UUID of the player to check.
     */
    private void enforceBackupLimit(String playerUuid) {
        int maxBackups = ModConfig.SERVER.maxBackupsPerPlayer.get();
        if (maxBackups <= 0) {
            return; // 0 or less means unlimited, so we do nothing.
        }

        String countSql = "SELECT COUNT(*) FROM player_backups WHERE player_uuid = ?";
        String findOldestSql = "SELECT id FROM player_backups WHERE player_uuid = ? ORDER BY timestamp ASC LIMIT 1";
        String deleteSql = "DELETE FROM player_backups WHERE id = ?";

        try (Connection conn = getConnection()) {
            int currentBackups = 0;
            
            // Step 1: Count current backups
            try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
                countStmt.setString(1, playerUuid);
                try (var rs = countStmt.executeQuery()) {
                    if (rs.next()) {
                        currentBackups = rs.getInt(1);
                    }
                }
            }

            // Step 2: If over limit, find and delete the oldest backup
            if (currentBackups > maxBackups) {
                LOGGER.info("Player {} has {} backups, exceeding the limit of {}. Deleting oldest backup.", playerUuid, currentBackups, maxBackups);
                
                int oldestBackupId = -1;
                try (PreparedStatement findStmt = conn.prepareStatement(findOldestSql)) {
                    findStmt.setString(1, playerUuid);
                    try (var rs = findStmt.executeQuery()) {
                        if (rs.next()) {
                            oldestBackupId = rs.getInt(1);
                        }
                    }
                }
                
                if (oldestBackupId > 0) {
                    try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                        deleteStmt.setInt(1, oldestBackupId);
                        int rowsAffected = deleteStmt.executeUpdate();
                        if (rowsAffected > 0) {
                            LOGGER.info("Successfully deleted oldest backup (ID: {}) for player {}.", oldestBackupId, playerUuid);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Could not enforce backup limit for player {}. Full error: ", playerUuid, e);
        }
    }

    /**
     * Removes all backups for a specific player.
     * @param playerUuid The UUID of the player whose backups should be removed.
     * @return The number of backups that were deleted.
     */
    public int removeAllBackupsForPlayer(UUID playerUuid) {
        String deleteSql = "DELETE FROM player_backups WHERE player_uuid = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
            
            pstmt.setString(1, playerUuid.toString());
            int deletedCount = pstmt.executeUpdate();
            
            LOGGER.info("Removed {} backup(s) for player {}", deletedCount, playerUuid);
            return deletedCount;
            
        } catch (SQLException e) {
            LOGGER.error("Could not remove backups for player {}. Full error: ", playerUuid, e);
            return 0;
        }
    }
} 