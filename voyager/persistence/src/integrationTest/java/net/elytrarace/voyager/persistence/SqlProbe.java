package net.elytrarace.voyager.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads rows back over plain JDBC, so an assertion sees what the database holds and not what the store
 * reports about itself.
 */
final class SqlProbe {

    private SqlProbe() {
    }

    /**
     * @param connection a connection of the test's database
     * @param table      the qualified table name, for example {@code voyager.race_session}
     * @return the number of rows in the table
     * @throws SQLException when the query fails
     */
    static long count(Connection connection, String table) throws SQLException {
        return (Long) one(connection, "SELECT count(*) FROM " + table);
    }

    /**
     * @param connection a connection of the test's database
     * @param sql        a query returning one value
     * @param parameters the bound parameters, in order
     * @return the value of the first column of the first row; {@code null} when the value is null
     * @throws SQLException when the query fails or returns no row
     */
    static Object one(Connection connection, String sql, Object... parameters) throws SQLException {
        List<List<Object>> rows = rows(connection, sql, parameters);
        if (rows.isEmpty()) {
            throw new SQLException("the query returned no row: " + sql);
        }
        return rows.getFirst().getFirst();
    }

    /**
     * @param connection a connection of the test's database
     * @param sql        a query
     * @param parameters the bound parameters, in order
     * @return every row, each as the list of its column values
     * @throws SQLException when the query fails
     */
    static List<List<Object>> rows(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                List<List<Object>> rows = new ArrayList<>();
                while (resultSet.next()) {
                    List<Object> row = new ArrayList<>(metadata.getColumnCount());
                    for (int column = 1; column <= metadata.getColumnCount(); column++) {
                        row.add(resultSet.getObject(column));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }
}
