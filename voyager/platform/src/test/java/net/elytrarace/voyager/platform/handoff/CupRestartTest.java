package net.elytrarace.voyager.platform.handoff;

import net.elytrarace.voyager.platform.LogCapture;
import net.elytrarace.voyager.race.rotation.CupRotationStore;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The restart after a finished cup, driven by explicit calls: a request, then one {@code tick} per server tick with the number
 * of players still connected. No clock, no sleep, and a fresh store and restart in every test.
 */
class CupRestartTest {

    private static final String STATE = "/var/voyager/rotation";

    /** Holds one name, as a state file does, and records every read so a test can see whether one happened. */
    private static final class MemoryStore implements CupRotationStore {

        private final List<String> writes = new ArrayList<>();
        private Optional<String> held;
        private int reads;

        MemoryStore(Optional<String> held) {
            this.held = held;
        }

        @Override
        public Optional<String> read() {
            reads++;
            return held;
        }

        @Override
        public void write(String cup) {
            writes.add(cup);
            held = Optional.of(cup);
        }
    }

    @Test
    void aDisabledRestartIsNeverRequestedAndNeverAsksForAStop() {
        CupRestart restart = CupRestart.disabled();

        assertThat(restart.enabled()).isFalse();
        assertThat(restart.request("alpha_cup", "legacy_cup", true)).isFalse();
        assertThat(restart.requested()).isFalse();
        for (int tick = 0; tick < CupRestart.DRAIN_TICKS + 1; tick++) {
            assertThat(restart.tick(0)).isFalse();
        }
    }

    @Test
    void aRestartWithOneCupIsAcceptedWithoutReadingAStateAndLogsOneInfoLine() {
        // A rotation of one cup keeps no state, so the restart is accepted without a read.
        MemoryStore store = new MemoryStore(Optional.empty());
        CupRestart restart = CupRestart.after(store, STATE);

        try (LogCapture log = LogCapture.of(CupRestart.class)) {
            assertThat(restart.request("alpha_cup", "alpha_cup", false)).isTrue();

            assertThat(log.infos()).singleElement().satisfies(line -> assertThat(line)
                    .contains("alpha_cup").contains("finished"));
        }
        assertThat(restart.requested()).isTrue();
        assertThat(store.reads).isZero();
    }

    @Test
    void aRotationRestartIsAcceptedWhenTheStateReadsBackTheNextCup() {
        MemoryStore store = new MemoryStore(Optional.of("legacy_cup"));
        CupRestart restart = CupRestart.after(store, STATE);

        try (LogCapture log = LogCapture.of(CupRestart.class)) {
            assertThat(restart.request("alpha_cup", "legacy_cup", true)).isTrue();

            assertThat(log.errors()).isEmpty();
            assertThat(log.infos()).singleElement().satisfies(line -> assertThat(line)
                    .contains("alpha_cup").contains("legacy_cup"));
        }
        assertThat(restart.requested()).isTrue();
    }

    @Test
    void aStateThatDoesNotReadBackRefusesTheRestartAndLogsOneErrorThatNamesTheFile() {
        MemoryStore store = new MemoryStore(Optional.of("alpha_cup"));
        CupRestart restart = CupRestart.after(store, STATE);

        try (LogCapture log = LogCapture.of(CupRestart.class)) {
            assertThat(restart.request("alpha_cup", "legacy_cup", true)).isFalse();

            assertThat(log.errors()).singleElement().satisfies(line -> assertThat(line)
                    .contains(STATE).contains("legacy_cup"));
            assertThat(log.infos()).isEmpty();
        }
        assertThat(restart.requested()).isFalse();
        assertThat(restart.tick(0)).isFalse();
    }

    @Test
    void theStopIsAskedOnceWhenNoPlayerIsConnected() {
        CupRestart restart = CupRestart.after(new MemoryStore(Optional.empty()), STATE);
        assertThat(restart.tick(0)).describedAs("no stop before a request").isFalse();
        restart.request("alpha_cup", "alpha_cup", false);

        assertThat(restart.tick(0)).isTrue();
        assertThat(restart.tick(0)).isFalse();
    }

    @Test
    void theStopWaitsWhileAPlayerIsConnectedAndIsAskedOnTheDrainLimitWithOneWarn() {
        CupRestart restart = CupRestart.after(new MemoryStore(Optional.empty()), STATE);
        restart.request("alpha_cup", "alpha_cup", false);

        try (LogCapture log = LogCapture.of(CupRestart.class)) {
            for (int tick = 1; tick < CupRestart.DRAIN_TICKS; tick++) {
                assertThat(restart.tick(1)).describedAs("tick %s, a player is still connected", tick).isFalse();
            }
            assertThat(log.warnings()).isEmpty();

            assertThat(restart.tick(1)).describedAs("the drain limit of %s ticks", CupRestart.DRAIN_TICKS).isTrue();
            assertThat(log.warnings()).singleElement().satisfies(line -> assertThat(line).contains("1 player"));
        }
        assertThat(restart.tick(1)).isFalse();
    }

    @Test
    void aSecondRequestAfterAnAcceptedOneChangesNothingAndLogsNothing() {
        MemoryStore store = new MemoryStore(Optional.empty());
        CupRestart restart = CupRestart.after(store, STATE);

        try (LogCapture log = LogCapture.of(CupRestart.class)) {
            assertThat(restart.request("alpha_cup", "alpha_cup", false)).isTrue();
            assertThat(restart.request("alpha_cup", "alpha_cup", false)).isFalse();

            assertThat(log.infos()).hasSize(1);
        }
        assertThat(restart.requested()).isTrue();
    }
}
