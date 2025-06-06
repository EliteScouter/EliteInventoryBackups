package com.strictgaming.elite.holograms.api.manager;

import com.strictgaming.elite.holograms.api.hologram.HologramBuilder;

import java.io.IOException;

/**
 *
 * Interface representing the platform's main hologram manager
 *
 */
public interface PlatformHologramManager {

    /**
     *
     * Gets the hologram factory for creating and managing holograms
     *
     * @return The factory
     */
    HologramFactory getFactory();

    /**
     *
     * Gets if placeholders are enabled
     *
     * @return True if they are, false otherwise
     */
    boolean arePlaceholdersEnabled();

    /**
     *
     * Reloads all data
     *
     * @throws IOException If there was an issue loading the data
     */
    void reload() throws IOException;

    /**
     *
     * Clears all holograms
     */
    void clear();
    
    /**
     * Creates a new hologram builder
     * 
     * @return The builder
     */
    HologramBuilder builder();
    
    /**
     * Creates a new hologram builder with the specified ID
     * 
     * @param id The ID for the hologram
     * @return The builder
     */
    HologramBuilder builder(String id);
    
    /**
     * Creates a new hologram builder with the specified lines
     * 
     * @param lines The lines for the hologram
     * @return The builder
     */
    HologramBuilder builder(String... lines);
    
    /**
     * Creates a new hologram builder with the specified world and position
     * 
     * @param world The world name
     * @param x The x position
     * @param y The y position
     * @param z The z position
     * @return The builder
     */
    HologramBuilder builder(String world, int x, int y, int z);
} 