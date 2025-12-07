package guru.nicks.commons.cucumber;

import guru.nicks.commons.ApplicationContextHolder;
import guru.nicks.commons.cucumber.domain.TestEntity;
import guru.nicks.commons.cucumber.world.JpaWorld;
import guru.nicks.commons.exception.http.NotFoundException;
import guru.nicks.commons.jpa.repository.EnhancedJpaSearchRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RequiredArgsConstructor
public class EnhancedJpaSearchRepositorySteps {

    // DI
    private final JpaWorld jpaWorld;

    @Mock
    private EntityManager entityManager;
    @Mock
    private EntityManagerFactory entityManagerFactory;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private JPAQuery<Object> jpaQuery;
    @Mock
    private Query query;
    @Mock
    private EntityGraph<TestEntity> entityGraph;
    private AutoCloseable closeableMocks;

    private TestRepository testRepository;
    private BooleanBuilder booleanBuilder;
    private String fullTextSearchText;
    private Pageable pageable;
    private Page<TestEntity> page;

    private Object value;
    private String stringValue;
    private String propertyName;
    private Map<String, String> jsonValue;

    private Predicate predicate;
    private boolean conditionAdded;
    private EntityPathBase<TestEntity> entityPath;

    @Before
    public void beforeEachScenario() {
        closeableMocks = MockitoAnnotations.openMocks(this);

        // setup ApplicationContextHolder used by repositories
        ApplicationContextHolder.setApplicationContext(applicationContext);
        when(applicationContext.getBean(EntityManager.class))
                .thenReturn(entityManager);
        when(applicationContext.getBean(ObjectMapper.class))
                .thenReturn(objectMapper);

        entityPath = new EntityPathBase<>(TestEntity.class, "testEntity");

        when(entityManager.getEntityManagerFactory())
                .thenReturn(entityManagerFactory);
        when(entityManager.getDelegate())
                .thenReturn(entityManagerFactory);
        when(entityManager.createQuery(any(String.class)))
                .thenReturn(query);
        when(entityManager.createEntityGraph(TestEntity.class))
                .thenReturn(entityGraph);

        // setup default JPA query behavior
        when(jpaQuery.select(any(Expression.class)))
                .thenReturn(jpaQuery);
        when(jpaQuery.from(any(EntityPath.class)))
                .thenReturn(jpaQuery);
        when(jpaQuery.where(any(Predicate.class)))
                .thenReturn(jpaQuery);
        when(jpaQuery.offset(any(Long.class)))
                .thenReturn(jpaQuery);
        when(jpaQuery.limit(any(Long.class)))
                .thenReturn(jpaQuery);
        when(jpaQuery.orderBy(any(OrderSpecifier.class)))
                .thenReturn(jpaQuery);
    }

    @After
    public void afterEachScenario() throws Exception {
        closeableMocks.close();
    }

    @Given("a test JPA repository")
    public void aTestJpaRepository() {
        testRepository = new TestRepository();
    }

    @Given("a plain search text {string}")
    public void aPlainSearchText(String text) {
        jpaWorld.setSearchText("null".equals(text)
                ? null
                : text);
    }

    @Given("full-text search text {string}")
    public void fullTextSearchText(String text) {
        fullTextSearchText = "null".equals(text)
                ? null
                : text;
    }

    @Given("pagination with page {int}, size {int}, and sort by {string} direction {string}")
    public void paginationWithPageSizeAndSortBy(int page, int size, String sortBy, String direction) {
        Sort sort = StringUtils.isBlank(sortBy)
                ? Sort.unsorted()
                : Sort.by(new Sort.Order("ASC".equals(direction) ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy));
        pageable = PageRequest.of(page, size, sort);
    }

    @Given("a boolean builder")
    public void aBooleanBuilder() {
        booleanBuilder = new BooleanBuilder();
    }

