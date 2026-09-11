package net.elytrarace.tools.recorder.capture;

import net.elytrarace.tools.recorder.format.BlockBox;
import net.elytrarace.tools.recorder.format.TraceFile;
import net.elytrarace.tools.recorder.format.TraceMetadata;
import net.elytrarace.tools.recorder.format.TraceTick;
import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;
import net.elytrarace.tools.recorder.script.FlightScript;
import net.elytrarace.tools.recorder.script.ScriptedInput;
import net.elytrarace.tools.recorder.world.SolidBlockSource;
import net.elytrarace.tools.recorder.world.WorldSliceCollector;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates one recording, tick by tick, without touching a Bukkit type.
 *
 * <p>This is the one place in the module where a record does not fit: a recording is inherently a
 * growing, in-progress thing, driven from the outside one {@link GliderSample} at a time. Task 6's
 * Bukkit listener is a thin adapter that fills a {@code GliderSample} from a live entity each tick
 * and calls {@link #record(GliderSample)}; every rule about what makes a recording valid lives here
 * instead, where it is reachable from a test without a server.
 */
public final class TraceCollector {

    private static final int FORMAT_VERSION = 1;

    private final String minecraftVersion;
    private final String profile;
    private final double gravity;
    private final FlightScript script;
    private final List<TraceTick> ticks = new ArrayList<>();

    public TraceCollector(String minecraftVersion, String profile, double gravity, FlightScript script) {
        this.minecraftVersion = minecraftVersion;
        this.profile = profile;
        this.gravity = gravity;
        this.script = script;
    }

    /** Appends {@code sample} as the next tick, assigning it the next consecutive index. */
    public void record(GliderSample sample) {
        if (isComplete()) {
            throw new InvalidTraceException(
                    "cannot record tick %s; the script only covers %s tick(s)"
                            .formatted(ticks.size(), script.durationTicks()));
        }
        ticks.add(new TraceTick(
                ticks.size(),
                sample.posX(), sample.posY(), sample.posZ(),
                sample.velX(), sample.velY(), sample.velZ(),
                sample.yaw(), sample.pitch(),
                sample.onGround(),
                sample.fireworkBoostActive(),
                sample.fireworkTicksRemaining()));
    }

    /**
     * The scripted input for the tick about to be recorded: apply it to the entity, let exactly one
     * world tick pass, then pass the resulting state to {@link #record(GliderSample)}. That recorded
     * tick will carry this same index — {@code nextInput()} for index {@code k} and the sample
     * {@code record} assigns index {@code k} are one input/response pair, not offset from each other.
     */
    public ScriptedInput nextInput() {
        return script.inputAt(ticks.size());
    }

    /** Whether every tick the script demands has been recorded. */
    public boolean isComplete() {
        return ticks.size() >= script.durationTicks();
    }

    /**
     * Finishes the recording: collects the world slice the flown path needs and returns the
     * completed {@link TraceFile}. Throws {@link InvalidTraceException} if the script has not been
     * fully recorded yet — a half-recorded script produces a fixture the replay cannot trust.
     */
    public TraceFile finish(SolidBlockSource source, double radius) {
        if (!isComplete()) {
            throw new InvalidTraceException(
                    "cannot finish; only %s of %s scripted tick(s) have been recorded"
                            .formatted(ticks.size(), script.durationTicks()));
        }
        List<BlockBox> worldSlice = WorldSliceCollector.collect(ticks, radius, source);
        TraceMetadata metadata = new TraceMetadata(minecraftVersion, profile, gravity, FORMAT_VERSION, worldSlice);
        return new TraceFile(metadata, ticks);
    }
}
