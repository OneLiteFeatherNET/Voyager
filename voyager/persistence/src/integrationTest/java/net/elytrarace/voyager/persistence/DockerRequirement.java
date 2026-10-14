package net.elytrarace.voyager.persistence;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Turns a missing Docker daemon into a failure when the Gradle property {@code voyager.requireDocker} is {@code true}.
 *
 * <p>Testcontainers' {@code disabledWithoutDocker} skips a class silently. This condition is registered by the
 * superclass, so JUnit evaluates it before the subclass's Testcontainers condition: with the property set and no
 * daemon, the class fails here and is never skipped. With the property unset, the condition enables the class and
 * the Testcontainers condition decides, so a local run without Docker skips.
 */
final class DockerRequirement implements ExecutionCondition {

    static final String PROPERTY = "voyager.requireDocker";

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (!Boolean.getBoolean(PROPERTY)) {
            return ConditionEvaluationResult.enabled("%s is not true, so Testcontainers decides".formatted(PROPERTY));
        }
        String message = "%s is true but no Docker daemon is reachable; the integration suite must fail, not skip"
                .formatted(PROPERTY);
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException probeFailure) {
            // Testcontainers can fail while probing (a broken socket, a container it cannot start) instead of answering no.
            throw new IllegalStateException(message, probeFailure);
        }
        if (available) {
            return ConditionEvaluationResult.enabled("%s is true and Docker is available".formatted(PROPERTY));
        }
        throw new IllegalStateException(message);
    }
}
