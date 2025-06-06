package com.strictgaming.elite.holograms.api.exception;

/**
 * Exception thrown when there is an issue with hologram operations
 */
public class HologramException extends RuntimeException {

    public HologramException(String message) {
        super(message);
    }

    public HologramException(String message, Throwable cause) {
        super(message, cause);
    }

    public HologramException(Throwable cause) {
        super(cause);
    }
} 