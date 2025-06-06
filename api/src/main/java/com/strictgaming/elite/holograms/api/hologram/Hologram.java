package com.strictgaming.elite.holograms.api.hologram;

import com.strictgaming.elite.holograms.api.exception.HologramException;

/**
 *
 * An interface representing a server hologram
 *
 */
public interface Hologram {

    /**
     * Gets the ID of the hologram
     *
     * @return The hologram ID
     */
    String getId();

    /**
     * Moves the hologram to the given position
     * 
     * @param x The x coordinate
     * @param y The y coordinate
     * @param z The z coordinate
     */
    void move(double x, double y, double z);

    /**
     * Sets the line at the given index to the given text
     *
     * @param index The line to set
     * @param text The text to set the line to
     */
    void setLine(int index, String text);

    /**
     * Inserts the new line at the specified line number
     *
     * @param lineNum The line number to insert at
     * @param line The line to insert
     */
    void insertLine(int lineNum, String line);

    /**
     * Adds a line to the hologram with the given text
     *
     * @param line The new line for the hologram
     */
    void addLine(String line);

    /**
     * Adds the given lines to the hologram
     *
     * @param lines The lines to add
     */
    void addLines(String... lines);

    /**
     * Removes a line from the hologram
     *
     * @param index The index of the line to remove
     */
    void removeLine(int index);

    /**
     * Deletes the hologram from the world
     */
    void delete();

    /**
     * Teleports the hologram to the new world and position
     *
     * @param worldName The name of the world
     * @param x The x coordinate
     * @param y The y coordinate
     * @param z The z coordinate
     */
    void teleport(String worldName, double x, double y, double z);

    /**
     * Copies the hologram to a new ID
     *
     * @param id The new ID
     * @return The new hologram instance
     */
    Hologram copy(String id);

    /**
     * Despawns the hologram for all players
     */
    void despawn();
    
    /**
     * Sets the range at which players can see the hologram
     * 
     * @param range The range in blocks
     */
    void setRange(int range);
    
    /**
     * Gets the location of the hologram
     * 
     * @return Array containing [x, y, z] coordinates
     */
    double[] getLocation();
    
    /**
     * Gets the name of the world the hologram is in
     * 
     * @return The world name
     */
    String getWorldName();
} 