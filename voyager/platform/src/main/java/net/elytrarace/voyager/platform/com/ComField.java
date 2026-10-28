package net.elytrarace.voyager.platform.com;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.MapDefinition;
import net.elytrarace.voyager.api.race.com.ComDifficulty;
import net.elytrarace.voyager.platform.cup.Competitor;
import net.elytrarace.voyager.platform.cup.CompetitorField;
import net.elytrarace.voyager.race.com.ComPath;
import net.elytrarace.voyager.race.com.ComRoster;
import net.elytrarace.voyager.race.com.ComSeat;
import net.elytrarace.voyager.race.com.ComState;
import net.elytrarace.voyager.race.com.ComStatus;
import net.elytrarace.voyager.race.com.ComTuning;
import net.elytrarace.voyager.race.flow.RaceClock;
import net.elytrarace.voyager.race.line.RacingLine;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The computer racers of a cup, on the Minestom side: one {@link ComBody} and one brain state per seat, with the entries
 * on the tab list of the humans.
 *
 * <p>The field decides who is on the grid and where they are. It does not decide which humans there are, how many
 * computer racers a cup wants, or which difficulty they play: those come in through the constructor, from the server's
 * composition root, which reads them from settings. The clock is given too, and the round seed is read from it when a
 * cup's first map starts: a round seats its racers from the time it starts, not from the time the server booted.
 *
 * <p>Each map start tops the grid up to the wanted count: a racer that is already on the grid keeps its body and its
 * name, and is moved to the new start. Only a seat the humans have taken goes, and that happens at a map start, never
 * in the middle of one. The racing line of a map is built once, on the first map start that needs it, and every racer
 * on that map shares it.
 *
 * <p>The field never touches {@code RaceRuns}. It hands the cup ids and positions, and the cup keeps the runs.
 */
public final class ComField implements CompetitorField {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComField.class);

    private final Supplier<Collection<Player>> humans;
    private final int comCount;
    private final ComDifficulty difficulty;
    private final Clock clock;

    /** The seed of the round in progress, read from the clock when the round's first map starts; null before that. */
    private @Nullable Long roundSeed;
    private final Function<String, Instance> instances;
    private final ComBrain brain;
    private final ComTabList tabList;

    /** The racers on the grid, in seat order. */
    private final Map<UUID, Racer> racers = new LinkedHashMap<>();

    /** The racing line of each map, keyed by the map's name, built the first time the map is started. */
    private final Map<String, ComPath> paths = new HashMap<>();

    /**
     * Builds the field that the server runs when computer racers are switched on.
     *
     * @param humans the human players, read on every call; the count of them decides how many seats are left
     * @param comCount the grid size the cup wants, humans included, between zero and {@link ComRoster#MAXIMUM_COUNT}
     * @param difficulty how hard every computer racer plays
     * @param clock the clock the round seed is read from, once, when the cup's first map starts
     * @param instances the instance a map's world is loaded into, by the world's name
     */
    public ComField(Supplier<Collection<Player>> humans, int comCount, ComDifficulty difficulty,
            Clock clock, Function<String, Instance> instances) {
        this(humans, comCount, difficulty, clock, instances, new PilotBrain());
    }

    /** The same field with the brain given, so a test can put a fake in its place. */
    ComField(Supplier<Collection<Player>> humans, int comCount, ComDifficulty difficulty, Clock clock,
            Function<String, Instance> instances, ComBrain brain) {
        this.humans = humans;
        this.comCount = comCount;
        this.difficulty = difficulty;
        this.clock = clock;
        this.instances = instances;
        this.brain = brain;
        this.tabList = new ComTabList(humans);
    }

    @Override
    public List<UUID> prepare(int mapIndex, MapDefinition map) {
        List<ComSeat> seats = ComRoster.plan(comCount, humans.get().size(), roundSeedFor(mapIndex));
        ComPath path = pathFor(map);
        Instance instance = instances.apply(map.world());

        Map<UUID, Racer> planned = new LinkedHashMap<>();
        for (ComSeat seat : seats) {
            UUID id = ComNames.idOf(seat);
            Racer racer = racers.remove(id);
            if (racer == null) {
                ComBody body = new ComBody(id, ComNames.displayNameOf(seat));
                racer = new Racer(id, ComTuning.of(difficulty, seat.seed()), body);
                tabList.add(id, ComNames.nameOf(seat), ComNames.displayNameOf(seat));
            }
            racer.startRun(seat, path);
            racer.body.spawn(instance, toPos(brain.positionOf(racer.state, racer.tuning, path)));
            planned.put(id, racer);
        }
        for (Racer leftover : racers.values()) {
            leftover.body.remove();
            tabList.remove(leftover.id);
        }
        racers.clear();
        racers.putAll(planned);
        return List.copyOf(planned.keySet());
    }

    @Override
    public List<Competitor> advance(RaceClock clock) {
        tabList.sync();
        List<Competitor> reported = new ArrayList<>(racers.size());
        for (Racer racer : racers.values()) {
            if (racer.state.status() != ComStatus.RACING) {
                continue;
            }
            racer.state = brain.step(racer.state, racer.tuning, racer.path, clock.gameTick());
            Vec3 at = brain.positionOf(racer.state, racer.tuning, racer.path);
            racer.body.teleport(toPos(at));
            reported.add(new Competitor(racer.id, at, true));
        }
        return List.copyOf(reported);
    }

    @Override
    public void dismiss() {
        for (Racer racer : racers.values()) {
            racer.body.remove();
        }
        tabList.dismiss();
        racers.clear();
    }

    /**
     * The seed of the round the map belongs to. The first map of a cup starts a round, and the seed is read from the
     * clock then, once, and logged so the round can be replayed. The maps after the first keep that seed.
     */
    private long roundSeedFor(int mapIndex) {
        if (mapIndex == 0 || roundSeed == null) {
            roundSeed = clock.millis();
            LOGGER.info("Computer racers round seed {} at map index {}", roundSeed, mapIndex);
        }
        return roundSeed;
    }

    /** The racing line of {@code map}, built on first use and shared by every racer that runs it. */
    private ComPath pathFor(MapDefinition map) {
        return paths.computeIfAbsent(map.key().asString(),
                name -> ComPath.fromGrid(map.spawn(), RacingLine.of(map), map.rings()));
    }

    private static Pos toPos(Vec3 at) {
        return new Pos(at.x(), at.y(), at.z());
    }

    /** One computer racer: its seat, its tuning, its body, and its state on the current map. */
    private static final class Racer {

        private final UUID id;
        private final ComTuning tuning;
        private final ComBody body;
        private ComState state;
        private ComPath path;

        Racer(UUID id, ComTuning tuning, ComBody body) {
            this.id = id;
            this.tuning = tuning;
            this.body = body;
        }

        /** Starts a fresh run on the map: the racer is back on the grid, at the start, with no ring passed. */
        void startRun(ComSeat seat, ComPath path) {
            this.state = ComState.start(seat);
            this.path = path;
        }
    }
}
