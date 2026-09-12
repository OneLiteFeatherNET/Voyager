package net.elytrarace.voyager.platform.catalog;

import net.elytrarace.voyager.api.race.MapCatalog;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.exception.DuplicateCatalogEntryException;
import net.elytrarace.voyager.platform.catalog.exception.MalformedCatalogFileException;
import net.elytrarace.voyager.platform.catalog.exception.UnreadableCatalogException;

import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The racecourses on disk, one {@code .json} file per map, read once when this object is built.
 *
 * <p><strong>Eagerly, and that is the decision.</strong> The alternative — parse a file the first
 * time somebody asks for that map — turns every way a map file can be wrong into an empty
 * {@code Optional} arriving in the middle of a cup, minutes or days after the server came up. There
 * is no good place to handle that: a rotation cannot skip a map without becoming a different
 * rotation, and there is nobody at a console to read the log. Read at construction, a malformed map
 * stops the server at boot with the file named in the message. It is the same argument
 * {@code UnknownWorldException} already makes one layer down, where a world name nothing is behind
 * has to be a refusal rather than an empty racetrack.
 *
 * <p>The cost is a few milliseconds of boot per map and the whole catalogue in memory. A
 * {@code MapDefinition} is a name, a world, a spawn and a list of rings — thirty-five of them on the
 * largest map in the repository — so the entire catalogue is smaller than one chunk of the world it
 * describes.
 *
 * <p>The key is the {@code name} inside the file, not the filename. A definition that cannot be
 * found under the name it declares would be a catalogue that disagrees with the cup that names it,
 * and {@link CatalogConsistency} checks exactly that agreement.
 */
public final class JsonMapCatalog implements MapCatalog {

    private final Map<String, MapDefinition> maps;

    /**
     * Reads every map definition in a directory.
     *
     * @param directory the directory holding one {@code .json} file per map
     * @throws UnreadableCatalogException     if the directory is absent, unreadable, or empty
     * @throws MalformedCatalogFileException  if any file is not a valid map definition
     * @throws DuplicateCatalogEntryException if two files declare the same map name
     */
    public JsonMapCatalog(Path directory) {
        this.maps = CatalogDirectory.readAll(directory, "map", MapDefinition.class, MapDefinition::name);
    }

    @Override
    public Optional<MapDefinition> byName(String name) {
        return Optional.ofNullable(maps.get(name));
    }

    /**
     * Every map name this catalogue knows.
     *
     * <p>{@link MapCatalog} itself deliberately has no such method — a consumer asks for the map it
     * is about to play, never for all of them. This exists for {@link CatalogConsistency}, which is
     * the one caller that has to check something about the whole set.
     *
     * @return the names, in the order the files were read
     */
    public @Unmodifiable Set<String> mapNames() {
        return maps.keySet();
    }
}
