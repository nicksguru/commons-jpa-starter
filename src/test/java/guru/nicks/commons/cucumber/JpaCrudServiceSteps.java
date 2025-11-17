package guru.nicks.commons.cucumber;

import guru.nicks.commons.cucumber.domain.TestEntity;
import guru.nicks.commons.cucumber.world.JpaWorld;
import guru.nicks.commons.cucumber.world.TextWorld;
import guru.nicks.commons.exception.BusinessException;
import guru.nicks.commons.jpa.repository.EnhancedJpaRepository;
import guru.nicks.commons.jpa.service.JpaCrudService;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import lombok.experimental.StandardException;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step definitions for testing JpaCrudService operations.
 */
@RequiredArgsConstructor
public class JpaCrudServiceSteps {

    // DI
    private final JpaWorld jpaWorld;
    private final TextWorld textWorld;

    @Spy
    private TestRepository repository;
    private AutoCloseable closeableMocks;

    private JpaCrudService<TestEntity, String> service;
    private List<TestEntity> entities;

    private boolean existsResult;
    private Optional<TestEntity> foundEntity;
    private TestEntity retrievedEntity;
    private List<TestEntity> foundEntities;

    private Stream<TestEntity> entityStream;
    private Page<TestEntity> entityPage;

    @Before
    public void beforeEachScenario() {
        closeableMocks = MockitoAnnotations.openMocks(this);
        service = () -> repository;
    }

    @After
    public void afterEachScenario() throws Exception {
        closeableMocks.close();
    }

    @Given("an entity exists")
    public void anEntityExists() {
        jpaWorld.setEntity(TestEntity.builder()
                .id(UUID.randomUUID().toString())
                .name("Test Entity")
                .build());

        when(repository.existsById(jpaWorld.getEntity().getId()))
                .thenReturn(true);
        when(repository.findById(jpaWorld.getEntity().getId()))
                .thenReturn(Optional.of(jpaWorld.getEntity()));
    }

    @Given("an entity with ID {string} exists")
    public void anEntityWithIdExists(String id) {
        jpaWorld.setEntity(TestEntity.builder()
                .id(id)
                .name("Test Entity " + id)
                .build());

        when(repository.existsById(id))
                .thenReturn(true);
        when(repository.findById(id))
                .thenReturn(Optional.of(jpaWorld.getEntity()));
    }

    @Given("no entity with ID {string} exists")
    public void noEntityWithIdExists(String id) {
        when(repository.existsById(id))
                .thenReturn(false);
        when(repository.findById(id))
                .thenReturn(Optional.empty());
    }

    @Given("entities with IDs {string} exist")
    public void entitiesWithIdsExist(String idsString) {
        String[] ids = idsString.split(",");
        entities = new ArrayList<>();

        for (String id : ids) {
            jpaWorld.setEntity(TestEntity.builder()
                    .id(id)
                    .name("Test Entity " + id)
                    .build());
            entities.add(jpaWorld.getEntity());
        }

        when(repository.findAllById(anyCollection()))
                .thenReturn(entities);
        when(repository.findAllByIdPreserveOrder(anyCollection()))
                .thenReturn(entities);
    }

