package net.elytrarace.voyager.api.handoff;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one slot the bridge fills and the server reads. The slot is process-wide, so every test clears it when it ends and
 * no test relies on what an earlier one installed.
 */
class PlayerHandoffsTest {

    @AfterEach
    void clearTheSlot() {
        PlayerHandoffs.SLOT.uninstall();
    }

    @Test
    void anEmptyHolderAnswersNoHandoff() {
        assertThat(PlayerHandoffs.SLOT.current()).isEmpty();
    }

    @Test
    void anInstalledHandoffIsTheOneReturned() {
        PlayerHandoff handoff = recorder(new ArrayList<>());

        PlayerHandoffs.SLOT.install(handoff);

        assertThat(PlayerHandoffs.SLOT.current()).containsSame(handoff);
    }

    @Test
    void aLaterInstallReplacesTheEarlierOne() {
        PlayerHandoff first = recorder(new ArrayList<>());
        PlayerHandoff second = recorder(new ArrayList<>());
        PlayerHandoffs.SLOT.install(first);

        PlayerHandoffs.SLOT.install(second);

        assertThat(PlayerHandoffs.SLOT.current()).containsSame(second);
    }

    @Test
    void uninstallClearsTheHolder() {
        PlayerHandoffs.SLOT.install(recorder(new ArrayList<>()));

        PlayerHandoffs.SLOT.uninstall();

        assertThat(PlayerHandoffs.SLOT.current()).isEmpty();
    }

    private static PlayerHandoff recorder(List<UUID> sent) {
        return sent::add;
    }
}
