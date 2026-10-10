package net.elytrarace.voyager.platform.lifecycle;

import net.elytrarace.voyager.api.permission.PermissionPolicy;
import net.elytrarace.voyager.api.permission.PermissionSubject;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@EnvTest
class StopCommandTest {

    /** Answers one node for every player, and records who was asked. */
    private static PermissionPolicy answering(boolean allowed, List<PermissionSubject> asked) {
        return (subject, node) -> {
            asked.add(subject);
            return allowed;
        };
    }

    private static ServiceShutdown countingShutdown(AtomicInteger stops) {
        return new ServiceShutdown(stops::incrementAndGet, Runnable::run);
    }

    @Test
    void aPlayerWithoutTheNodeDoesNotStopTheServer(Env env) {
        Player player = env.createPlayer(env.createFlatInstance(), new Pos(0, 40, 0));
        AtomicInteger stops = new AtomicInteger();
        List<PermissionSubject> asked = new ArrayList<>();
        StopCommand command = new StopCommand(answering(false, asked), countingShutdown(stops));

        command.execute(player);

        assertThat(stops).as("a denied player must not stop the server").hasValue(0);
        assertThat(asked).singleElement().satisfies(subject ->
                assertThat(((PermissionSubject.Player) subject).id()).isEqualTo(player.getUuid()));
    }

    @Test
    void aPlayerWithTheNodeStopsTheServer(Env env) {
        Player player = env.createPlayer(env.createFlatInstance(), new Pos(0, 40, 0));
        AtomicInteger stops = new AtomicInteger();
        StopCommand command = new StopCommand(answering(true, new ArrayList<>()), countingShutdown(stops));

        command.execute(player);

        assertThat(stops).hasValue(1);
    }

    @Test
    void theConsoleStopsTheServerWhateverThePolicySays() {
        CommandSender console = MinecraftServer.getCommandManager().getConsoleSender();
        AtomicInteger stops = new AtomicInteger();
        List<PermissionSubject> asked = new ArrayList<>();
        StopCommand command = new StopCommand(answering(false, asked), countingShutdown(stops));

        command.execute(console);

        assertThat(stops).hasValue(1);
        assertThat(asked).as("the console is not asked the policy").isEmpty();
    }
}
