package com.strictgaming.elite.holograms.api.manager.database;

import com.strictgaming.elite.holograms.api.hologram.Hologram;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 *
 * Handles the saving and loading of all databases for the server from the storage
 *
 */
public interface HologramSaver {

    /**
     *
     * Loads all holograms
     *
     * @return The holograms
     * @throws IOException If there was an issue loading the data
     */
    Map<String, Hologram> load() throws IOException;

    /**
     *
     * Saves all holograms
     *
     * @param holograms Cached holograms
     */
    void save(List<Hologram> holograms);

} 