package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.race.CupCatalog;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.platform.catalog.exception.DuplicateCatalogEntryException;
import net.elytrarace.voyager.platform.catalog.exception.MalformedCatalogFileException;
import net.elytrarace.voyager.platform.catalog.exception.UnreadableCatalogException;

import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The cups on disk, one {@code .json} file per cup, read once when this object is built.
 *
 * <p>Eager for the same reason as {@link JsonMapCatalog}, and with one more of its own: a cup is the
 * thing that decides what is played next, so a cup file that will not parse is discovered at the
 * worst possible moment if it is discovered lazily — between two maps, with a lobby waiting.
 *
 * <p>This class does not check that the maps a cup names exist. It cannot: it has never been shown a
 * map catalogue. That check is {@link CatalogConsistency}'s, run once at boot where both are in
 * hand.
 */
public final class JsonCupCatalog implements CupCatalog {

    private final Map<String, CupDefinition> cups;

    /**
     * Reads every cup definition in a directory.
     *
     * @param directory the directory holding one {@code .json} file per cup
     * @throws UnreadableCatalogException     if the directory is absent, unreadable, or empty
     * @throws MalformedCatalogFileException  if any file is not a valid cup definition
     * @throws DuplicateCatalogEntryException if two files declare the same cup name
     */
    public JsonCupCatalog(Path directory) {
        this.cups = CatalogDirectory.readAll(directory, "cup", CupDefinition.class, CupDefinition::name);
    }

    @Override
    public Optional<CupDefinition> byName(String name) {
        return Optional.ofNullable(cups.get(name));
    }

    /**
     * Every cup name this catalogue knows.
     *
     * <p>Exists for {@link CatalogConsistency}, which needs to walk every cup's rotation. See the
     * note on {@link JsonMapCatalog#mapNames()} for why the interface itself does not carry this.
     *
     * @return the names, in the order the files were read
     */
    public @Unmodifiable Set<String> cupNames() {
        return cups.keySet();
    }
}