    @Given("a non-null value {string}")
    public void aNonNullValue(String value) {
        this.value = value;
    }

    @Given("a null value")
    public void aNullValue() {
        value = null;
    }

    @Given("a non-blank string {string}")
    public void aNonBlankString(String value) {
        stringValue = value;
    }

    @Given("a blank string {string}")
    public void aBlankString(String value) {
        stringValue = value;
    }

    @Given("a property name {string}")
    public void aPropertyName(String name) {
        propertyName = name;
    }

    @Given("a JSON value")
    public void aJsonValue(DataTable dataTable) {
        jsonValue = new HashMap<>();

        for (Map<String, String> row : dataTable.entries()) {
            jsonValue.put(row.get("key1"), row.get("key2"));
        }
    }

    @When("the filter is converted to a search predicate")
    public void theFilterIsConvertedToASearchPredicate() {
        var filter = TestFilter.builder()
                .text(jpaWorld.getSearchText())
                .build();
        booleanBuilder = testRepository.convertToSearchBuilder(filter);
    }

    @When("entities are found by filter")
    public void entitiesAreFoundByFilter() {
        var filter = TestFilter.builder()
                .text(jpaWorld.getSearchText())
                .build();

        // actual sort criteria may change (due to FTS) as compared that passed in pageable
        page = testRepository.findByFilter(filter, pageable);
    }

    @When("andIfNotNull is called with the value")
    public void andIfNotNullIsCalledWithTheValue() {
        Supplier<Object> valueSupplier = () -> value;

        Function<Object, Predicate> condition = val -> {
            conditionAdded = true;
            return Expressions.TRUE;
        };

        testRepository.andIfNotNull(valueSupplier, booleanBuilder, condition);
    }

    @When("andIfNotBlank is called with the string")
    public void andIfNotBlankIsCalledWithTheString() {
        Supplier<String> valueSupplier = () -> stringValue;

        Function<String, Predicate> condition = val -> {
            conditionAdded = true;
            return Expressions.TRUE;
        };

        testRepository.andIfNotBlank(valueSupplier, booleanBuilder, condition);
    }

    @When("a JSON contains predicate is created")
    public void aJsonContainsPredicateIsCreated() throws JsonProcessingException {
        // mock ObjectMapper behavior
        when(objectMapper.writeValueAsString(jsonValue))
                .thenReturn("{\"key1\":\"value1\",\"key2\":\"value2\"}");

        predicate = testRepository.createJsonContainsPredicate(propertyName, jsonValue);
    }

    @When("pagination and sort are applied to the query")
    public void paginationAndSortAreAppliedToTheQuery() {
        // mock EntityManager behavior for Querydsl constructor
        when(entityManager.getEntityManagerFactory())
                .thenReturn(mock(EntityManagerFactory.class));

        testRepository.applyPaginationAndSort(jpaQuery, pageable, entityPath);
    }

    @When("an entity graph is created in the repository")
    public void anEntityGraphIsCreated() {
        // repository delegates the call to entityManager via getEntityManager()
        var result = testRepository.createEntityGraph();

        assertThat(result)
                .as("entityGraph")
                .isEqualTo(entityGraph);
    }

    @Then("the predicate should not be empty")
    public void thePredicateShouldNotBeEmpty() {
        assertThat(booleanBuilder)
                .as("booleanBuilder")
                .isNotNull();

        assertThat(booleanBuilder.hasValue())
                .as("booleanBuilder.hasValue()")
                .isTrue();
    }

    @Then("the predicate should be empty")
    public void thePredicateShouldBeEmpty() {
        assertThat(booleanBuilder)
                .as("booleanBuilder")
                .isNotNull();

        assertThat(booleanBuilder.hasValue())
                .as("booleanBuilder.hasValue()")
                .isFalse();
    }

