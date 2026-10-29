package net.elytrarace.voyager.setup.mapsetup;

import net.elytrarace.voyager.setup.mapsetup.EditClaims.Holder;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The claim registry: one holder per key, held in memory, with a fresh registry for every test. */
class EditClaimsTest {

    private static final UUID ALPHA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BETA = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID GAMMA = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final String SKYFORTRESS = "map:skyfortress";

    @Test
    void aFreeKeyIsGrantedToTheBuilder() {
        EditClaims claims = new EditClaims();

        assertThat(claims.claim(SKYFORTRESS, ALPHA, "Alpha")).isEmpty();
        assertThat(claims.holder(SKYFORTRESS)).contains(new Holder(ALPHA, "Alpha"));
    }

    @Test
    void aSecondBuilderIsRefusedWithTheFirstHoldersName() {
        EditClaims claims = new EditClaims();
        claims.claim(SKYFORTRESS, ALPHA, "Alpha");

        assertThat(claims.claim(SKYFORTRESS, BETA, "Beta")).contains(new Holder(ALPHA, "Alpha"));
        assertThat(claims.holder(SKYFORTRESS)).contains(new Holder(ALPHA, "Alpha"));
    }

    @Test
    void theSameBuildersSecondClaimIsGrantedAndKeepsTheNameRecordedAtTheFirst() {
        EditClaims claims = new EditClaims();
        claims.claim(SKYFORTRESS, ALPHA, "Alpha");

        assertThat(claims.claim(SKYFORTRESS, ALPHA, "Alpha renamed")).isEmpty();
        assertThat(claims.holder(SKYFORTRESS)).contains(new Holder(ALPHA, "Alpha"));
    }

    @Test
    void aHolderReleaseFreesTheKeyForAnotherBuilder() {
        EditClaims claims = new EditClaims();
        claims.claim(SKYFORTRESS, ALPHA, "Alpha");

        claims.release(SKYFORTRESS, ALPHA);

        assertThat(claims.holder(SKYFORTRESS)).isEmpty();
        assertThat(claims.claim(SKYFORTRESS, BETA, "Beta")).isEmpty();
    }

    @Test
    void aReleaseByABuilderWhoDoesNotHoldTheKeyChangesNothing() {
        EditClaims claims = new EditClaims();
        claims.claim(SKYFORTRESS, ALPHA, "Alpha");

        claims.release(SKYFORTRESS, BETA);

        assertThat(claims.holder(SKYFORTRESS)).contains(new Holder(ALPHA, "Alpha"));
    }

    @Test
    void releasingAllKeysOfOneBuilderFreesOnlyThatBuildersKeys() {
        EditClaims claims = new EditClaims();
        claims.claim("map:skyfortress", ALPHA, "Alpha");
        claims.claim("map:canyon", ALPHA, "Alpha");
        claims.claim("map:ridge", BETA, "Beta");

        claims.releaseAll(ALPHA);

        assertThat(claims.holder("map:skyfortress")).isEmpty();
        assertThat(claims.holder("map:canyon")).isEmpty();
        assertThat(claims.holder("map:ridge")).contains(new Holder(BETA, "Beta"));
    }

    @Test
    void theMapKeyAndTheCupKeyOfOneIdAreIndependentClaims() {
        EditClaims claims = new EditClaims();

        assertThat(claims.claim("map:alpha", ALPHA, "Alpha")).isEmpty();
        assertThat(claims.claim("cup:alpha", BETA, "Beta")).isEmpty();
        assertThat(claims.holder("map:alpha")).contains(new Holder(ALPHA, "Alpha"));
        assertThat(claims.holder("cup:alpha")).contains(new Holder(BETA, "Beta"));
    }

    @Test
    void unlockFreesAHeldKeyWhoeverHoldsItAndReturnsTheHolder() {
        EditClaims claims = new EditClaims();
        claims.claim(SKYFORTRESS, ALPHA, "Alpha");

        assertThat(claims.unlock(SKYFORTRESS)).contains(new Holder(ALPHA, "Alpha"));
        assertThat(claims.holder(SKYFORTRESS)).isEmpty();
        assertThat(claims.claim(SKYFORTRESS, GAMMA, "Gamma")).isEmpty();
    }

    @Test
    void unlockOfAFreeKeyReturnsNothing() {
        EditClaims claims = new EditClaims();

        assertThat(claims.unlock(SKYFORTRESS)).isEmpty();
        assertThat(claims.holder(SKYFORTRESS)).isEmpty();
    }
}
