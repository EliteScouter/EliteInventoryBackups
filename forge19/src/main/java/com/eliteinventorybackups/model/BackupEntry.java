package com.eliteinventorybackups.model;

import java.util.UUID;

public record BackupEntry(
    int id, // Added backup ID from the database
    UUID playerUuid,
    String playerName,
    long timestamp,
    String eventType, // e.g., "login", "logout", "death", "manual"
    String world,
    double posX,
    double posY,
    double posZ,
    int experienceLevel,
    float experienceProgress,
    String inventoryMain, // Serialized main inventory
    String inventoryArmor, // Serialized armor inventory
    String inventoryOffhand, // Serialized offhand inventory
    String inventoryEnderChest, // Serialized Ender Chest inventory
    String causeOfDeath, // Nullable, only for death events
    String inventoryCurios, // Nullable, for Curios/Baubles API if integrated
    String playerNbt, // Nullable, generic NBT backup as fallback
    String moddedInventories // Nullable, JSON of modded inventory data
) {} 