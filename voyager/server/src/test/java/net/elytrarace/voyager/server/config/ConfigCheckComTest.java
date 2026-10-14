package net.elytrarace.voyager.server.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The computer racer lines of the configuration check: the boot line that states the racers when they are on, and the
 * refusal of a count that cannot be used, keyed under its setting. Each test passes its own properties map, so nothing
 * reads the JVM's system properties.
 */
class ConfigCheckComTest {

    @TempDir
    Path root;

    @Test
    void aBootLineStatesTheCountAndDifficultyWhenEnabled() throws IOException {
        ServerSettings settings = settingsWith(Map.of(
                ServerSettings.COM_COUNT_PROPERTY, "5",
                ServerSettings.COM_DIFFICULTY_PROPERTY, "HARD"));

        assertThat(ConfigCheck.comBootLine(settings)).hasValue("computer racers: 5 at HARD");
    }

    @Test
    void noBootLineIsLoggedWhenTheCountIsZero() throws IOException {
        ServerSettings settings = settingsWith(Map.of(ServerSettings.COM_COUNT_PROPERTY, "0"));

        assertThat(ConfigCheck.comBootLine(settings)).isEmpty();
    }

    @Test
    void aCountThatCannotBeUsedIsReportedUnderItsSettingNamingTheRange() throws IOException {
        Map<String, String> properties = validDirectories();
        properties.put(ServerSettings.COM_COUNT_PROPERTY, "16");

        assertThat(ConfigCheck.settingsProblems(new String[0], properties)).singleElement().satisfies(problem -> {
            assertThat(problem.key()).isEqualTo(ServerSettings.COM_COUNT_PROPERTY);
            assertThat(problem.message()).contains("0 to 15");
        });
    }

    private ServerSettings settingsWith(Map<String, String> extra) throws IOException {
        Map<String, String> properties = validDirectories();
        properties.putAll(extra);
        return ServerSettings.fromProperties(new String[0], properties::get);
    }

    private Map<String, String> validDirectories() throws IOException {
        Path data = Files.createDirectories(root.resolve("catalog"));
        Path worlds = Files.createDirectories(root.resolve("worlds"));
        Map<String, String> properties = new HashMap<>();
        properties.put(ServerSettings.DATA_PATH_PROPERTY, data.toString());
        properties.put(ServerSettings.WORLDS_PATH_PROPERTY, worlds.toString());
        return properties;
    }
}
