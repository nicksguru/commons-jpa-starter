package guru.nicks.commons.cucumber;

import guru.nicks.commons.cucumber.world.TextWorld;
import guru.nicks.commons.jpa.generator.UuidV7Generator;
import guru.nicks.commons.jpa.generator.UuidV7GeneratorImpl;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.EventType;
import org.hibernate.id.factory.spi.CustomIdGeneratorCreationContext;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Step definitions for testing {@link UuidV7GeneratorImpl}.
 */
@RequiredArgsConstructor
public class UuidV7GeneratorSteps {

    // DI
    private final TextWorld textWorld;
    private final TextCommonSteps textCommonSteps; // Assuming common steps like exception checks exist

    @Mock
    private UuidV7Generator annotation; // Mocked annotation
    @Mock
    private CustomIdGeneratorCreationContext context; // Mocked context
    @Mock
    private SharedSessionContractImplementor session; // Mocked session
    private AutoCloseable closeableMocks;

    private Member idMember;
    private Class<?> entityClass;
    private String fieldName;
    private UuidV7GeneratorImpl generator;
    private Object entityInstance;
    private Object generatedId;
    private UUID existingUuid;
    private EnumSet<EventType> eventTypes;

    @Before
    public void beforeEachScenario() {
        closeableMocks = MockitoAnnotations.openMocks(this);
    }

    @After
    public void afterEachScenario() throws Exception {
        if (closeableMocks != null) {
            closeableMocks.close();
        }
    }

    @Given("a UuidV7Generator is configured for the {string} field of {string}")
    public void aUuidV7GeneratorIsConfiguredForTheFieldOf(String field, String className) throws Exception {
        fieldName = field;

        // construct the full class name assuming it's within this step definition class for simplicity
        String fullClassName = UuidV7GeneratorSteps.class.getName() + "$" + className;
        entityClass = getClass().getClassLoader().loadClass(fullClassName);
        idMember = entityClass.getDeclaredField(fieldName);
    }

    @Given("the entity has no ID set")
    public void theEntityHasNoIdSet() throws Exception {
        // instantiate correct entity type
        entityInstance = entityClass.getDeclaredConstructor().newInstance();

        // ensure ID is null
        Field idField = entityClass.getDeclaredField(fieldName);
        idField.setAccessible(true);
        idField.set(entityInstance, null);
    }

    @Given("the entity has an existing ID set")
    public void theEntityHasAnExistingIdSet() throws Exception {
        existingUuid = UUID.randomUUID();
        // instantiate correct entity type
        entityInstance = entityClass.getDeclaredConstructor().newInstance();

        // set ID
        Field idField = entityClass.getDeclaredField(fieldName);
        idField.setAccessible(true);
        idField.set(entityInstance, existingUuid);
    }

    @Given("the entity ID getter throws an exception")
    public void theEntityIdGetterThrowsAnException() throws Exception {
        // instantiate the generator first (requires valid setup)
        idMember = TestEntityWithUuid.class.getDeclaredField("id");
        generator = new UuidV7GeneratorImpl(annotation, idMember, context);

        entityClass = TestEntityWithUuid.class;
        entityInstance = entityClass.getDeclaredConstructor().newInstance();

        // mock the Method object itself
        Method mockedGetter = mock(Method.class);
        when(mockedGetter.getName())
                .thenReturn("getId");
        when(mockedGetter.invoke(entityInstance))
                .thenThrow(new InvocationTargetException(new RuntimeException("Getter failed")));

        // use reflection to replace the generator's 'getter' field with our mock
        Field generatorGetterField = generator.getClass().getDeclaredField("getter");
        generatorGetterField.setAccessible(true);
        generatorGetterField.set(generator, mockedGetter);
    }

    @When("the generator is instantiated")
    public void theGeneratorIsInstantiated() {

        textWorld.setLastException(catchThrowable(() ->
                generator = new UuidV7GeneratorImpl(annotation, idMember, context)));
    }

    @When("the generator is called to generate an ID for the entity")
    public void theGeneratorIsCalledToGenerateAnIdForTheEntity() {
        // instantiate the generator if not already done (e.g. in exception scenarios)
        if (generator == null) {
            try {
                generator = new UuidV7GeneratorImpl(annotation, idMember, context);
            } catch (Exception e) {
                textWorld.setLastException(e);
                // stop if constructor failed
                return;
            }
        }

        try {
            generatedId = generator.generate(session, entityInstance, null, EventType.INSERT);
        } catch (Exception e) {
            textWorld.setLastException(e);
        }
    }

    @When("the generator event types are requested")
    public void theGeneratorEventTypesAreRequested() {
        generator = new UuidV7GeneratorImpl(annotation, idMember, context);
        eventTypes = generator.getEventTypes();
    }

    @Then("a new UUID v7 should be generated")
    public void aNewUuidV7ShouldBeGenerated() {
        assertThat(generatedId)
                .as("generatedId")
                .isNotNull()
                .isInstanceOf(UUID.class);
    }

    @Then("the existing UUID should be returned")
    public void theExistingUuidShouldBeReturned() {
        assertThat(generatedId)
                .as("generatedId")
                .isNotNull()
                .isEqualTo(existingUuid);
    }

    @Then("the event types should be INSERT_ONLY")
    public void theEventTypesShouldBeInsertOnly() {
        assertThat(eventTypes)
                .as("eventTypes")
                .isNotNull()
                .isEqualTo(EnumSet.of(EventType.INSERT));
    }

    @Getter
    @Setter
    public static class TestEntityWithUuid {

        private UUID id;
        private String description;

    }

    @Getter
    @Setter
    public static class TestEntityWithStringId {

        // incorrect type for the generator
        private String name;

        private String description;

    }

    @Getter
    @Setter
    public static class TestEntityWithIntegerId {

        // incorrect type for the generator
        private Integer value;

        private String description;

    }

}
