package net.elytrarace.fitness;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import com.tngtech.archunit.library.freeze.TextFileBasedViolationStore;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The game loop runs on the server tick and must never wait for a future: a blocking {@code get} or {@code join} on the
 * tick stalls every player for as long as the wait lasts (design D11, D5).
 */
@AnalyzeClasses(packages = "net.elytrarace.voyager", importOptions = ImportOption.DoNotIncludeTests.class)
class TickBoundaryTest {

    /** A call to {@code get} or {@code join} whose target is a {@link Future} or a {@link CompletableFuture}. */
    private static final DescribedPredicate<JavaCall<?>> WAITS_ON_A_FUTURE =
            JavaCall.Predicates.target(HasName.Predicates.name("get").or(HasName.Predicates.name("join")))
                    .and(JavaCall.Predicates.target(HasOwner.Predicates.With.owner(
                            JavaClass.Predicates.assignableTo(Future.class)
                                    .or(JavaClass.Predicates.assignableTo(CompletableFuture.class)))))
                    .as("wait on a future with get or join");

    /**
     * Frozen (the same mechanism as {@link SliceBoundaryRulesTest}): the platform's chunk loading and teleport waits
     * ({@code Arrivals}, {@code MapInstances}, {@code InFlightChunkLoads}, {@code LobbyHologram}) predate this rule and
     * are recorded in the committed baseline. A new blocking wait in race or platform fails the build. Removing one
     * shrinks the baseline locally; the debt is retired by a separate change that moves those waits off the tick.
     */
    @ArchTest
    static final ArchRule noBlockingOnFuturesOnTheTick =
            FreezingArchRule.freeze(noBlockingWaitOnAFuture("net.elytrarace.voyager.race..", "net.elytrarace.voyager.platform.."))
                    .persistIn(new TextFileBasedViolationStore(SliceBoundaryRulesTest::storeFileName));

    /** The unfrozen rule over {@code packages}, so the seeded test can run it on its own seeded classes. */
    static ArchRule noBlockingWaitOnAFuture(String... packages) {
        return noClasses().that()
                .resideInAnyPackage(packages)
                .should().callMethodWhere(WAITS_ON_A_FUTURE)
                .as("classes in race and platform do not wait on a future")
                .because("a blocking wait stalls the tick (design D11)")
                .allowEmptyShould(false);
    }
}
