package net.elytrarace.voyager.persistence;

import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.utility.DockerImageName;

/**
 * Base of every integration test that starts a PostgreSQL container. Its {@link DockerRequirement} is registered
 * before the subclass's Testcontainers extension, so the CI guard applies first (design D10).
 */
@ExtendWith(DockerRequirement.class)
abstract class DockerRequiredIT {

    /** The PostgreSQL image of design D1; the same major version as the production target. */
    static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17-alpine");
}
