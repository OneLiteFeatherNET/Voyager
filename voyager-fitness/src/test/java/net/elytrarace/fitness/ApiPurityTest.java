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

import net.minestom.server.coordinate.Vec;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * voyager-api is the module every other module depends on, so anything it drags in is dragged in
 * everywhere. These rules are the reason the dependency direction in the module graph holds.
 */
@AnalyzeClasses(packages = "net.elytrarace.voyager", importOptions = ImportOption.DoNotIncludeTests.class)
class ApiPurityTest {

    @ArchTest
    static final ArchRule apiDoesNotDependOnMinestomOrItsWorldLoader =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.api..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("net.minestom..", "net.onelitefeather.falco..")
                    .because("Minestom types and the Falco world loader that reads region files for "
                            + "it are both platform detail and belong to voyager-platform alone")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotDependOnPaper =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.bukkit..")
                    .because("Paper is dropped entirely by the rebuild")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotDependOnPersistenceTechnology =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
                    .because("ports live in the api module, ORM technology does not")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotDependOnADiContainer =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.google.inject..", "jakarta.inject..")
                    .because("DI annotations are confined to the composition roots")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotPerformFileIo =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("java.nio.file..")
                    .because("voyager-api declares configuration types and never loads them")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule physicsDoesNotDependOnMinestomOrItsWorldLoader =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.physics..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("net.minestom..", "net.onelitefeather.falco..")
                    .because("Minestom types and the Falco world loader that reads region files for "
                            + "it are both platform detail and belong to voyager-platform alone")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule physicsDoesNotDependOnPaper =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.physics..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.bukkit..")
                    .because("Paper is dropped entirely by the rebuild")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule physicsDoesNotDependOnPersistenceTechnology =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.physics..")
                    .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
                    .because("the physics module simulates flight, ORM technology does not belong here")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule physicsDoesNotDependOnADiContainer =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.physics..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.google.inject..", "jakarta.inject..")
                    .because("DI annotations are confined to the composition roots")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule physicsDoesNotPerformFileIo =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.physics..")
                    .should().dependOnClassesThat().resideInAnyPackage("java.nio.file..")
                    .because("the physics module has no configuration of any kind and never loads files")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule physicsDoesNotDependOnRaceOrPlatform =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.physics..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("net.elytrarace.voyager.race..", "net.elytrarace.voyager.platform..")
                    .because("physics is a pure simulation core that race and platform depend on, not vice versa")
                    .allowEmptyShould(false);

    // voyager-race is the module whose defining property is that a whole race runs without a server,
    // and until these three rules landed nothing enforced it: race appeared in this file only as a
    // forbidden target of physics, never as a subject. Xerus is named explicitly because the spec
    // names it ("Xerus is Minestom-bound and therefore may not appear in voyager-race") and because
    // E4 is the stage that first puts it on a neighbouring module's classpath, which is exactly when
    // an unenforced prohibition turns into an argument.
    @ArchTest
    static final ArchRule raceDoesNotDependOnMinestomXerusOrItsWorldLoader =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.race..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("net.minestom..", "net.theevilreaper.xerus..",
                            "net.onelitefeather.falco..")
                    .because("a whole race has to play out in JUnit; Minestom, the Minestom-bound "
                            + "Xerus phase system and the Falco world loader that reads a racetrack "
                            + "off disk are all platform detail and belong to voyager-platform alone")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule raceDoesNotDependOnPaper =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.race..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.bukkit..")
                    .because("Paper is dropped entirely by the rebuild")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule raceDoesNotDependOnPersistenceTechnology =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.race..")
                    .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
                    .because("the race domain scores a run; storing one is voyager-persistence's job")
                    .allowEmptyShould(false);

    // E4 puts voyager-platform and voyager-server on this module's classpath for the first time.
    // They are the two modules where a stray import does the most damage, because they are the only
    // ones with a platform to reach for — so they land with purity rules already in place, not added
    // after the fact in the final review the way voyager-race's were in E3. The existing rules above
    // already forbid Minestom for voyager-api and voyager-physics, and forbid Minestom-and-Xerus for
    // voyager-race, and physicsDoesNotDependOnRaceOrPlatform already forbids voyager-platform for
    // voyager-physics — the six rules below close the remaining gaps: Xerus for voyager-api and
    // voyager-physics, voyager-platform for voyager-api and voyager-race, voyager-server for
    // voyager-platform, and the DI container for every module except voyager-server.

