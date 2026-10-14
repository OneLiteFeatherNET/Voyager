package net.elytrarace.voyager.persistence;

import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Creates an empty database of its own inside a container, so that each test starts from nothing and no test sees
 * another test's rows. The container is shared by one test class; the databases are not.
 */
final class FreshDatabase {

    private static final int POSTGRES_PORT = 5432;

    private FreshDatabase() {
    }

    /**
     * @param container the running PostgreSQL container of the test class
     * @return a JDBC URL of a database that did not exist a moment before
     * @throws SQLException when the database cannot be created
     */
    static String create(PostgreSQLContainer container) throws SQLException {
        String name = "voyager_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection admin = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        return "jdbc:postgresql://%s:%d/%s".formatted(container.getHost(), container.getMappedPort(POSTGRES_PORT), name);
    }
}
