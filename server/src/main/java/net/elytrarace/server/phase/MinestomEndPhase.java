package net.elytrarace.server.phase;

import net.elytrarace.common.ecs.Entity;
import net.elytrarace.common.ecs.EntityManager;
import net.elytrarace.server.ecs.component.PlayerRefComponent;
import net.elytrarace.server.ecs.component.ScoreComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.utils.time.TimeUnit;
import net.theevilreaper.xerus.api.phase.TickDirection;
import net.theevilreaper.xerus.api.phase.TimedPhase;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * End phase for the Minestom server.
 * <p>
 * Counts down from a configurable number of ticks (default 100 ticks = 5 seconds at 20 TPS)
 * while displaying the results and remaining time to all players. When the countdown
 * finishes, the server is stopped.
 * <p>
 * Final scores are read from {@link ScoreComponent} on player entities in the
 * {@link EntityManager}. Position bonuses (1st: 50, 2nd: 30, 3rd: 20, rest: 10)
 * are applied and displayed as a title overlay.
 */
public final class MinestomEndPhase extends TimedPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinestomEndPhase.class);
    private static final int DEFAULT_END_TICKS = 100;
    private static final int[] POSITION_BONUSES = {50, 30, 20};
    private static final int DEFAULT_BONUS = 10;

    private final int endTicksValue;
    private final @Nullable EntityManager entityManager;
    private boolean finishing = false;
    private boolean suppressOnFinish = false;

    public MinestomEndPhase() {
        this(DEFAULT_END_TICKS, null);
    }

    public MinestomEndPhase(int endTicks, @Nullable EntityManager entityManager) {
        super("end", TimeUnit.SERVER_TICK, 20);
        this.endTicksValue = endTicks;
        this.entityManager = entityManager;
        setEndTicks(0);
        setCurrentTicks(endTicks);
        setTickDirection(TickDirection.DOWN);
    }

    @Override
    public void finish() {
        if (finishing) {
            return;
        }
        finishing = true;
        super.finish();
    }

    @Override
    public void onStart() {
        finishing = false;
        setCurrentTicks(endTicksValue);
        super.onStart();
        LOGGER.info("End phase started — showing results for {} ticks", endTicksValue);
        displayFinalScores();
    }

    @Override
    public void onUpdate() {
        PhaseUiHelper.broadcastTimeActionBar("phase.end.time", getCurrentTicks());
    }

    @Override
    protected void onFinish() {
        if (suppressOnFinish) {
            LOGGER.info("End phase finished — server stop suppressed (game restart)");
            return;
        }
        LOGGER.info("End phase finished — scheduling server stop");
        // Schedule stop for the next tick so the finish() chain completes cleanly
        // before the server shuts down (avoids "Advance called on not running phase").
        MinecraftServer.getSchedulerManager()
                .buildTask(MinecraftServer::stopCleanly)
                .delay(1, TimeUnit.SERVER_TICK)
                .schedule();
    }

    /**
     * Suppresses the server stop that normally occurs when the end phase finishes.
     * <p>
     * Call this before {@link #finish()} during a game restart to prevent the
     * end phase from shutting down the server.
     *
     * @param suppress {@code true} to suppress the server stop
     */
    public void setSuppressOnFinish(boolean suppress) {
        this.suppressOnFinish = suppress;
    }

    /**
     * Calculates position bonuses and displays the final scoreboard as title
     * overlays to all players. Scores are read from {@link ScoreComponent}.
     */
    private void displayFinalScores() {
        if (entityManager == null) {
            LOGGER.warn("No EntityManager provided — skipping final score display");
            return;
        }

        // Collect player entities with scores and sort by total descending
        List<Entity> ranked = entityManager.getEntitiesWithComponent(ScoreComponent.class).stream()
                .filter(e -> e.hasComponent(PlayerRefComponent.class))
                .sorted(Comparator.<Entity, Integer>comparing(
                        e -> e.getComponent(ScoreComponent.class).getTotal()).reversed())
                .toList();

        // Apply position bonuses
        for (int i = 0; i < ranked.size(); i++) {
            int bonus = i < POSITION_BONUSES.length ? POSITION_BONUSES[i] : DEFAULT_BONUS;
            ranked.get(i).getComponent(ScoreComponent.class).setPositionBonus(bonus);
        }

        // Build scoreboard message and show to all players
        var scoreboardBuilder = Component.text()
                .append(Component.text("Final Results", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline());

        for (int i = 0; i < ranked.size(); i++) {
            var ref = ranked.get(i).getComponent(PlayerRefComponent.class);
            var score = ranked.get(i).getComponent(ScoreComponent.class);
            var color = i == 0 ? NamedTextColor.GOLD : i == 1 ? NamedTextColor.GRAY : NamedTextColor.WHITE;
            scoreboardBuilder
                    .append(Component.text("#" + (i + 1) + " ", color, TextDecoration.BOLD))
                    .append(Component.text(ref.getPlayer().getUsername(), color))
                    .append(Component.text(" — " + score.getTotal() + " pts", NamedTextColor.WHITE))
                    .append(Component.newline());
        }

        var scoreboard = scoreboardBuilder.build();
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            player.showTitle(Title.title(
                    Component.text("Race Complete!", NamedTextColor.GOLD, TextDecoration.BOLD),
                    ranked.isEmpty() ? Component.empty()
                            : Component.text("Winner: " +
                                    ranked.getFirst().getComponent(PlayerRefComponent.class)
                                            .getPlayer().getUsername(),
                                    NamedTextColor.GREEN),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
            player.sendMessage(scoreboard);
        }

        LOGGER.info("Final scores displayed for {} players", ranked.size());
    }
}