    @Given("multiple entities exist")
    public void multipleEntitiesExist() {
        entities = List.of(
                TestEntity.builder().id("1").name("Entity 1").build(),
                TestEntity.builder().id("2").name("Entity 2").build(),
                TestEntity.builder().id("3").name("Entity 3").build()
        );

        when(repository.findAllAsStream())
                .thenReturn(entities.stream());
        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(entities));
    }

    @When("an entity is saved")
    public void anEntityIsSaved() {
        jpaWorld.setEntity(TestEntity.builder()
                .id(UUID.randomUUID().toString())
                .name("New Entity")
                .build());

        when(repository.save(jpaWorld.getEntity()))
                .thenReturn(jpaWorld.getEntity());
        jpaWorld.setEntity(service.save(jpaWorld.getEntity()));
    }

    @When("multiple entities are saved")
    public void multipleEntitiesAreSaved() {
        entities = List.of(
                TestEntity.builder().id("1").name("Entity 1").build(),
                TestEntity.builder().id("2").name("Entity 2").build()
        );

        when(repository.saveAll(anyIterable()))
                .thenReturn(entities);
        entities = (List<TestEntity>) service.saveAll(entities);
    }

    @When("the entity is deleted")
    public void theEntityIsDeleted() {
        doNothing().when(repository).delete(jpaWorld.getEntity());
        service.delete(jpaWorld.getEntity());
    }

    @When("the entity with ID {string} is deleted")
    public void theEntityWithIdIsDeleted(String id) {
        doNothing().when(repository).deleteById(id);
        service.deleteById(id);
    }

    @When("entities with IDs {string} are deleted")
    public void entitiesWithIdsAreDeleted(String idsString) {
        List<String> ids = Arrays.asList(idsString.split(","));
        doNothing().when(repository).deleteAllById(ids);
        service.deleteAllById(ids);
    }

    @When("existence is checked for ID {string}")
    public void existenceIsCheckedForId(String id) {
        existsResult = service.existsById(id);
    }

    @When("an entity is found by ID {string}")
    public void anEntityIsFoundById(String id) {
        foundEntity = service.findById(id);
    }

    @When("an entity is retrieved by ID {string}")
    public void anEntityIsRetrievedById(String id) {
        try {
            retrievedEntity = service.getByIdOrThrow(id);
        } catch (Exception e) {
            textWorld.setLastException(e);
        }
    }

    @When("entities are found by IDs {string}")
    public void entitiesAreFoundByIds(String idsString) {
        List<String> ids = Arrays.asList(idsString.split(","));
        foundEntities = service.findAllByIdPreserveOrder(ids);
    }

    @When("all entities are found as stream")
    public void allEntitiesAreFoundAsStream() {
        entityStream = service.findAllAsStream();
    }

    @When("all entities are found with page {int} and size {int}")
    public void allEntitiesAreFoundWithPageAndSize(int page, int size) {
        entityPage = service.findAll(Pageable.ofSize(size).withPage(page));
    }

    @Then("the entity should be saved successfully")
    public void theEntityShouldBeSavedSuccessfully() {
        verify(repository).save(jpaWorld.getEntity());

        assertThat(jpaWorld.getEntity())
                .as("entity")
                .isNotNull();
    }

    @Then("all entities should be saved successfully")
    public void allEntitiesShouldBeSavedSuccessfully() {
        verify(repository).saveAll(entities);

        assertThat(entities)
                .as("entities")
                .isNotNull()
                .isNotEmpty();
    }

    @Then("the entity should be deleted successfully")
    public void theEntityShouldBeDeletedSuccessfully() {
        verify(repository).delete(jpaWorld.getEntity());
    }

    @Then("the entity should be deleted successfully by ID {string}")
    public void theEntityShouldBeDeletedSuccessfullyByID(String id) {
        verify(repository).deleteById(id);
    }

    @Then("the entities should be deleted successfully")
    public void theEntitiesShouldBeDeletedSuccessfully() {
        verify(repository).deleteAllById(anyIterable());
    }

    @Then("the existence result should be {booleanValue}")
    public void theExistenceResultShouldBe(boolean expected) {
        assertThat(existsResult)
                .as("existsResult")
                .isEqualTo(expected);
    }

    @Then("the entity should be found")
    public void theEntityShouldBeFound() {
        assertThat(foundEntity)
                .as("foundEntity")
                .isPresent();
    }

    @Then("the entity should be retrieved")
    public void theEntityShouldBeRetrieved() {
        assertThat(retrievedEntity)
                .as("retrievedEntity")
                .isNotNull();
    }

    @Then("entities should be found in the order {string}")
    public void entitiesShouldBeFoundInTheOrder(String expectedOrderString) {
        String[] expectedOrder = expectedOrderString.split(",");

        assertThat(foundEntities)
                .as("foundEntities")
                .isNotNull()
                .hasSize(expectedOrder.length);

        for (int i = 0; i < expectedOrder.length; i++) {
            assertThat(foundEntities.get(i).getId())
                    .as("entity at index " + i)
                    .isEqualTo(expectedOrder[i]);
        }
    }

    @Then("all entities should be returned as stream")
    public void allEntitiesShouldBeReturnedAsStream() {
        assertThat(entityStream)
                .as("entityStream")
                .isNotNull();

        List<TestEntity> streamedEntities = entityStream.toList();
        assertThat(streamedEntities)
                .as("streamedEntities")
                .hasSize(entities.size());
    }

    @Then("a page of entities should be returned")
    public void aPageOfEntitiesShouldBeReturned() {
        assertThat(entityPage)
                .as("entityPage")
                .isNotNull();

        assertThat(entityPage.getContent())
                .as("page content")
                .hasSize(entities.size());
    }

    @Then("TestException should be thrown")
    public void testEntityExceptionShouldBeThrown() {
        assertThat(textWorld.getLastException()).isInstanceOf(TestException.class);
    }

    public interface TestRepository extends EnhancedJpaRepository<TestEntity, String, TestException> {
    }

    @StandardException
    public static class TestException extends BusinessException {
    }

}
