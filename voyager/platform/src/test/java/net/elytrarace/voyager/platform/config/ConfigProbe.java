package net.elytrarace.voyager.platform.config;

import net.elytrarace.voyager.platform.proxy.ProxyForwardingSettings;

/**
 * The child-JVM entry point of {@link ConfigurationPrecedenceTest}. It builds the loader the way the server does, from
 * the real environment, system properties and working directory, and prints what it reads for each key it is given.
 * The key {@value #VELOCITY} asks for the forwarding secret the server would use.
 */
public final class ConfigProbe {

    /** The argument that asks for the velocity secret instead of a plain key. */
    static final String VELOCITY = "@velocity";

    /** The line prefix the test reads; anything else the JVM prints is ignored. */
    static final String LINE_PREFIX = "PROBE\t";

    /** What the probe prints for a key that is not set. */
    static final String ABSENT = "<absent>";

    private ConfigProbe() {
    }

    public static void main(String[] keys) {
        ConfigLoader loader = ConfigLoader.fromJvm();
        for (String key : keys) {
            if (VELOCITY.equals(key)) {
                String secret = ProxyForwardingSettings.fromLoader(loader).secret();
                System.out.println(LINE_PREFIX + key + "\t" + (secret == null ? ABSENT : secret) + "\t-");
            } else {
                String value = loader.get(key);
                System.out.println(LINE_PREFIX + key + "\t" + (value == null ? ABSENT : value) + "\t"
                        + loader.sourceOf(key));
            }
        }
    }
}
