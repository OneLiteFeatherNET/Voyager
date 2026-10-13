package net.elytrarace.voyager.platform.cup;

import net.elytrarace.voyager.api.math.Vec3;
import net.elytrarace.voyager.api.race.BoostConfig;
import net.elytrarace.voyager.platform.convert.VelocityExit;
import net.elytrarace.voyager.platform.convert.Vectors;
import net.elytrarace.voyager.platform.flight.FireworkBoostTracker;
import net.elytrarace.voyager.platform.flight.Racers;
import net.elytrarace.voyager.platform.hud.RaceFeedback;
import net.elytrarace.voyager.platform.tick.FlightTickDriver;
import net.elytrarace.voyager.platform.text.Messages;
import net.elytrarace.voyager.race.reset.ResetCause;
import net.elytrarace.voyager.race.reset.ResetPlan;
import net.minestom.server.entity.Player;

import java.util.UUID;

/**
 * Sends one racer back to where a {@link ResetPlan} says, and tells them why.
 *
 * <p>The only class that performs a reset. The decision is made by {@link RunReset} and the cup decides when
 * it applies; this class does the effects, in an order a racer could observe:
 *
 * <ol>
 *   <li>the run is replaced with the planned one, so no later tick reads the run from before the reset;</li>
 *   <li>an active firework burn is cancelled, and its cooldown kept, so a reset cannot refresh a rocket;</li>
 *   <li>the racer's shadow flight is forgotten, so the next flight sample re-seeds from the client;</li>
 *   <li>the racer is moved to the target, with the chunks around it loaded and a confirmed teleport. A checkpoint
 *       target is entered a block behind and above its centre, so the racer is airborne;</li>
 *   <li>the racer is placed with the equipment, facing the planned point. A checkpoint reset is gliding, with no
 *       impulse; a spawn reset is on foot, as at GO, and the racer deploys the elytra to take off;</li>
 *   <li>the velocity is zeroed, so a fall is not carried into the target, and nothing else is sent;</li>
 *   <li>the title and the sound are sent, in this same tick.</li>
 * </ol>
 */
public final class RunResetter {

    /**
     * How far back along the checkpoint's normal a checkpoint reset places the racer, in blocks. The racer starts
     * behind the ring they are reset to, so they fly through it in order rather than standing in its plane.
     */
    private static final double GLIDE_START_BEHIND = 1.0;

    /**
     * How far above the checkpoint's centre a checkpoint reset places the racer, in blocks. Above the ring, so
     * the racer is airborne when placed and the glide is not ended by the first position packet from the ground.
     */
    private static final double GLIDE_START_ABOVE = 1.0;

    private final RaceRuns runs;
    private final FireworkBoostTracker boosts;
    private final FlightTickDriver flight;

    /**
     * @param runs the run board the planned run is written into
     * @param boosts the boost tracker whose burn is cancelled
     * @param flight the flight driver whose shadow state for the racer is forgotten
     */
    public RunResetter(RaceRuns runs, FireworkBoostTracker boosts, FlightTickDriver flight) {
        this.runs = runs;
        this.boosts = boosts;
        this.flight = flight;
    }

    /**
     * Performs {@code plan} for {@code racer}. The racer must hold a run on the map being raced.
     *
     * @param boost the tuning of the map being raced, for the rockets the racer is given again
     * @throws net.elytrarace.voyager.platform.cup.exception.UnstartedRunException if the racer holds no run
     */
    public void reset(Player racer, ResetPlan plan, BoostConfig boost) {
        UUID id = racer.getUuid();
        runs.resetTo(id, plan.run());
        boosts.cancelBurn(id);
        flight.forget(id);
        boolean checkpoint = plan.targetRing().isPresent();
        if (checkpoint) {
            Vec3 normal = plan.toward().minus(plan.position());
            Vec3 start = glideStart(plan.position(), normal);
            MapTransition.reposition(racer, Vectors.toMinestom(start).asPos());
            Racers.place(racer, start.plus(normal), boost, true);
        } else {
            MapTransition.reposition(racer, Vectors.toMinestom(plan.position()).asPos());
            Racers.place(racer, plan.toward(), boost, false);
        }
        VelocityExit.send(racer, Vec3.ZERO);
        RaceFeedback.reset(
                racer,
                Messages.resetTitle(plan.cause() == ResetCause.OUT_OF_BOUNDS),
                Messages.resetSubtitle(plan.targetRing()));
    }

    /** The point a checkpoint reset starts from: behind the centre along {@code normal}, and above it. */
    private static Vec3 glideStart(Vec3 centre, Vec3 normal) {
        return centre.minus(normal.scale(GLIDE_START_BEHIND)).plus(new Vec3(0, GLIDE_START_ABOVE, 0));
    }
}
