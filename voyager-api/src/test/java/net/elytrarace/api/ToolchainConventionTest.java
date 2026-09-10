package net.elytrarace.api;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ToolchainConventionTest {

    private static final int JAVA_25_CLASS_FILE_MAJOR = 69;

    @Test
    void classesAreCompiledForJava25() throws Exception {
        String resource = "/%s.class".formatted(ToolchainConventionTest.class.getName().replace('.', '/'));
        try (InputStream in = ToolchainConventionTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("compiled class file must be on the test classpath").isNotNull();
            DataInputStream data = new DataInputStream(in);
            assertThat(data.readInt()).as("class file magic").isEqualTo(0xCAFEBABE);
            data.readUnsignedShort(); // minor version, unused
            assertThat(data.readUnsignedShort())
                    .as("class file major version — 69 is Java 25")
                    .isEqualTo(JAVA_25_CLASS_FILE_MAJOR);
        }
    }
}