    @Then("the result should be sorted by {string} {string}")
    public void theResultShouldBeSortedBy(String sortedBy, String direction) {
        if (StringUtils.isBlank(sortedBy)) {
            assertThat(page.getSort().isUnsorted())
                    .as("result doesn't specify any sorting criteria")
                    .isTrue();
        } else {
            assertThat(page.getSort().getOrderFor(sortedBy))
                    .as(pageable.getSort() + " for field '" + sortedBy + "'")
                    .isNotNull();

            var enumDirection = Sort.Direction.valueOf(direction);
            assertThat(page.getSort().getOrderFor(sortedBy).getDirection())
                    .as(pageable.getSort() + " for field '" + sortedBy + "'")
                    .isEqualTo(enumDirection);
        }
    }

    @Then("the condition should be added to the boolean builder")
    public void theConditionShouldBeAddedToTheBooleanBuilder() {
        assertThat(conditionAdded)
                .as("condition added to BooleanBuilder")
                .isTrue();
    }

    @Then("the condition should not be added to the boolean builder")
    public void theConditionShouldNotBeAddedToTheBooleanBuilder() {
        assertThat(conditionAdded)
                .as("condition added to BooleanBuilder")
                .isFalse();
    }

    @Then("the predicate should contain the JSON template")
    public void thePredicateShouldContainTheJsonTemplate() throws JsonProcessingException {
        assertThat(predicate)
                .as("predicate")
                .isNotNull();

        // verify that ObjectMapper was called with the JSON value
        verify(objectMapper).writeValueAsString(jsonValue);
    }

    @Then("the query should have offset {int} and limit {int}")
    public void theQueryShouldHaveOffsetAndLimit(int offset, int limit) {
        verify(jpaQuery).offset(offset);
        verify(jpaQuery).limit(limit);
    }

