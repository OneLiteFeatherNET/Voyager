package net.elytrarace.voyager.cloudnet.bridge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The extension descriptor is what the Minestom loader reads. {@code @ExtensionInfo} has source retention, so the
 * annotation cannot be read by reflection at run time; the processor writes {@code extension.json} into the module's
 * classes instead, and that file is what this test reads.
 */
class VoyagerCloudNetExtensionTest {

    @Test
    void theDescriptorDeclaresTheCloudNetBridgeAsADependency() throws IOException {
        String descriptor = readDescriptor();

        assertThat(descriptor)
                .as("without CloudNet_Bridge among the dependencies, the loader does not load this extension after the bridge")
                .contains("\"dependencies\"")
                .contains("\"CloudNet_Bridge\"");
    }

    @Test
    void theDescriptorNamesTheExtension() throws IOException {
        String descriptor = readDescriptor();

        assertThat(descriptor).contains("\"name\"").contains("\"VoyagerCloudNetPermissions\"");
    }

    private static String readDescriptor() throws IOException {
        try (InputStream in = VoyagerCloudNetExtensionTest.class.getClassLoader().getResourceAsStream("extension.json")) {
            assertThat(in)
                    .as("the minestom-extensions processor must write extension.json into the module's classes")
                    .isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
