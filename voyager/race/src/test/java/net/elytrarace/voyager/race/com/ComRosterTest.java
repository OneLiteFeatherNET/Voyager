package net.elytrarace.voyager.race.com;

import net.elytrarace.voyager.race.com.exception.InvalidComCountException;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The computer seats that fill a grid to its target count.
 *
 * <p>The planning is a pure function of its arguments, so each test calls {@link ComRoster#plan} directly with
 * a fixed seed. No course, no server and no clock are involved.
 */
class ComRosterTest {

    private static final long SEED = 42L;

    @Test
    void theGridFillsToTheTargetCount() {
        List<ComSeat> seats = ComRoster.plan(5, 2, SEED);

        assertThat(seats)
                .as("the computer seats that fill a grid of 5 with 2 humans already on it")
                .hasSize(3);
    }

    @Test
    void humansAlreadyOnTheGridReduceTheCount() {
        List<ComSeat> seats = ComRoster.plan(5, 4, SEED);

        assertThat(seats)
                .as("the computer seats left for a grid of 5 with 4 humans already on it")
                .hasSize(1);
    }

    @Test
    void theSameSeedGivesTheSameSeats() {
        List<ComSeat> first = ComRoster.plan(5, 2, SEED);
        List<ComSeat> second = ComRoster.plan(5, 2, SEED);

        assertThat(second)
                .as("the seats planned twice from the same seed %s".formatted(SEED))
                .isEqualTo(first);
    }

    @Test
    void seatsAreNumberedComOneToComNInOrder() {
        List<ComSeat> seats = ComRoster.plan(3, 0, SEED);

        assertThat(seats)
                .as("the names of three computer seats in grid order")
                .extracting(ComSeat::name)
                .containsExactly("COM 1", "COM 2", "COM 3");
    }

    @Test
    void aCountAbove15IsRefused() {
        assertThatThrownBy(() -> ComRoster.plan(16, 0, SEED))
                .as("planning 16 computer racers, one above the maximum of 15")
                .isInstanceOf(InvalidComCountException.class)
                .hasMessageContaining("16");
    }
}
