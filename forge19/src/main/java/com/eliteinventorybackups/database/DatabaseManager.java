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

public class DatabaseManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String H2_DB_SUBFOLDER = "eliteinventorybackups" + File.separator + "data";
    private static final String H2_DB_NAME = "inventorybackups";
    private String jdbcUrl;
    private String dbUsername;
    private String dbPassword;

    public DatabaseManager() {
        ModConfig.DatabaseType dbType = ModConfig.SERVER.databaseType.get();
        
        if (dbType == ModConfig.DatabaseType.MYSQL) {
            String host = ModConfig.SERVER.mysqlHost.get();
            int port = ModConfig.SERVER.mysqlPort.get();
            String database = ModConfig.SERVER.mysqlDatabase.get();
            this.dbUsername = ModConfig.SERVER.mysqlUsername.get();
            this.dbPassword = ModConfig.SERVER.mysqlPassword.get();
            boolean useSSL = ModConfig.SERVER.mysqlUseSSL.get();
            String extraParams = ModConfig.SERVER.mysqlExtraParams.get();

            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(database);
            urlBuilder.append("?useSSL=").append(useSSL);
            if (extraParams != null && !extraParams.trim().isEmpty()) {
                if (!extraParams.startsWith("&") && !extraParams.startsWith("?")) {
                    urlBuilder.append("&");
                }
                urlBuilder.append(extraParams);
            }
            this.jdbcUrl = urlBuilder.toString();
            LOGGER.info("Configured to use MySQL database: {}", this.jdbcUrl.substring(0, this.jdbcUrl.indexOf('?') > 0 ? this.jdbcUrl.indexOf('?') : this.jdbcUrl.length()));
             try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                LOGGER.error("MySQL JDBC Driver not found. Please ensure it is in the classpath.", e);
            }
        } else {
            try {
                File dbDir = new File("." + File.separator + "config" + File.separator + H2_DB_SUBFOLDER);
                if (!dbDir.exists()) {
                    if (!dbDir.mkdirs()) {
                        LOGGER.error("Could not create H2 database directory: {}", dbDir.getAbsolutePath());
                        this.jdbcUrl = "jdbc:h2:mem:" + H2_DB_NAME + "_fallback";
                    } else {
                        this.jdbcUrl = "jdbc:h2:" + dbDir.getAbsolutePath() + File.separator + H2_DB_NAME;
                    }
                } else {
                    this.jdbcUrl = "jdbc:h2:" + dbDir.getAbsolutePath() + File.separator + H2_DB_NAME;
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
        if (ModConfig.SERVER.databaseType.get() == ModConfig.DatabaseType.MYSQL) {
            return DriverManager.getConnection(jdbcUrl, dbUsername, dbPassword);
        } else {
            return DriverManager.getConnection(jdbcUrl);
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
                INDEX idx_timestamp (timestamp)
            );
            """;
        } else {
            // H2 Database syntax
            createTableSql = """
            CREATE TABLE IF NOT EXISTS player_backups (
                id INT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(255),
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
            
            // Create indexes separately for H2
            if (dbType != ModConfig.DatabaseType.MYSQL) {
                try {
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_uuid ON player_backups (player_uuid);");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_timestamp ON player_backups (timestamp);");
                } catch (SQLException e) {
                    LOGGER.debug("Index creation failed (may already exist): {}", e.getMessage());
                }
            }
            
            LOGGER.info("Database table 'player_backups' initialized successfully.");
        } catch (SQLException e) {
            LOGGER.error("Could not initialize database table 'player_backups' an_error_occurred", e);
        }
    }

    public void saveBackup(BackupEntry entry) {
        String insertSql = """
        INSERT INTO player_backups (
            player_uuid, player_name, timestamp, event_type, world, 
            pos_x, pos_y, pos_z, experience_level, experience_progress, 
            inventory_main, inventory_armor, inventory_offhand, inventory_enderchest, 
            cause_of_death, inventory_curios, player_nbt, modded_inventories
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSql)) {

            pstmt.setString(1, entry.playerUuid().toString());
            pstmt.setString(2, entry.playerName());
            pstmt.setLong(3, entry.timestamp());
            pstmt.setString(4, entry.eventType());
            pstmt.setString(5, entry.world());
            pstmt.setDouble(6, entry.posX());
            pstmt.setDouble(7, entry.posY());
            pstmt.setDouble(8, entry.posZ());
            pstmt.setInt(9, entry.experienceLevel());
            pstmt.setFloat(10, entry.experienceProgress());
            pstmt.setString(11, entry.inventoryMain());
            pstmt.setString(12, entry.inventoryArmor());
            pstmt.setString(13, entry.inventoryOffhand());
            pstmt.setString(14, entry.inventoryEnderChest());
            pstmt.setString(15, entry.causeOfDeath());
            pstmt.setString(16, entry.inventoryCurios());
            pstmt.setString(17, entry.playerNbt());
            pstmt.setString(18, entry.moddedInventories());

            pstmt.executeUpdate();
            LOGGER.debug("Saved backup for player {} ({}) at {}", entry.playerName(), entry.playerUuid(), entry.timestamp());
        } catch (SQLException e) {
            LOGGER.error("Could not save backup for player {} an_error_occurred", entry.playerName(), e);
        }
    }
    
    public List<BackupSummary> getBackupsSummaryForPlayer(UUID playerUuid) {
        List<BackupSummary> summaries = new ArrayList<>();
        String querySql = "SELECT id, timestamp, event_type, world FROM player_backups WHERE player_uuid = ? ORDER BY timestamp DESC";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(querySql)) {
            
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                summaries.add(new BackupSummary(
                    rs.getInt("id"),
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
} 