    @Then("the query should be sorted by {string} direction {string}")
    public void theQueryShouldBeSortedByDirection(String field, String direction) {
        Sort.Direction expectedDirection = "ASC".equals(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        assertThat(pageable.getSort().getOrderFor(field))
                .as("pageable sort order for " + field)
                .isNotNull();

        assertThat(pageable.getSort().getOrderFor(field).getDirection())
                .as("pageable sort direction for " + field)
                .isEqualTo(expectedDirection);
    }

    @Then("the entity graph should be created in EntityManager")
    public void theEntityGraphShouldBeCreatedInEntityManager() {
        verify(entityManager).createEntityGraph(TestEntity.class);
    }

    @Value
    @Builder
    public static class TestFilter {

        String text;

    }

    public class TestRepository
            implements EnhancedJpaSearchRepository<TestEntity, String, NotFoundException, TestFilter> {

        @Nonnull
        @Override
        public BooleanBuilder convertToSearchBuilder(@Nonnull TestFilter filter) {
            var builder = new BooleanBuilder();

            if ((filter != null) && (filter.getText() != null) && !filter.getText().isBlank()) {
                builder.and(Expressions.TRUE);
            }

            return builder;
        }

        @Transactional(readOnly = true)
        @Nonnull
        @Override
        public Page<TestEntity> findByFilter(@Nonnull TestFilter filter, @Nonnull Pageable pageable) {
            return findByFilter(filter,
                    () -> fullTextSearchText,
                    pageable, entityPath,
                    () -> entityGraph);
        }

        @Nonnull
        @Override
        public ObjectMapper getObjectMapperBean() {
            return objectMapper;
        }

        @Nonnull
        @Override
        public Class<TestEntity> getEntityClass() {
            return TestEntity.class;
        }

        @Nonnull
        @Override
        public EntityManager getEntityManagerBean() {
            return entityManager;
        }

        @Nonnull
        @Override
        public Stream<TestEntity> findAllBy() {
            return Stream.empty();
        }

        @Override
        public void flush() {
            // Mock implementation for testing
        }

        @Nonnull
        @Override
        public <S extends TestEntity> S saveAndFlush(S entity) {
            return entity;
        }

        @Nonnull
        @Override
        public <S extends TestEntity> List<S> saveAllAndFlush(Iterable<S> entities) {
            // Mock implementation for testing
            return List.of();
        }

        @Override
        public void deleteAllInBatch(Iterable<TestEntity> entities) {
            // do nothing
        }

        @Override
        public void deleteAllByIdInBatch(Iterable<String> strings) {
            // do nothing
        }

        @Override
        public void deleteAllInBatch() {
            // do nothing
        }

        @Override
        public TestEntity getOne(String s) {
            return null;
        }

        @Nonnull
        @Override
        public TestEntity getById(String s) {
            return null;
        }

        public TestEntity getReferenceById(String s) {
            return null;
        }

        @Nonnull
        @Override
        public <S extends TestEntity> List<S> findAll(Example<S> example) {
            return List.of();
        }

        @Nonnull
        @Override
        public <S extends TestEntity> List<S> findAll(Example<S> example, Sort sort) {
            return List.of();
        }

        @Override
        @Nonnull
        public Optional<TestEntity> findOne(Predicate predicate) {
            return Optional.empty();
        }

        @Override
        public Iterable<TestEntity> findAll(Predicate predicate) {
            return null;
        }

        @Nonnull
        @Override
        public Iterable<TestEntity> findAll(Predicate predicate, Sort sort) {
            return null;
        }

        @Override
        public Iterable<TestEntity> findAll(Predicate predicate, OrderSpecifier<?>... orders) {
            return null;
        }

        @Override
        public Iterable<TestEntity> findAll(OrderSpecifier<?>... orders) {
            return null;
        }

        @Override
        public Page<TestEntity> findAll(Predicate predicate, Pageable pageable) {
            return null;
        }

        @Override
        public long count(Predicate predicate) {
            return 0;
        }

        @Override
        public boolean exists(Predicate predicate) {
            return false;
        }

        @Override
        public <S extends TestEntity, R> R findBy(Predicate predicate,
                Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            return null;
        }

        @Nonnull
        @Override
        public <S extends TestEntity> List<S> saveAll(Iterable<S> entities) {
            return List.of();
        }

        @Nonnull
        @Override
        public List<TestEntity> findAll() {
            return List.of();
        }

        @Nonnull
        @Override
        public List<TestEntity> findAllById(Iterable<String> strings) {
            return List.of();
        }

        @Override
        public <S extends TestEntity> S save(S entity) {
            return null;
        }

        @Nonnull
        @Override
        public Optional<TestEntity> findById(String s) {
            return Optional.empty();
        }

        @Override
        public boolean existsById(String s) {
            return false;
        }

        @Override
        public long count() {
            return 0;
        }

        @Override
        public void deleteById(String s) {
            // do nothing
        }

        @Override
        public void delete(TestEntity entity) {
            // do nothing
        }

        @Override
        public void deleteAllById(Iterable<? extends String> strings) {
            // do nothing
        }

        @Override
        public void deleteAll(Iterable<? extends TestEntity> entities) {
            // do nothing
        }

        @Override
        public void deleteAll() {
            // do nothing
        }

        @Nonnull
        @Override
        public List<TestEntity> findAll(Sort sort) {
            return List.of();
        }

        @Override
        public Page<TestEntity> findAll(Pageable pageable) {
            return null;
        }

        @Nonnull
        @Override
        public <S extends TestEntity> Optional<S> findOne(Example<S> example) {
            return Optional.empty();
        }

        @Override
        public <S extends TestEntity> Page<S> findAll(Example<S> example, Pageable pageable) {
            return null;
        }

        @Override
        public <S extends TestEntity> long count(Example<S> example) {
            return 0;
        }

        @Override
        public <S extends TestEntity> boolean exists(Example<S> example) {
            return false;
        }

        @Override
        public <S extends TestEntity, R> R findBy(Example<S> example,
                Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
            return null;
        }
    }

}
