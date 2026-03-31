package net.elytrarace.api.phase;

/**
 * Abstraction over platform-specific event registration (Bukkit, Minestom, etc.).
 * Implementations are provided by the platform-specific modules.
 */
public interface EventRegistrar {

    /**
     * Registers an event listener with the platform.
     *
     * @param listener the listener to register
     */
    void registerListener(Object listener);

    /**
     * Unregisters an event listener from the platform.
     *
     * @param listener the listener to unregister
     */
    void unregisterListener(Object listener);
}
