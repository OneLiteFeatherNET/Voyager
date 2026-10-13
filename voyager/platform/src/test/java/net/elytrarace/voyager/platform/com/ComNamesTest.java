package net.elytrarace.voyager.platform.com;

import net.elytrarace.voyager.race.com.ComRoster;
import net.elytrarace.voyager.race.com.ComSeat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ComNamesTest {

    private static final long ROUND_SEED = 7L;

    @Test
    void seatsAreNamedComOneToComN() {
        List<ComSeat> seats = ComRoster.plan(3, 0, ROUND_SEED);

        List<String> names = seats.stream().map(ComNames::nameOf).toList();

        assertThat(names)
                .as("three computer seats with no humans, named in seat order")
                .containsExactly("COM 1", "COM 2", "COM 3");
    }

    @Test
    void aNameIsKeptForTheLifeOfTheCup() {
        ComSeat seatWhenCupStarts = ComRoster.plan(3, 0, ROUND_SEED).get(1);
        ComSeat seatAfterTopUp = ComRoster.plan(5, 2, ROUND_SEED).get(1);

        assertThat(ComNames.nameOf(seatAfterTopUp))
                .as("seat index 1 keeps its name when humans join and the grid is topped up")
                .isEqualTo(ComNames.nameOf(seatWhenCupStarts))
                .isEqualTo("COM 2");
    }

    @Test
    void aHumansIdIsNotAComputerRacer() {
        UUID human = UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8));

        assertThat(ComNames.isComputer(human))
                .as("an offline-mode player id must not be read as a computer racer")
                .isFalse();
    }

    @Test
    void aComputerSeatsIdIsRecognised() {
        ComSeat seat = ComRoster.plan(1, 0, ROUND_SEED).getFirst();

        assertThat(ComNames.isComputer(ComNames.idOf(seat)))
                .as("the id derived for a computer seat is flagged as a computer racer")
                .isTrue();
    }
}
