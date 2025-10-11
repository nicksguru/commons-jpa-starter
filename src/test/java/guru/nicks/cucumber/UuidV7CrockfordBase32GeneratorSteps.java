package guru.nicks.cucumber;

import guru.nicks.cucumber.domain.TestEntity;
import guru.nicks.cucumber.world.JpaWorld;
import guru.nicks.cucumber.world.TextWorld;
import guru.nicks.jpa.generator.UuidV7CrockfordBase32Generator;
import guru.nicks.jpa.generator.UuidV7CrockfordBase32GeneratorImpl;
import guru.nicks.utils.UuidUtils;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.EventType;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@RequiredArgsConstructor
public class UuidV7CrockfordBase32GeneratorSteps {

    // DI
    private final JpaWorld jpaWorld;
    private final TextWorld textWorld;

    @Mock
    private SharedSessionContractImplementor session;
    @Mock
    private UuidV7CrockfordBase32Generator annotation;
    private AutoCloseable closeableMocks;

    private UuidV7CrockfordBase32GeneratorImpl generator;

    @Before
    public void beforeEachScenario() {
        closeableMocks = MockitoAnnotations.openMocks(this);
    }

    @After
    public void afterEachScenario() throws Exception {
        closeableMocks.close();
    }

    @Given("a UuidV7CrockfordBase32Generator is created for a String property")
    public void aUuidV7CrockfordBase32GeneratorImplIsCreatedForAStringProperty() throws Exception {
        Field idMember = TestEntity.class.getDeclaredField(TestEntity.Fields.id);
        generator = new UuidV7CrockfordBase32GeneratorImpl(annotation, idMember, null);
    }

    @When("the UuidV7CrockfordBase32Generator is called with a new entity")
    public void theUuidV7CrockfordBase32GeneratorIsCalledWithANewEntity() {
        jpaWorld.setEntity(TestEntity.builder().build());
        jpaWorld.setGeneratedId((String) generator.generate(session, jpaWorld.getEntity(), null, EventType.INSERT));
    }

    @When("the UuidV7CrockfordBase32Generator is called with the existing entity")
    public void theUuidV7CrockfordBase32GeneratorIsCalledWithTheExistingEntity() {
        jpaWorld.setGeneratedId((String) generator.generate(session, jpaWorld.getEntity(), null, EventType.INSERT));
    }

    @When("a UuidV7CrockfordBase32Generator is created for a non-String property")
    public void aUuidV7CrockfordBase32GeneratorIsCreatedForANonStringProperty() throws NoSuchFieldException {
        Field idMember = NonStringIdEntity.class.getDeclaredField(NonStringIdEntity.Fields.nonStringId);

        // attempt to create the generator, which should throw an exception
        textWorld.setLastException(catchThrowable(() ->
                new UuidV7CrockfordBase32GeneratorImpl(annotation, idMember, null)
        ));
    }

    @And("the generated ID should decode back to an UUID v7")
    public void theGeneratedIDShouldDecodeBackToAnUUIDv7() {
        UUID uuid = UuidUtils.decodeFromCrockfordBase32(jpaWorld.getGeneratedId());
        assertThat(uuid)
                .as("decoded")
                .isNotNull();
        assertThat(uuid.version())
                .as("UUID version")
                .isEqualTo(7);

        String encodedAgain = UuidUtils.encodeToCrockfordBase32(uuid);
        assertThat(encodedAgain)
                .as("encoded")
                .isEqualTo(jpaWorld.getGeneratedId());
    }

    @When("event types are retrieved from the UuidV7CrockfordBase32Generator")
    public void eventTypesAreRetrievedFromTheUuidV7CrockfordBaseGenerator() {
        jpaWorld.setGeneratorEventTypes(generator.getEventTypes());
    }

    @Getter
    @Setter
    @Builder
    @FieldNameConstants
    public static class NonStringIdEntity {

        private UUID nonStringId;

    }

}
