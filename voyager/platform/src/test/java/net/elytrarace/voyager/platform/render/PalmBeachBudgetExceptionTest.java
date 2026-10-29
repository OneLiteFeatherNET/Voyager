package net.elytrarace.voyager.platform.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.platform.catalog.adapter.MapDefinitionAdapter;
import net.elytrarace.voyager.platform.catalog.adapter.Vec3Adapter;
import net.elytrarace.voyager.race.line.RacingLine;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.testing.Collector;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import net.minestom.testing.TestConnection;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The accepted exception for {@code elytrarace:palm_beach}, pinned so that it cannot get worse silently.
 *
 * <p>The owner decided on 2026-10-29 to accept that palm_beach's DUST line is sent above the 2,000 per-racer limit of
 * {@code race/racing-line-display}, and to keep its spacing at 1.0. The figures measured on 2026-10-28 are 3,690 packets
 * a second for the worst of sixteen racers and 29,800 in total. The ceilings here are 4,000 and 30,000. A change that
 * takes the course above either of them needs a new decision, and this test is where that decision is recorded.
 *
 * <p>Sixteen racers are placed at evenly spaced rings in FULL mode, as {@link GuideLineRendererTest} places them for the
 * shipped course. The race clock is ticked explicitly for three seconds, a multiple of every refresh interval, and each
 * figure is the packet count divided by three. Nothing sleeps and nothing reads the system clock.
 */
@EnvTest
class PalmBeachBudgetExceptionTest {

    private static final int RACERS = 16;
    private static final int SECONDS = 3;
    private static final long CEILING_PER_RACER = 4_000;
    private static final long CEILING_TOTAL = 30_000;

    @Test
    void theWorstRacerOnPalmBeachIsSentNoMoreThanTheAcceptedCeilingPerSecond(Env env) {
        List<Long> perRacer = packetsPerSecond(env);

        assertThat(perRacer.stream().mapToLong(Long::longValue).max().orElseThrow())
                .as("packets per second to the worst racer on palm_beach, measured 3,690 on 2026-10-28, %s", perRacer)
                .isLessThanOrEqualTo(CEILING_PER_RACER);
    }

    @Test
    void theTotalOnPalmBeachIsSentNoMoreThanThirtyThousandPerSecond(Env env) {
        List<Long> perRacer = packetsPerSecond(env);

        assertThat(perRacer.stream().mapToLong(Long::longValue).sum())
                .as("packets per second in total on palm_beach, measured 29,800 on 2026-10-28, %s", perRacer)
                .isLessThanOrEqualTo(CEILING_TOTAL);
    }

    /** Runs the sixteen-racer field for {@link #SECONDS} seconds and returns each racer's packets per second. */
    private static List<Long> packetsPerSecond(Env env) {
        MapDefinition palmBeach = readPalmBeach();
        RacingLine line = RacingLine.of(palmBeach);
        int step = (line.ringCount() - 1) / RACERS;

        Instance instance = env.createFlatInstance();
        loadChunksAround(instance, palmBeach);

        List<Player> players = new ArrayList<>();
        List<Collector<ParticlePacket>> incoming = new ArrayList<>();
        for (int i = 0; i < RACERS; i++) {
            Vec3 at = palmBeach.rings().get(i * step).center();
            TestConnection connection = env.createConnection();
            players.add(connection.connect(instance, new Pos(at.x(), at.y(), at.z())));
            incoming.add(connection.trackIncoming(ParticlePacket.class));
        }

        GuideLineRenderer renderer = new GuideLineRenderer();
        for (int tick = 0; tick < SECONDS * 20; tick++) {
            for (int i = 0; i < RACERS; i++) {
                renderer.render(players.get(i), palmBeach, i * step, tick);
            }
        }

        List<Long> perSecond = new ArrayList<>();
        for (Collector<ParticlePacket> packets : incoming) {
            perSecond.add(packets.collect().size() / (long) SECONDS);
        }
        return perSecond;
    }

    private static MapDefinition readPalmBeach() {
        Path file = Path.of("..", "server", "src", "main", "resources", "maps", "palm_beach.json");
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Vec3.class, new Vec3Adapter())
                .registerTypeAdapter(MapDefinition.class, new MapDefinitionAdapter())
                .create();
        try {
            return gson.fromJson(JsonParser.parseString(Files.readString(file)), MapDefinition.class);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read %s".formatted(file), e);
        }
    }

    /** Loads every chunk the course's rings lie in, so that a racer placed on any ring stands on loaded ground. */
    private static void loadChunksAround(Instance instance, MapDefinition course) {
        int minX = (int) Math.floor(course.rings().stream().mapToDouble(r -> r.center().x()).min().orElseThrow()) - 16;
        int maxX = (int) Math.floor(course.rings().stream().mapToDouble(r -> r.center().x()).max().orElseThrow()) + 16;
        int minZ = (int) Math.floor(course.rings().stream().mapToDouble(r -> r.center().z()).min().orElseThrow()) - 16;
        int maxZ = (int) Math.floor(course.rings().stream().mapToDouble(r -> r.center().z()).max().orElseThrow()) + 16;
        for (int chunkX = Math.floorDiv(minX, 16); chunkX <= Math.floorDiv(maxX, 16); chunkX++) {
            for (int chunkZ = Math.floorDiv(minZ, 16); chunkZ <= Math.floorDiv(maxZ, 16); chunkZ++) {
                instance.loadChunk(chunkX, chunkZ).join();
            }
        }
    }
}
