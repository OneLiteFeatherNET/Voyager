package net.elytrarace.voyager.setup;

import net.elytrarace.voyager.setup.config.SetupSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SetupServerTest {

    @TempDir
    Path root;

    @Test
    void aMissingDataAndWorldsDirectoryAreEachReportedWithTheirAbsolutePath() {
        SetupSettings settings = new SetupSettings("0.0.0.0", 25566, root.resolve("data"), root.resolve("worlds"));

        var problems = SetupServer.directoryProblems(settings);

        assertThat(problems).hasSize(2);
        assertThat(problems.get(0).format()).contains(root.resolve("data").toAbsolutePath().toString());
        assertThat(problems.get(1).format()).contains(root.resolve("worlds").toAbsolutePath().toString());
    }

    @Test
    void onlyTheMissingWorldsDirectoryIsReportedWhenTheDataDirectoryExists() throws Exception {
        Files.createDirectories(root.resolve("data"));
        SetupSettings settings = new SetupSettings("0.0.0.0", 25566, root.resolve("data"), root.resolve("worlds"));

        var problems = SetupServer.directoryProblems(settings);

        assertThat(problems).hasSize(1);
        assertThat(problems.getFirst().format()).contains(root.resolve("worlds").toAbsolutePath().toString());
    }

    @Test
    void noProblemIsReportedWhenBothDirectoriesExist() throws Exception {
        Files.createDirectories(root.resolve("data"));
        Files.createDirectories(root.resolve("worlds"));
        SetupSettings settings = new SetupSettings("0.0.0.0", 25566, root.resolve("data"), root.resolve("worlds"));

        assertThat(SetupServer.directoryProblems(settings)).isEmpty();
    }
}
