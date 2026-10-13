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
 *   <li>the racer is moved to the target, with the chunks around it loaded and a confirmed teleport;</li>
 *   <li>the racer is placed at the target, facing the planned point, with the equipment and no glide;</li>
 *   <li>the velocity is zeroed, so a fall is not carried into the target, and nothing else is sent;</li>
 *   <li>the title and the sound are sent, in this same tick.</li>
 * </ol>
 */
public final class RunResetter {

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
        MapTransition.reposition(racer, Vectors.toMinestom(plan.position()).asPos());
        Racers.place(racer, plan.toward(), boost);
        VelocityExit.send(racer, Vec3.ZERO);
        RaceFeedback.reset(
                racer,
                Messages.resetTitle(plan.cause() == ResetCause.OUT_OF_BOUNDS),
                Messages.resetSubtitle(plan.targetRing()));
    }
}
