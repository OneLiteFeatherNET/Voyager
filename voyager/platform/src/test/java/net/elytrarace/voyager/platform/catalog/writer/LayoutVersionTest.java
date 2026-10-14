package net.elytrarace.voyager.platform.catalog.writer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The layout identity of a map definition: the first sixteen hex characters of SHA-256 over its canonical form.
 * Two definitions that differ in a ring have two layouts; the same definition always has the same one.
 */
class LayoutVersionTest {

    @Test
    void aChangedRingChangesTheLayoutVersion() {
        String before = LayoutVersion.of(CanonicalFixtures.course());
        String after = LayoutVersion.of(CanonicalFixtures.courseWithSecondRingMoved());

        assertThat(after).as("layout version after the second ring moved").isNotEqualTo(before);
    }

    @Test
    void theLayoutVersionIsSixteenLowercaseHexCharacters() {
        String version = LayoutVersion.of(CanonicalFixtures.course());

        assertThat(version).as("layout version").matches("[0-9a-f]{16}");
    }
}
