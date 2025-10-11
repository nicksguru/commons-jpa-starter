package guru.nicks.cucumber;

import guru.nicks.cucumber.domain.TestEntity;
import guru.nicks.cucumber.world.JpaWorld;
import guru.nicks.cucumber.world.TextWorld;
import guru.nicks.jpa.generator.UuidV4CrockfordBase32Generator;
import guru.nicks.jpa.generator.UuidV4CrockfordBase32GeneratorImpl;
import guru.nicks.utils.UuidUtils;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
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
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@RequiredArgsConstructor
public class UuidV4CrockfordBase32GeneratorSteps {

    // DI
    private final JpaWorld jpaWorld;
    private final TextWorld textWorld;

    @Mock
    private SharedSessionContractImplementor session;
    @Mock
    private UuidV4CrockfordBase32Generator annotation;
    private AutoCloseable closeableMocks;
    private UuidV4CrockfordBase32GeneratorImpl generator;

    @Before
    public void beforeEachScenario() {
        closeableMocks = MockitoAnnotations.openMocks(this);
    }

    @After
    public void afterEachScenario() throws Exception {
        closeableMocks.close();
    }

    @Given("a UuidV4CrockfordBase32Generator is created for a String property")
    public void aUuidV4CrockfordBase32GeneratorImplIsCreatedForAStringProperty() throws Exception {
        Field idMember = TestEntity.class.getDeclaredField(TestEntity.Fields.id);
        generator = new UuidV4CrockfordBase32GeneratorImpl(annotation, idMember, null);
    }

    @Given("an entity with ID {string}")
    public void anEntityWithId(String existingId) {
        jpaWorld.setEntity(TestEntity.builder()
                .id(existingId)
                .build());
    }

    @When("the UuidV4CrockfordBase32Generator is called with a new entity")
    public void theUuidV4CrockfordBase32GeneratorIsCalledWithANewEntity() {
        jpaWorld.setEntity(TestEntity.builder().build());
        jpaWorld.setGeneratedId((String) generator.generate(session, jpaWorld.getEntity(), null, EventType.INSERT));
    }

    @When("the UuidV4CrockfordBase32Generator is called with the existing entity")
    public void theUuidV4CrockfordBase32GeneratorIsCalledWithTheExistingEntity() {
        jpaWorld.setGeneratedId((String) generator.generate(session, jpaWorld.getEntity(), null, EventType.INSERT));
    }

    @When("a UuidV4CrockfordBase32Generator is created for a non-String property")
    public void aUuidV4CrockfordBase32GeneratorIsCreatedForANonStringProperty() throws NoSuchFieldException {
        Field idMember = NonStringIdEntity.class.getDeclaredField(NonStringIdEntity.Fields.nonStringId);

        // attempt to create the generator, which should throw an exception
        textWorld.setLastException(catchThrowable(() ->
                new UuidV4CrockfordBase32GeneratorImpl(annotation, idMember, null)
        ));
    }

    @Then("a non-null String ID should be generated out of UUIDv4")
    public void aNonNullStringIdShouldBeGeneratedOutOfUUIDv4() {
        assertThat(jpaWorld.getGeneratedId())
                .as("generatedId")
                .isNotNull();
    }

    @Then("the generated ID should be {int} characters long")
    public void theGeneratedIdShouldBeCharactersLong(int length) {
        assertThat(jpaWorld.getGeneratedId())
                .as("generatedId.length")
                .hasSize(length);
    }

    @Then("the generated ID should contain only valid Crockford Base32 characters")
    public void theGeneratedIdShouldContainOnlyValidCrockfordBase32Characters() {
        // Crockford Base32 uses: 0123456789ABCDEFGHJKMNPQRSTVWXYZ (lowercase in our implementation)
        assertThat(jpaWorld.getGeneratedId())
                .as("generatedId")
                .matches("[0-9a-hjkmnp-tv-z]+");
    }

    @Then("the entity ID should remain {string}")
    public void theEntityIdShouldRemain(String expectedId) {
        assertThat(jpaWorld.getGeneratedId())
                .as("generatedId")
                .isEqualTo(expectedId);
    }

    @Then("the generated ID should decode back to an UUID v4")
    public void theGeneratedIDShouldDecodeBackToAnUUIDv4() {
        UUID uuid = UuidUtils.decodeFromCrockfordBase32(jpaWorld.getGeneratedId());
        assertThat(uuid)
                .as("decoded")
                .isNotNull();
        assertThat(uuid.version())
                .as("UUID version")
                .isEqualTo(4);

        String encodedAgain = UuidUtils.encodeToCrockfordBase32(uuid);
        assertThat(encodedAgain)
                .as("encoded")
                .isEqualTo(jpaWorld.getGeneratedId());
    }

    @Then("a non-blank String ID should be generated")
    public void aNonBlankStringIDShouldBeGenerated() {
        assertThat(jpaWorld.getGeneratedId())
                .as("generatedId")
                .isNotBlank();
    }

    @When("event types are retrieved from the UuidV4CrockfordBase32Generator")
    public void eventTypesAreRetrievedFromTheUuidV4CrockfordBaseGenerator() {
        jpaWorld.setGeneratorEventTypes(generator.getEventTypes());
    }

    @Then("the generator event types should be {string}")
    public void theGeneratorEventTypesShouldBe(String commaSeparatedValues) {
        Set<EventType> expectedEventTypes = Arrays.stream(commaSeparatedValues.split(","))
                .map(EventType::valueOf)
                .collect(Collectors.toSet());

        assertThat(jpaWorld.getGeneratorEventTypes())
                .as("eventTypes")
                .isEqualTo(expectedEventTypes);
    }

    @Getter
    @Setter
    @Builder
    @FieldNameConstants
    public static class NonStringIdEntity {

        private UUID nonStringId;

    }

}