    @ArchTest
    static final ArchRule apiDoesNotDependOnXerus =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("net.theevilreaper.xerus..")
                    .because("Xerus is Minestom-bound and belongs to voyager-platform alone")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule apiDoesNotDependOnPlatform =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.api..")
                    .should().dependOnClassesThat().resideInAnyPackage("net.elytrarace.voyager.platform..")
                    .because("voyager-api is the module every other module depends on; a dependency "
                            + "back on voyager-platform would put a platform type into every module's graph")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule physicsDoesNotDependOnXerus =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.physics..")
                    .should().dependOnClassesThat().resideInAnyPackage("net.theevilreaper.xerus..")
                    .because("Xerus is Minestom-bound and belongs to voyager-platform alone")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule raceDoesNotDependOnPlatform =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.race..")
                    .should().dependOnClassesThat().resideInAnyPackage("net.elytrarace.voyager.platform..")
                    .because("a whole race has to play out in JUnit; voyager-platform depends on "
                            + "voyager-race, not the other way around")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule platformDoesNotDependOnServer =
            noClasses().that().resideInAPackage("net.elytrarace.voyager.platform..")
                    .should().dependOnClassesThat().resideInAnyPackage("net.elytrarace.voyager.server..")
                    .because("voyager-server is the composition root that depends on voyager-platform, "
                            + "not the other way around")
                    .allowEmptyShould(false);

    // Minestom's Shape interface does not expose the boxes a shape is made of; only the ShapeImpl
    // record does, so reading them needs a cast to an implementation type. That cast is confined to
    // BlockShapes so a Minestom upgrade that changes the runtime type breaks in one place — a
    // constraint the brief for this stage states in prose, which is the kind of statement that
    // quietly stops being true. Now it cannot: a second class reaching for ShapeImpl turns this red.
    @ArchTest
    static final ArchRule onlyBlockShapesReachesForMinestomsShapeImplementation =
            noClasses().that().resideInAPackage("net.elytrarace.voyager..")
                    .and().doNotHaveFullyQualifiedName("net.elytrarace.voyager.platform.collision.BlockShapes")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("net.minestom.server.collision.ShapeImpl")
                    .because("the one cast past Minestom's Shape interface lives in BlockShapes alone")
                    .allowEmptyShould(false);

    // Task 2 left this as a finding for whoever wired the first tick loop: the design's single
    // velocity exit was stated in prose only. "One place to look when a velocity turns up that
    // should not have" is worth nothing if a second place can be added without the build noticing,
    // and E4's tick driver is where the temptation first appears — it is the first code that holds a
    // per-tick velocity for a live player. Matched by call target name and owner package rather than
    // by the declared type at the call site: setVelocity is inherited from Entity, so a caller
    // holding an Entity or a LivingEntity emits a different owner and would walk past a rule pinned
    // to Player.
    private static final DescribedPredicate<JavaCall<?>> SET_VELOCITY_ON_A_MINESTOM_TYPE =
            JavaCall.Predicates.target(HasName.Predicates.name("setVelocity"))
                    .and(JavaCall.Predicates.target(HasOwner.Predicates.With.owner(
                            JavaClass.Predicates.resideInAPackage("net.minestom.."))))
                    .as("call setVelocity on a Minestom type");

    @ArchTest
    static final ArchRule onlyVelocityExitSendsAVelocityToMinestom =
            noClasses().that().resideInAPackage("net.elytrarace.voyager..")
                    .and().doNotHaveFullyQualifiedName("net.elytrarace.voyager.platform.convert.VelocityExit")
                    .should().callMethodWhere(SET_VELOCITY_ON_A_MINESTOM_TYPE)
                    .because("normal elytra flight is client-authoritative and the server simulates "
                            + "silently alongside it; a velocity reaches Minestom only for a firework "
                            + "boost, a ring BOOST/SLOW effect and an out-of-bounds reset, and all "
                            + "three go through net.elytrarace.voyager.platform.convert.VelocityExit")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule onlyVectorsBuildsAMinestomVectorFromDomainCoordinates =
            noClasses().that().resideInAPackage("net.elytrarace.voyager..")
                    .and().doNotHaveFullyQualifiedName("net.elytrarace.voyager.platform.convert.Vectors")
                    .should().callConstructor(Vec.class, double.class, double.class, double.class)
                    .because("the Vec3 <-> Minestom boundary is one class, so a value that crossed it "
                            + "without the finiteness re-assertion — or with a unit conversion the "
                            + "coordinate path must not carry — has exactly one place to have come from")
                    .allowEmptyShould(false);

    // io.airlift:guice keeps upstream Guice's com.google.inject package name (see the greenfield
    // design, D10), so one rule scoped to that package plus io.airlift.. catches either artifact.
    // Written as "everything outside voyager-server" rather than naming each domain module
    // individually, so a future rebuild module is covered by construction instead of needing its own
    // DI-purity rule remembered on top.
    @ArchTest
    static final ArchRule onlyServerDependsOnDiContainer =
            noClasses().that().resideInAPackage("net.elytrarace.voyager..")
                    .and().resideOutsideOfPackage("net.elytrarace.voyager.server..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.google.inject..", "io.airlift..")
                    .because("DI annotations are confined to the composition roots")
                    .allowEmptyShould(false);
}
