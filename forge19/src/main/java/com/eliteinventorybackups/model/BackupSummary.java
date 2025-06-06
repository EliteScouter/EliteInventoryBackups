package com.eliteinventorybackups.model;

// Simple record to hold summary data for listing backups
public record BackupSummary(
    int id,
    long timestamp,
    String eventType,
    String world // Optional: maybe useful in summary
) {} 