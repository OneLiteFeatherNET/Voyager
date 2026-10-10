package net.elytrarace.voyager.api.race;

import java.util.Optional;

/**
 * Read-only access to known cup definitions, defined from the consumer's need rather than the
 * provider's capability.
 *
 * <p>{@code voyager-race} stays free of Gson and of the filesystem: it is handed a catalog rather
 * than loading one itself. The Gson-backed implementation, with its {@code *Adapter} classes,
 * arrives with the platform and persistence stages; tests supply their own in-memory catalogs.
 */
@FunctionalInterface
public interface CupCatalog {

    /** Returns the cup definition named {@code name}, or empty if no such cup is known. */
    Optional<CupDefinition> byName(String name);
}
