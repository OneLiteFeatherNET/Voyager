package net.elytrarace.voyager.persistence;

import com.github.dockerjava.api.model.PortBinding;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

/**
 * Takes a PostgreSQL container down and brings it back for an outage test.
 *
 * <p>{@link PostgreSQLContainer#stop()} removes the container, data included, so a restarted container would be empty.
 * The outage is therefore a plain Docker stop and start of the same container, which keeps its data volume. The host
 * port is fixed for the test: a restart may map a new random port, and the connection pool keeps the URL it was built
 * with. The readiness wait after the start is the library's own shell strategy.
 */
final class ContainerOutage {

    private static final int CONTAINER_PORT = 5432;

    private ContainerOutage() {
    }

    /**
     * @param password the superuser password of the container; the outage tests check that it never reaches a log
     * @return a PostgreSQL 17 container that is not started yet, on a host port chosen for this test
     */
    static PostgreSQLContainer postgres(String password) {
        int hostPort = freeHostPort();
        return new PostgreSQLContainer(DockerRequiredIT.POSTGRES_IMAGE)
                .withPassword(password)
                .withCreateContainerCmdModifier(command -> command.withHostConfig(
                        command.getHostConfig().withPortBindings(
                                PortBinding.parse(hostPort + ":" + CONTAINER_PORT))));
    }

    /**
     * Stops the container without removing it. Connections fail from now on.
     *
     * @param container a started container
     */
    static void stop(PostgreSQLContainer container) {
        container.getDockerClient().stopContainerCmd(container.getContainerId()).exec();
    }

    /**
     * Starts a stopped container again and waits until PostgreSQL answers.
     *
     * @param container a container stopped by {@link #stop(PostgreSQLContainer)}
     * @param user      the role the readiness check connects as
     */
    static void start(PostgreSQLContainer container, String user) {
        container.getDockerClient().startContainerCmd(container.getContainerId()).exec();
        Wait.forSuccessfulCommand("pg_isready -h 127.0.0.1 -U %s".formatted(user)).waitUntilReady(container);
    }

    private static int freeHostPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException failure) {
            throw new UncheckedIOException("no free host port for the test container", failure);
        }
    }
}
