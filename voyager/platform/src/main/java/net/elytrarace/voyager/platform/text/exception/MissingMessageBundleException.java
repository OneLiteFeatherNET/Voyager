package net.elytrarace.voyager.platform.text.exception;

/**
 * Thrown when the message bundle is not on the classpath at all.
 *
 * <p>A server that cannot find it has nothing to say to anybody: every line a racer would see is a
 * translation key, so the failure would present as a game that works and speaks in dotted
 * identifiers. It is a refusal at boot instead, naming the resource it looked for.
 */
public final class MissingMessageBundleException extends RuntimeException {

    public MissingMessageBundleException(String resource) {
        super(("no message bundle at '%s' on the classpath — without it every line a player sees is "
                + "a raw translation key").formatted(resource));
    }
}
