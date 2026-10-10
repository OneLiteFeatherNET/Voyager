package net.elytrarace.voyager.platform.world;

import net.elytrarace.voyager.platform.catalog.WorldOpener;
import net.elytrarace.voyager.platform.world.exception.UnknownWorldException;
import net.elytrarace.voyager.platform.world.exception.UnreadableRegionException;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.world.DimensionType;
import net.onelitefeather.falco.anvil.AnvilDiagnostics;
import net.onelitefeather.falco.anvil.FalcoAnvilLoader;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The racetrack worlds, one Minestom instance per world directory, read through Falco.
 *
 * <p><strong>Falco and not Minestom's own {@code AnvilLoader}</strong>, for one reason: Falco throws
 * on a read failure where Minestom's loader reports the chunk as absent. Absence makes the server
 * generate a fresh chunk in its place, and the next save writes that over real map geometry. For a
 * racetrack that is the difference between a loud failure and a silent one.
 *
 * <p><strong>Nothing here ever saves.</strong> {@link #close()} closes the loaders and unregisters
 * the instances and deliberately does not call {@code saveChunksToStorage()}. The race server reads
 * maps and never writes them, so there is no state worth persisting — and a save path is precisely
 * how a bug anywhere else in the rebuild would turn into a corrupted racetrack that no one notices
 * until a player flies into the hole.
 *
 * <p><strong>One instance per world name, cached.</strong> That is a correctness requirement rather
 * than an optimisation: a single world backs more than one map, so two {@code MapDefinition}s naming
 * the same world have to get the same instance and the same loader. Two loaders over one set of
 * region files is a bug.
 *
 * <p><strong>Why the counters below are worth keeping.</strong> Minecraft namespaced the chunk
 * status in 1.20.2, so a chunk written before that stores {@code full} rather than
 * {@code minecraft:full}. Falco 2.x compared the stored status to that literal and reported every
 * older chunk as not fully generated, which means it came back as air: on the shipped world
 * {@code ElytraraceBlueAndRed} that was 3464 of its 9429 chunks — a third of the racetrack, and the
 * only thing that said so was a partial-chunk count nobody was reading. Falco 3.0.0 parses the
 * status as a key instead, so the bare form takes the default namespace and reads, and the same
 * world now loads all 9429 with no unknown block and no error. The defect is fixed upstream; the
 * lesson that a world can look loaded while a third of it is missing is why {@link WorldHealth}
 * exists and why {@link #healthOf(String)} is worth calling after a world is opened.
 *
 * <p>The unknown-block policy stays at Falco's default — an unrecognised block name becomes air —
 * and is made visible rather than fatal. A throwing policy runs inside a chunk load, where the
 * exception is caught by code we do not control, and one removed decorative block would take the
 * whole server down. {@link #healthOf(String)} logs the first time it sees a non-zero count so the
 * hole is reported instead of merely being there.
 */
public final class MapInstances implements AutoCloseable, WorldOpener {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapInstances.class);

    /** {@code r.<regionX>.<regionZ>.mca}, the name Anvil gives each region file. */
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final int CHUNKS_PER_REGION = 32;

    private final InstanceManager instanceManager;
    private final Path worldsRoot;
    private final Map<String, LoadedWorld> loaded = new ConcurrentHashMap<>();

    /**
     * @param instanceManager the manager the instances are registered with and unregistered from
     * @param worldsRoot      the directory the world directories sit in; a world name resolves
     *                        against it to a world root, the directory holding {@code region/} or
     *                        {@code dimensions/}, not to {@code region/} itself
     */
    public MapInstances(InstanceManager instanceManager, Path worldsRoot) {
        this.instanceManager = instanceManager;
        this.worldsRoot = worldsRoot;
    }

    /**
     * The instance a map's world is loaded into, building it on the first call for that name.
     *
     * @param world the world directory's name, as carried by {@code MapDefinition.world()}
     * @return the instance for that world; the same object for every call with the same name
     * @throws UnknownWorldException if no region data sits behind the name
     */
    public Instance forWorld(String world) {
        return load(world).instance();
    }

    /**
     * Falco's raw counters for a world, for a caller that needs more than {@link WorldHealth}
     * reports — the acceptance run reads the per-status and per-version breakdowns from here.
     *
     * @param world the world directory's name
     * @return the diagnostics object the loader for that world writes into
     * @throws UnknownWorldException if no region data sits behind the name
     */
    public AnvilDiagnostics diagnosticsFor(String world) {
        return load(world).diagnostics();
    }

    /**
     * What the loader for a world has actually done so far.
     *
     * <p>Logs a warning the first time it sees an unknown block name for a world. Once, not per
     * call: the count only grows, and a line repeated every time somebody asks is noise rather than
     * a report.
     *
     * @param world the world directory's name
     * @return a snapshot of that world's counters
     * @throws UnknownWorldException if no region data sits behind the name
     */
    public WorldHealth healthOf(String world) {
        LoadedWorld entry = load(world);
        return report(world, entry.diagnostics(), entry.unknownBlocksReported());
    }

    /**
     * Reads every chunk the world's region files cover, so {@link #healthOf(String)} reports what the
     * world really holds rather than what happened to be asked for.
     *
     * <p>A chunk with no data behind it is counted as skipped, not as an error, so the walk over a
     * sparse region file costs little more than the chunks that exist. Used by the configuration check
     * only; the race server loads chunks as players reach them.
     *
     * @param world the world directory's name
     * @throws UnknownWorldException if no region data sits behind the name
     * @throws UnreadableRegionException if a chunk in a region file cannot be read; the message names the file
     */
    public void readEveryChunk(String world) {
        LoadedWorld entry = load(world);
        Path regionDirectory = entry.loader().regionDirectory();
        if (!Files.isDirectory(regionDirectory)) {
            return;
        }
        try (Stream<Path> files = Files.list(regionDirectory)) {
            for (Path file : files.sorted().toList()) {
                Matcher region = REGION_FILE.matcher(file.getFileName().toString());
                if (!region.matches()) {
                    continue;
                }
                int firstChunkX = Integer.parseInt(region.group(1)) * CHUNKS_PER_REGION;
                int firstChunkZ = Integer.parseInt(region.group(2)) * CHUNKS_PER_REGION;
                try {
                    for (int chunkX = firstChunkX; chunkX < firstChunkX + CHUNKS_PER_REGION; chunkX++) {
                        for (int chunkZ = firstChunkZ; chunkZ < firstChunkZ + CHUNKS_PER_REGION; chunkZ++) {
                            entry.instance().loadChunk(chunkX, chunkZ).join();
                        }
                    }
                } catch (CompletionException exception) {
                    throw new UnreadableRegionException(world, file, exception.getCause() == null ? exception : exception.getCause());
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot list the region directory %s".formatted(regionDirectory), exception);
        }
    }

    @Override
    public boolean holdsRegionData(String world) {
        if (loaded.containsKey(world)) {
            return true;
        }
        FalcoAnvilLoader probe = buildLoader(worldsRoot.resolve(world));
        try {
            return WorldFolders.holdsRegionData(probe.regionDirectory());
        } finally {
            closeQuietly(probe, world);
        }
    }

    @Override
    public boolean isOpen(String world) {
        return loaded.containsKey(world);
    }

    @Override
    public void open(String world) {
        load(world);
    }

    /**
     * Unregisters and closes one world. Both steps run even if the first fails, as {@link #close()} does, and
     * a failure is rethrown after both.
     */
    @Override
    public void discard(String world) {
        LoadedWorld entry = loaded.remove(world);
        if (entry == null) {
            return;
        }
        RuntimeException failure = null;
        try {
            instanceManager.unregisterInstance(entry.instance());
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            entry.loader().close();
        } catch (IOException exception) {
            failure = also(failure, new UncheckedIOException(
                    "the loader for world '%s' could not be closed".formatted(world), exception));
        } catch (RuntimeException exception) {
            failure = also(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public boolean regionDataChanged(String world) {
        LoadedWorld entry = loaded.get(world);
        if (entry == null) {
            return false;
        }
        return !regionFingerprint(entry.loader().regionDirectory()).equals(entry.fingerprint());
    }

    /**
     * Closes every loader and unregisters every instance this object created.
     *
     * <p>Does not save. See the class javadoc: the race server reads maps and never writes them.
     */
    @Override
    public void close() {
        RuntimeException failure = null;

        for (Map.Entry<String, LoadedWorld> cached : loaded.entrySet()) {
            String world = cached.getKey();
            LoadedWorld entry = cached.getValue();

            // Both steps keep going past a failure, for the same reason: a shutdown that stops at
            // the first world leaves every loader after it holding its region files open, and the
            // cache uncleared. Unregistering is the live hazard rather than the theoretical one —
            // InstanceManager refuses an instance that still has a player in it, and a shutdown is
            // precisely when one still does.
            try {
                instanceManager.unregisterInstance(entry.instance());
            } catch (RuntimeException exception) {
                failure = also(failure, exception);
            }
            try {
                entry.loader().close();
            } catch (IOException exception) {
                failure = also(failure, new UncheckedIOException(
                        "the loader for world '%s' could not be closed".formatted(world), exception));
            } catch (RuntimeException exception) {
                failure = also(failure, exception);
            }
        }
        loaded.clear();

        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException also(@Nullable RuntimeException first, RuntimeException next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }

    /**
     * Reads a set of Falco counters into a {@link WorldHealth}.
     *
     * <p>Skipped chunks are the two "there was no data" counters and deliberately not Falco's own
     * {@code chunksSkipped()}, which also folds in partially generated chunks. A partial chunk says
     * the world is mid-generation; a missing region file or a missing entry says the data is not
     * there at all, and only the second kind is what a mistyped world name looks like. A racetrack
     * is a finished world, so a partial chunk in one is a different conversation.
     *
     * <p>Refused chunks are carried separately even though the loader also counts each of them as an
     * error, so {@code isSound()} would already be false without this. The redundancy is the point:
     * the verdict then does not depend on Falco continuing to account a version refusal as an error,
     * and {@code describe()} can say which version was refused, which is the only part of that
     * report an operator can act on.
     *
     * <p>Package-private for the test: the difference between the two-term sum and Falco's own
     * total only shows on a world holding a partially generated chunk, which nothing the public
     * surface can reach will produce — a loader only ever reads what is on disk, and the round trip
     * writes fully generated chunks. Feeding a hand-filled {@code AnvilDiagnostics} in here pins the
     * choice without a hand-written region file.
     *
     * <p>The unknown-block warning is raised here rather than by the caller, and raised once per
     * world: the count only grows, so a line repeated on every call is noise rather than a report.
     * The flag the guard flips is the only trace a log line leaves behind that a test can read,
     * which is the second reason this method is reachable from one.
     *
     * @param world                 the world these counters belong to
     * @param diagnostics           the counters a loader has been writing into
     * @param unknownBlocksReported whether this world has already had its unknown blocks warned
     *                              about; set by this method the first time there are any
     * @return the report for that world
     */
    @VisibleForTesting
    static WorldHealth report(String world, AnvilDiagnostics diagnostics, AtomicBoolean unknownBlocksReported) {
        WorldHealth health = new WorldHealth(
                world,
                diagnostics.chunksLoaded(),
                diagnostics.chunksSkippedWithoutRegionFile() + diagnostics.chunksSkippedWithoutEntry(),
                diagnostics.unknownBlockCount(),
                diagnostics.chunksSkippedAsUnsupported(),
                diagnostics.unsupportedChunkVersions(),
                diagnostics.errors());

        if (health.unknownBlocks() > 0 && unknownBlocksReported.compareAndSet(false, true)) {
            LOGGER.warn("{} - each unknown name became air, so the course has holes where those blocks were",
                    health.describe());
        }
        return health;
    }

    /**
     * Whether this world has already had its unknown block names warned about.
     *
     * <p>Package-private for the test, and narrower than it looks: {@link #report} pins how the
     * warning treats the flag it is handed, but nothing on the public surface can see that
     * {@link #healthOf(String)} hands it that world's own flag rather than a fresh one — a
     * {@code healthOf} that warned on every call is indistinguishable from one that warns once.
     * This is what tells the two apart, and what shows the flag is per world and not shared.
     *
     * @param world the world directory's name
     * @return true once a non-zero unknown-block count has been reported for that world
     * @throws UnknownWorldException if no region data sits behind the name
     */
    @VisibleForTesting
    boolean hasWarnedAboutUnknownBlocks(String world) {
        return load(world).unknownBlocksReported().get();
    }

    /**
     * The loader behind a world, for the test that proves {@link #close()} really closes it.
     *
     * <p>Package-private and only that: {@link #close()} clears the cache, so nothing on the public
     * surface can observe whether the loaders it held were closed or merely dropped. Do not widen
     * this — a caller outside this package holding a loader could close it under a live instance.
     *
     * @param world the world directory's name
     * @return the loader for that world
     * @throws UnknownWorldException if no region data sits behind the name
     */
    @VisibleForTesting
    FalcoAnvilLoader loaderFor(String world) {
        return load(world).loader();
    }

    private LoadedWorld load(String world) {
        return loaded.computeIfAbsent(world, this::openWorld);
    }

    private LoadedWorld openWorld(String world) {
        AnvilDiagnostics diagnostics = new AnvilDiagnostics();
        FalcoAnvilLoader loader = buildLoader(worldsRoot.resolve(world), diagnostics);

        try {
            // Falco's own resolution rather than a second derivation of the directory layout here:
            // the loader picks the dimension layout or the legacy one depending on what exists, and
            // a check that re-derived that choice could disagree with the loader it is guarding.
            if (!WorldFolders.holdsRegionData(loader.regionDirectory())) {
                throw new UnknownWorldException(world, loader.regionDirectory(), loader.legacyLayout());
            }
        } catch (RuntimeException failure) {
            closeQuietly(loader, world);
            throw failure;
        }

        InstanceContainer instance = instanceManager.createInstanceContainer(DimensionType.OVERWORLD);
        instance.setChunkLoader(loader);
        instance.enableAutoChunkLoad(true);
        return new LoadedWorld(instance, loader, diagnostics, new AtomicBoolean(),
                regionFingerprint(loader.regionDirectory()));
    }

    private static FalcoAnvilLoader buildLoader(Path worldRoot) {
        return buildLoader(worldRoot, new AnvilDiagnostics());
    }

    private static FalcoAnvilLoader buildLoader(Path worldRoot, AnvilDiagnostics diagnostics) {
        return FalcoAnvilLoader.builder()
                .diagnostics(diagnostics)
                .build(worldRoot, DimensionType.OVERWORLD.key());
    }

    /**
     * The name, size and modification time of every region file, sorted by name. A change to any of them
     * is a change to the world on disk. Missing directory gives an empty fingerprint.
     */
    private static String regionFingerprint(Path regionDirectory) {
        if (!Files.isDirectory(regionDirectory)) {
            return "";
        }
        try (Stream<Path> files = Files.list(regionDirectory)) {
            return files.sorted().map(file -> {
                try {
                    return "%s:%d:%d".formatted(file.getFileName(), Files.size(file),
                            Files.getLastModifiedTime(file).toMillis());
                } catch (IOException exception) {
                    throw new UncheckedIOException("cannot stat region file %s".formatted(file), exception);
                }
            }).collect(Collectors.joining("|"));
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot list the region directory %s".formatted(regionDirectory), exception);
        }
    }

    private static void closeQuietly(FalcoAnvilLoader loader, String world) {
        try {
            loader.close();
        } catch (IOException exception) {
            LOGGER.warn("Could not close the rejected loader for world '{}'", world, exception);
        }
    }

    private record LoadedWorld(Instance instance, FalcoAnvilLoader loader, AnvilDiagnostics diagnostics,
                               AtomicBoolean unknownBlocksReported, String fingerprint) {
    }
}
