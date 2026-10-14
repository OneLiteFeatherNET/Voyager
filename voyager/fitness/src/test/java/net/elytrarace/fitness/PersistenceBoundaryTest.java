package net.elytrarace.fitness;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import net.elytrarace.voyager.api.config.Secret;

import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * voyager-persistence stores statistics behind the ports of {@code net.elytrarace.voyager.api.stats}. These rules keep it
 * one-way (it knows nothing of the game), keep the JPA entities inside their package, and keep the database password
 * ({@link Secret}) out of every module that does not connect to the database (design D2, D11).
 */
@AnalyzeClasses(packages = "net.elytrarace.voyager", importOptions = ImportOption.DoNotIncludeTests.class)
class PersistenceBoundaryTest {

    private static final String PERSISTENCE = "net.elytrarace.voyager.persistence";
    private static final String ENTITY = PERSISTENCE + ".entity";

    /** An entity is a mapped class; these are the JPA annotations that would make one entity hold another. */
    private static final String[] ASSOCIATION_ANNOTATIONS = {
        "jakarta.persistence.OneToOne",
        "jakarta.persistence.OneToMany",
        "jakarta.persistence.ManyToOne",
        "jakarta.persistence.ManyToMany",
        "jakarta.persistence.JoinColumn",
        "jakarta.persistence.JoinColumns",
        "jakarta.persistence.JoinTable",
        "jakarta.persistence.ElementCollection",
    };

    @ArchTest
    static final ArchRule persistenceDependsOnlyOnApiAndTheStorageLibraries =
            noClasses().that().resideInAPackage(PERSISTENCE + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "net.elytrarace.voyager.race..", "net.elytrarace.voyager.platform..",
                            "net.elytrarace.voyager.server..", "net.elytrarace.voyager.setup..",
                            "net.elytrarace.voyager.physics..")
                    .because("voyager-persistence depends on voyager-api and the storage libraries only; the records it "
                            + "stores are API records, so nothing points back into the game (design D2)")
                    .allowEmptyShould(false);

    @ArchTest
    static final ArchRule entitiesAreMappedClasses =
            classes().that().resideInAPackage(ENTITY + "..")
                    .and().areTopLevelClasses()
                    .and().doNotHaveSimpleName("package-info")
                    .should().beAnnotatedWith("jakarta.persistence.Entity")
                    .andShould().notHaveModifier(JavaModifier.FINAL)
                    .andShould(new ArchCondition<JavaClass>("have a no-argument constructor") {
                        @Override
                        public void check(JavaClass entity, ConditionEvents events) {
                            boolean hasNoArgumentConstructor = entity.getConstructors().stream()
                                    .anyMatch(constructor -> constructor.getRawParameterTypes().isEmpty());
                            if (!hasNoArgumentConstructor) {
                                events.add(SimpleConditionEvent.violated(entity,
                                        "%s has no no-argument constructor, which Hibernate needs".formatted(entity.getName())));
                            }
                        }
                    })
                    .because("Hibernate maps a non-final @Entity with a no-argument constructor; anything else is not a "
                            + "mapping and does not belong in the entity package (design D11)")
                    .allowEmptyShould(false);

    private static final ArchCondition<JavaField> NOT_AN_ASSOCIATION =
            new ArchCondition<>("not carry a JPA association annotation") {
                @Override
                public void check(JavaField field, ConditionEvents events) {
                    for (String annotation : ASSOCIATION_ANNOTATIONS) {
                        if (field.isAnnotatedWith(annotation)) {
                            events.add(SimpleConditionEvent.violated(field,
                                    "%s is annotated with @%s".formatted(field.getFullName(), annotation)));
                        }
                    }
                }
            };

    @ArchTest
    static final ArchRule entitiesHaveNoAssociations =
            fields().that().areDeclaredInClassesThat().resideInAPackage(ENTITY + "..")
                    .should(NOT_AN_ASSOCIATION)
                    .because("an entity holds scalars and its own id; a relation between entities is the schema's foreign "
                            + "key, written by hand in the migration, and the statistics queries read the rows (design D11)")
                    .allowEmptyShould(false);

    /** A method outside the entity package whose signature names an entity type has leaked the mapping to a caller. */
    private static final ArchCondition<JavaMethod> SIGNATURE_WITHOUT_ENTITY =
            new ArchCondition<>("not declare an entity type in its signature") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    boolean exposesEntity = Stream.concat(Stream.of(method.getRawReturnType()),
                                    method.getRawParameterTypes().stream())
                            .anyMatch(type -> type.getPackageName().equals(ENTITY)
                                    || type.getPackageName().startsWith(ENTITY + "."));
                    if (exposesEntity) {
                        events.add(SimpleConditionEvent.violated(method,
                                "%s declares an entity type in its signature".formatted(method.getFullName())));
                    }
                }
            };

    @ArchTest
    static final ArchRule entitiesAreNotLeaked =
            methods().that().arePublic()
                    .and().areDeclaredInClassesThat().resideOutsideOfPackage(ENTITY + "..")
                    .should(SIGNATURE_WITHOUT_ENTITY)
                    .because("callers outside the entity package see the statistics as API records; a public method that "
                            + "returns or takes an entity lets the mapping leak out of voyager-persistence (design D11)")
                    .allowEmptyShould(false);

    /** The calls that read the secret text out of a {@link Secret}: {@code Secret.value()}. */
    @ArchTest
    static final ArchRule secretValueIsConfined =
            noClasses().that().resideOutsideOfPackages(PERSISTENCE + "..", "net.elytrarace.voyager.server..")
                    .should().callMethodWhere(JavaCall.Predicates.target(HasName.Predicates.name("value")
                                    .and(HasOwner.Predicates.With.owner(JavaClass.Predicates.equivalentTo(Secret.class)))))
                    .because("the database password is read only by the storage module and the composition root that "
                            + "wires it; any other class could print or forward it (design D11, ADR-0036)")
                    .allowEmptyShould(false);
}
