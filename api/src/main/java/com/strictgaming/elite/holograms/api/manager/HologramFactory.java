package com.strictgaming.elite.holograms.api.manager;

import com.strictgaming.elite.holograms.api.hologram.Hologram;
import com.strictgaming.elite.holograms.api.hologram.HologramBuilder;

import java.util.List;

/**
 *
 * Factory for creating {@link Hologram}s
 *
 */
public interface HologramFactory {

    /**
     *
     * Creates a new {@link HologramBuilder} for creating a new {@link Hologram}
     *
     * @return The builder
     */
    HologramBuilder builder();

    /**
     *
     * Gets a hologram by its ID
     *
     * @param id The ID to look for
     * @return The hologram found, or null
     */
    Hologram getById(String id);

    /**
     *
     * Gets all holograms in the specified radius from the position
     *
     * @param worldName The name of the world to search in
     * @param x The x position to search from
     * @param y The y position to search from
     * @param z The z position to search from
     * @param radius The radius to search in
     * @return The list of holograms found
     */
    List<Hologram> getNearby(String worldName, double x, double y, double z, double radius);

} 