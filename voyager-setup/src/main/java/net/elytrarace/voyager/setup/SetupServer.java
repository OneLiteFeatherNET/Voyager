package net.elytrarace.voyager.setup;

import io.avaje.inject.BeanScope;

import net.elytrarace.voyager.api.config.ConfigProblem;
import net.elytrarace.voyager.setup.config.SetupSettings;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.ExecutionType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The composition root of the setup server, the second {@code main} of the rebuild after voyager-server.
 *
 * <p>The order is the one {@code VoyagerServer} uses: settings and both directories first, so a wrong working
 * directory is a refusal naming the absolute path; then {@code MinecraftServer.init()}; then the object graph, built
 * eagerly; then events and commands; then the listening socket. A refused start never opens a socket.
 */
public final class SetupServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetupServer.class);
    private static final String SOURCE = "setup settings";

    private SetupServer() {
    }

    public static void main(String[] args) {
        SetupSettings settings;
        try {
            settings = SetupSettings.fromEnvironment(args);
        } catch (IllegalArgumentException exception) {
            LOGGER.error("Setup server refused to start: {}", exception.getMessage());
            System.exit(1);
            return;
        }
        List<ConfigProblem> problems = directoryProblems(settings);
        if (!problems.isEmpty()) {
            for (ConfigProblem problem : problems) {
                LOGGER.error("Setup server refused to start: {}", problem.message());
            }
            System.exit(1);
            return;
        }
        LOGGER.info("Voyager setup server — data={} worlds={} at {}:{}", settings.dataPath().toAbsolutePath(),
                settings.worldsPath().toAbsolutePath(), settings.host(), settings.port());

        MinecraftServer server = MinecraftServer.init();
        BeanScope graph;
        try {
            graph = openGraph(settings);
        } catch (RuntimeException exception) {
            LOGGER.error("Setup server refused to start while building the object graph", exception);
            System.exit(1);
            return;
        }
        registerShutdownTask(graph);

        LOGGER.info("Listening on {}:{}", settings.host(), settings.port());
        server.start(settings.host(), settings.port());
    }

    /**
     * The directories the server needs, checked before anything else runs.
     *
     * @return one problem per missing directory, naming its absolute path; empty when both exist
     */
    static List<ConfigProblem> directoryProblems(SetupSettings settings) {
        List<ConfigProblem> problems = new ArrayList<>(2);
        if (!Files.isDirectory(settings.dataPath())) {
            problems.add(missing(SetupSettings.DATA_PATH_PROPERTY, settings.dataPath()));
        }
        if (!Files.isDirectory(settings.worldsPath())) {
            problems.add(missing(SetupSettings.WORLDS_PATH_PROPERTY, settings.worldsPath()));
        }
        return List.copyOf(problems);
    }

    /**
     * Builds the object graph for {@code settings}, with every singleton constructed before this returns, so that a
     * bean which cannot be built is a refusal here and not an exception on the first click.
     *
     * @return the scope; the caller closes it when the server stops
     * @throws IllegalStateException if a bean cannot be built
     */
    public static BeanScope openGraph(SetupSettings settings) {
        try {
            return BeanScope.builder()
                    .bean(SetupSettings.class, settings)
                    .shutdownHook(false)
                    .build();
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "the object graph did not build: %s".formatted(exception.getMessage()), exception);
        }
    }

    private static ConfigProblem missing(String key, Path directory) {
        return new ConfigProblem(key, SOURCE,
                "the directory %s is missing or is not a directory".formatted(directory.toAbsolutePath()),
                ConfigProblem.Severity.ERROR);
    }

    /** Closes the graph inside Minestom's own shutdown, ordered with the rest of the server, as VoyagerServer does. */
    private static void registerShutdownTask(BeanScope graph) {
        MinecraftServer.getSchedulerManager().buildShutdownTask(() -> {
            LOGGER.info("Shutting down");
            graph.close();
        });
    }
}
