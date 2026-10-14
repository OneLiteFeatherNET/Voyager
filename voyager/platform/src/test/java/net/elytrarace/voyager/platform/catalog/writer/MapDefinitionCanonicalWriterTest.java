package net.elytrarace.voyager.platform.catalog.writer;

import net.elytrarace.voyager.api.race.MapDefinition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The canonical form of a map definition, with no file and no server. The form is what the layout version is hashed
 * from, so it has to be the same bytes every time the same definition is written.
 */
class MapDefinitionCanonicalWriterTest {

    @Test
    void theCanonicalBytesOfOneDefinitionDoNotChangeBetweenTwoWrites() {
        MapDefinition course = CanonicalFixtures.course();

        String first = MapDefinitionCanonicalWriter.toCanonicalJson(course);
        String second = MapDefinitionCanonicalWriter.toCanonicalJson(course);

        assertThat(second).as("canonical form of the second write").isEqualTo(first);
    }
}
