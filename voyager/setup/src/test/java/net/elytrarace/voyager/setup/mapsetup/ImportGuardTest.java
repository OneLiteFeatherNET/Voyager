package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.api.mapsetup.MapId;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportGuardTest {

    private final MapId skycube = new MapId("skycube");
    private final MapId skyfortress = new MapId("skyfortress");

    @Test
    void aSecondClaimOfAnIdIsRefusedWhileTheFirstIsHeld() {
        ImportGuard guard = new ImportGuard();
        assertThat(guard.claim(skycube)).isTrue();

        assertThat(guard.claim(skycube)).as("the id is still held by the first import").isFalse();
    }

    @Test
    void releasingAnIdFreesIt() {
        ImportGuard guard = new ImportGuard();
        guard.claim(skycube);

        guard.release(skycube);

        assertThat(guard.isClaimed(skycube)).isFalse();
        assertThat(guard.claim(skycube)).isTrue();
    }

    @Test
    void twoIdsDoNotAffectEachOther() {
        ImportGuard guard = new ImportGuard();
        guard.claim(skycube);

        assertThat(guard.claim(skyfortress)).isTrue();
        assertThat(guard.isClaimed(skycube)).isTrue();
    }
}
