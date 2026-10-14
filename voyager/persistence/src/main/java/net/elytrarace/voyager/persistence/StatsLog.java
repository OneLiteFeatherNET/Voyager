package net.elytrarace.voyager.persistence;

/**
 * The warnings and errors the writer reports. The server binds it to its logging facade; tests bind a recorder.
 * A message never contains a credential.
 */
public interface StatsLog {

    /**
     * @param message one line for the operator
     */
    void warn(String message);

    /**
     * @param message one line for the operator
     */
    void error(String message);
}
