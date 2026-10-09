package net.elytrarace.voyager.platform.catalog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.CupDefinition;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.adapter.CupDefinitionAdapter;
import net.elytrarace.voyager.platform.catalog.adapter.MapDefinitionAdapter;
import net.elytrarace.voyager.platform.catalog.adapter.Vec3Adapter;
import net.elytrarace.voyager.platform.catalog.exception.DuplicateCatalogEntryException;
import net.elytrarace.voyager.platform.catalog.exception.MalformedCatalogFileException;
import net.elytrarace.voyager.platform.catalog.exception.UnreadableCatalogException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Reads every {@code .json} file in a directory into definitions, keyed by the name each one
 * declares, and records each way a directory can be wrong as a {@link CatalogProblem}.
 *
 * <p>Shared by the map and cup halves of {@link CatalogLoader} so that the four ways a directory can
 * be wrong — it is not there, it holds nothing, a file will not parse, two files claim one name — are
 * answered in one place and with one shape of message. Two copies of this would be two chances for one of them to answer a
 * question with an empty {@code Optional} instead.
 *
 * <p>Files are read in sorted order so that a duplicate name names the same two files on every
 * machine. Directory iteration order is not stable across filesystems, and an error message that
 * changes between a developer's laptop and the server is an error message that gets doubted.
 */
final class CatalogDirectory {

    private static final String JSON_SUFFIX = ".json";

    /**
     * One Gson for both halves of the loader, built once. Registering the adapters here rather than inside
     * each catalogue lets {@code MapDefinitionAdapter} ask for a {@code Vec3} through the deserialisation
     * context instead of constructing its own reader. A ring is not registered: it is never a top-level
     * value, and {@code MapDefinitionAdapter} reads each one with its position in the array.
     */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Vec3.class, new Vec3Adapter())
            .registerTypeAdapter(MapDefinition.class, new MapDefinitionAdapter())
            .registerTypeAdapter(CupDefinition.class, new CupDefinitionAdapter())
            .create();

    private CatalogDirectory() {
    }

    /**
     * Reads a whole directory, recording every problem instead of stopping at the first.
     *
     * <p>A missing directory, an unreadable one and an empty one are each one problem, and the
     * definitions it does hold are still read where there are any. A file that will not parse is one
     * problem for that file, and the reading goes on. A name two files both declare keeps the first
     * file's definition and records the second file as the problem.
     *
     * @param directory the directory holding one definition per {@code .json} file
     * @param kind      what these definitions are, for the error messages — {@code "map"} or
     *                  {@code "cup"}
     * @param type      the definition type to parse each file into
     * @param nameOf    how to read a definition's name, which becomes its key
     * @param problems  the list every problem is appended to, in the order it was found
     * @return the definitions that parsed, by name, in filename order
     */
    static <T> Map<String, T> readAll(Path directory, String kind, Class<T> type, Function<T, String> nameOf,
            List<CatalogProblem> problems) {
        Map<String, T> definitions = new LinkedHashMap<>();
        Map<String, Path> sources = new LinkedHashMap<>();

        List<Path> files;
        try {
            files = jsonFilesIn(directory, kind);
        } catch (UnreadableCatalogException exception) {
            problems.add(new CatalogProblem(directory, exception));
            return Collections.unmodifiableMap(definitions);
        }
        for (Path file : files) {
            T definition;
            try {
                definition = parse(file, type);
            } catch (UnreadableCatalogException | MalformedCatalogFileException exception) {
                problems.add(new CatalogProblem(file, exception));
                continue;
            }
            String name = nameOf.apply(definition);
            Path existing = sources.get(name);
            if (existing != null) {
                problems.add(new CatalogProblem(file, new DuplicateCatalogEntryException(kind, name, existing, file)));
                continue;
            }
            sources.put(name, file);
            definitions.put(name, definition);
        }
        return Collections.unmodifiableMap(definitions);
    }

    private static <T> T parse(Path file, Class<T> type) {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UnreadableCatalogException("%s cannot be read".formatted(file), exception);
        }
        try {
            T definition = GSON.fromJson(content, type);
            if (definition == null) {
                throw new JsonParseException("the file is empty");
            }
            return definition;
        } catch (JsonParseException exception) {
            throw new MalformedCatalogFileException(file, exception);
        } catch (RuntimeException exception) {
            // A record's own compact constructor rejecting its arguments arrives here — rings that
            // are not 0..n-1, a normal that is not unit length, a reference time of zero. From the
            // operator's side that is the same event as malformed JSON and wants the same message,
            // with the file in it; without this, it surfaces as an InvalidMapException naming no file
            // at all, which on a directory of thirty maps is not an actionable report.
            throw new MalformedCatalogFileException(file, exception);
        }
    }

    private static List<Path> jsonFilesIn(Path directory, String kind) {
        if (!Files.isDirectory(directory)) {
            throw UnreadableCatalogException.notADirectory(kind, directory);
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> entries = Files.list(directory)) {
            entries.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(JSON_SUFFIX))
                    .sorted()
                    .forEach(files::add);
        } catch (IOException exception) {
            throw new UnreadableCatalogException("%s cannot be listed".formatted(directory), exception);
        }
        if (files.isEmpty()) {
            throw UnreadableCatalogException.empty(kind, directory);
        }
        return files;
    }
}
