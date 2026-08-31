package guru.nicks.commons.cucumber;

import guru.nicks.commons.cucumber.world.TextWorld;
import guru.nicks.commons.jpa.JpaInference;
import guru.nicks.commons.jpa.impl.EnhancedJpaSearchRepositoryFragmentImpl;
import guru.nicks.commons.jpa.it.domain.TestAuthor;
import guru.nicks.commons.jpa.it.domain.TestDocument;
import guru.nicks.commons.jpa.it.domain.TestDocumentFilter;
import guru.nicks.commons.jpa.it.domain.TestDocumentNotFoundException;
import guru.nicks.commons.jpa.it.repo.TestAuthorRepository;
import guru.nicks.commons.jpa.it.repo.TestDocumentRepository;
import guru.nicks.commons.jpa.repository.EnhancedJpaSearchRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions pinning {@code EnhancedJpaSearchRepositoryFragment}: filter predicates with pagination/sorting,
 * andIfNotNull/andIfNotBlank helpers, the JSON-contains predicate (executed against the H2-emulated JSON_CONTAINS
 * function) and the full-text search ngram path (executed against the H2-emulated FULL_TEXT_SEARCH function).
 */
@RequiredArgsConstructor
public class EnhancedJpaSearchRepositoryFragmentSteps {

    // DI
    private final TestDocumentRepository documentRepository;
    private final TestAuthorRepository authorRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final TextWorld textWorld;
    private final JpaInference jpaInference;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    private Page<TestDocument> resultPage;
    private Predicate jsonPredicate;
    private BooleanBuilder builder;
    private AtomicBoolean conditionInvoked;
    private long rebuiltCount;

    /**
     * Persists the default document set: three documents spread over two authors and users, with JSON metadata.
     */
    @Given("the default documents exist")
    public void theDefaultDocumentsExist() {
        transactionTemplate.executeWithoutResult(tx -> {
            authorRepository.saveAll(List.of(
                    TestAuthor.builder().id("author-alice").name("Alice Author").build(),
                    TestAuthor.builder().id("author-bob").name("Bob Author").build()));
            documentRepository.saveAll(List.of(
                    newDocument("doc-1", "Alpha red document", "user-1", "{\"color\":\"red\"}", "author-alice"),
                    newDocument("doc-2", "Beta blue document", "user-1", "{\"color\":\"blue\"}", "author-alice"),
                    newDocument("doc-3", "Gamma green document", "user-2", "{\"color\":\"green\"}", "author-bob")));
        });
    }

    /**
     * Searches documents with a name/user filter, pagination and sorting.
     *
     * @param name   substring to match the document name against
     * @param userId exact user ID to match
     * @param page   1-based page number
     * @param size   page size
     */
    @When("documents are searched with a filter for name {string} and user {string} requesting page {int} of size {int} sorted by name")
    public void documentsAreSearchedWithAFilterForNameAndUser(String name, String userId, int page, int size) {
        var filter = new TestDocumentFilter(name, userId, null, null);
        resultPage = documentRepository.findByFilter(filter, PageRequest.of(page - 1, size, Sort.by("name")));
    }

    /**
     * Searches documents with a null filter (no conditions at all).
     */
    @When("documents are searched with a null filter")
    public void documentsAreSearchedWithANullFilter() {
        resultPage = documentRepository.findByFilter(null, PageRequest.of(0, 10));
    }

    /**
     * Searches documents with an all-null filter (no conditions at all).
     */
    @When("documents are searched with an empty filter")
    public void documentsAreSearchedWithAnEmptyFilter() {
        resultPage = documentRepository.findByFilter(new TestDocumentFilter(null, null, null, null),
                PageRequest.of(0, 10));
    }

    /**
     * Searches documents by a value inside the JSON metadata column.
     *
     * @param color color value to look for in the metadata JSON
     */
    @When("documents are searched with a filter for metadata color {string}")
    public void documentsAreSearchedWithAFilterForMetadataColor(String color) {
        resultPage = documentRepository.findByFilter(new TestDocumentFilter(null, null, color, null),
                PageRequest.of(0, 10));
    }

    /**
     * Searches documents by full-text (ngram fuzzy) search text.
     *
     * @param searchText search text, may be deliberately misspelled
     */
    @When("documents are searched with a full-text search for {string}")
    public void documentsAreSearchedWithAFullTextSearchFor(String searchText) {
        resultPage = documentRepository.findByFilter(new TestDocumentFilter(null, null, null, searchText),
                PageRequest.of(0, 10));
    }

    /**
     * Searches documents with a null full-text search text (no FTS condition applied).
     */
    @When("documents are searched with a null full-text search")
    public void documentsAreSearchedWithANullFullTextSearch() {
        resultPage = documentRepository.findByFilter(new TestDocumentFilter(null, null, null, null),
                PageRequest.of(0, 10));
    }

    /**
     * Searches documents with a blank full-text search text (no FTS condition applied).
     */
    @When("documents are searched with a blank full-text search")
    public void documentsAreSearchedWithABlankFullTextSearch() {
        resultPage = documentRepository.findByFilter(new TestDocumentFilter(null, null, null, " "),
                PageRequest.of(0, 10));
    }

    /**
     * Verifies the total element count of the last search result page.
     *
     * @param expected expected total elements
     */
    @Then("the total elements should be {int}")
    public void theTotalElementsShouldBe(int expected) {
        assertThat(resultPage.getTotalElements())
                .as("total elements")
                .isEqualTo(expected);
    }

    /**
     * Verifies the total page count of the last search result page.
     *
     * @param expected expected total pages
     */
    @Then("the total pages should be {int}")
    public void theTotalPagesShouldBe(int expected) {
        assertThat(resultPage.getTotalPages())
                .as("total pages")
                .isEqualTo(expected);
    }

    /**
     * Verifies the document names in the last search result page, in order.
     *
     * @param names comma-separated expected names, in order
     */
    @Then("the page content names should be {string}")
    public void thePageContentNamesShouldBe(String names) {
        assertThat(resultPage.getContent())
                .extracting(TestDocument::getName)
                .containsExactly(names.split(","));
    }

    /**
     * Corrupts the stored ngram data of all documents via a bulk JPQL update, bypassing {@code @PreUpdate} callbacks.
     * This simulates rows left stale by a change in ngram generation logic (e.g. a lemmatization fix): the checksum
     * still matches the raw text (which is untouched), so a regular save would never rebuild the ngrams.
     */
    @When("the full-text search data of all documents is corrupted by a bulk JPQL update")
    public void theFullTextSearchDataOfAllDocumentsIsCorruptedByABulkJpqlUpdate() {
        transactionTemplate.executeWithoutResult(tx ->
                entityManager.createQuery(
                                "UPDATE TestDocument d SET d.fullTextSearchData = :corrupted")
                        .setParameter("corrupted", "stale")
                        .executeUpdate());
    }

    /**
     * Invokes the batch reindex on the document repository.
     */
    @When("the full-text search data is rebuilt")
    public void theFullTextSearchDataIsRebuilt() {
        rebuiltCount = documentRepository.rebuildFullTextSearchData();
    }

    /**
     * Verifies the number of entities processed by the last batch reindex.
     *
     * @param expected expected processed entity count
     */
    @Then("the number of processed documents should be {int}")
    public void theNumberOfProcessedDocumentsShouldBe(int expected) {
        assertThat(rebuiltCount)
                .as("processed documents")
                .isEqualTo(expected);
    }

    /**
     * Verifies the first (highest-ranked) document name of the last search result page.
     *
     * @param name expected first document name
     */
    @Then("the first page content name should be {string}")
    public void theFirstPageContentNameShouldBe(String name) {
        assertThat(resultPage.getContent())
                .as("page content")
                .isNotEmpty();
        assertThat(resultPage.getContent().getFirst().getName())
                .as("first (highest-ranked) page content name")
                .isEqualTo(name);
    }

    /**
     * Invokes the batch reindex on a fragment wired for a non-FTS entity type (constructed directly, without the
     * repository proxy) and captures the failure.
     */
    @When("the full-text search data is rebuilt for a repository of a non-FTS entity type")
    public void theFullTextSearchDataIsRebuiltForARepositoryOfANonFtsEntityType() {
        try {
            new EnhancedJpaSearchRepositoryFragmentImpl<>(entityManager, NonFtsSearchRepository.class, jpaInference,
                    applicationContext, objectMapper).rebuildFullTextSearchData();
        } catch (RuntimeException e) {
            textWorld.setLastException(e);
        }
    }

    /**
     * Invokes andIfNotNull with a supplier yielding null.
     */
    @When("andIfNotNull is invoked with a null value")
    public void andIfNotNullIsInvokedWithANullValue() {
        invokeAndIfNotNull(() -> null);
    }

    /**
     * Invokes andIfNotNull with a supplier yielding a present value.
     */
    @When("andIfNotNull is invoked with a present value")
    public void andIfNotNullIsInvokedWithAPresentValue() {
        invokeAndIfNotNull(() -> "Alpha red document");
    }

    /**
     * Invokes andIfNotBlank with a supplier yielding a blank value.
     */
    @When("andIfNotBlank is invoked with a blank value")
    public void andIfNotBlankIsInvokedWithABlankValue() {
        invokeAndIfNotBlank(() -> " ");
    }

    /**
     * Invokes andIfNotBlank with a supplier yielding a non-blank value.
     */
    @When("andIfNotBlank is invoked with a non-blank value")
    public void andIfNotBlankIsInvokedWithANonBlankValue() {
        invokeAndIfNotBlank(() -> "text");
    }

    /**
     * Verifies that the condition lambda was not evaluated and the builder collected no value.
     */
    @Then("the condition should not be evaluated and the builder should stay empty")
    public void theConditionShouldNotBeEvaluatedAndTheBuilderShouldStayEmpty() {
        assertThat(conditionInvoked).as("condition evaluated").isFalse();
        assertThat(builder.hasValue()).as("builder value").isFalse();
    }

    /**
     * Verifies that the condition lambda was evaluated and the builder collected a value.
     */
    @Then("the condition should be evaluated and the builder should have a value")
    public void theConditionShouldBeEvaluatedAndTheBuilderShouldHaveAValue() {
        assertThat(conditionInvoked).as("condition evaluated").isTrue();
        assertThat(builder.hasValue()).as("builder value").isTrue();
    }

    /**
     * Creates a JSON-contains predicate, capturing failures for root-cause assertions.
     *
     * @param property property name to search in
     * @param value    value to search for
     */
    @When("a JSON contains predicate is created for property {string} and value {string}")
    public void aJsonContainsPredicateIsCreatedForPropertyAndValue(String property, String value) {
        try {
            jsonPredicate = documentRepository.createJsonContainsPredicate(property, value);
        } catch (RuntimeException e) {
            textWorld.setLastException(e);
        }
    }

    /**
     * Verifies that the predicate references the JSON_CONTAINS function on the property with the JSON-quoted value.
     *
     * @param property expected property reference
     * @param value    expected JSON-quoted value
     */
    @Then("the predicate should reference JSON_CONTAINS on {string} with the JSON-quoted value {string}")
    public void thePredicateShouldReferenceJsonContainsOnWithTheJsonQuotedValue(String property, String value) {
        assertThat(textWorld.getLastException()).as("last exception").isNull();
        assertThat(jsonPredicate.toString())
                .contains("JSON_CONTAINS")
                .contains(property)
                .contains("\"" + value + "\"");
    }

    /**
     * Verifies that the attacker's double quote is JSON-escaped and cannot terminate the SQL string literal early.
     */
    @Then("the malicious double quote should be escaped so it cannot terminate the SQL string literal")
    public void theMaliciousDoubleQuoteShouldBeEscaped() {
        assertThat(jsonPredicate.toString())
                .contains("\\\"d;")
                // no UNESCAPED double quote directly before the payload (the escaped one is preceded by a backslash)
                .doesNotMatch(".*[^\\\\]\"d;.*");
    }

    /**
     * Verifies that an invalid property name is rejected with IllegalArgumentException as the root cause (the
     * repository proxy wraps it into InvalidDataAccessApiUsageException).
     */
    @Then("IllegalArgumentException should be the root cause of the failure")
    public void illegalArgumentShouldBeTheRootCauseOfTheFailure() {
        assertThat(textWorld.getLastException())
                .as("captured exception")
                .isNotNull()
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Invokes andIfNotNull on the repository with a condition lambda recording its invocation.
     *
     * @param valueSupplier supplier for the condition value
     */
    private void invokeAndIfNotNull(Supplier<String> valueSupplier) {
        builder = new BooleanBuilder();
        conditionInvoked = new AtomicBoolean(false);
        documentRepository.andIfNotNull(valueSupplier, builder, value -> {
            conditionInvoked.set(true);
            return TestDocumentRepository.DOCUMENT_PATH.getString(TestDocument.Fields.name).eq(value);
        });
    }

    /**
     * Invokes andIfNotBlank on the repository with a condition lambda recording its invocation.
     *
     * @param valueSupplier supplier for the condition value
     */
    private void invokeAndIfNotBlank(Supplier<String> valueSupplier) {
        builder = new BooleanBuilder();
        conditionInvoked = new AtomicBoolean(false);
        documentRepository.andIfNotBlank(valueSupplier, builder, value -> {
            conditionInvoked.set(true);
            return TestDocumentRepository.DOCUMENT_PATH.getString(TestDocument.Fields.name).eq(value);
        });
    }

    /**
     * Builds a test document referencing an author by ID.
     *
     * @param id       document ID
     * @param name     document name
     * @param userId   owning user ID
     * @param metadata JSON-ish metadata value
     * @param authorId referenced author ID
     * @return new document
     */
    private TestDocument newDocument(String id, String name, String userId, String metadata, String authorId) {
        var author = authorRepository.getReferenceById(authorId);
        return TestDocument.builder()
                .id(id)
                .name(name)
                .userId(userId)
                .metadata(metadata)
                .author(author)
                .build();
    }

    /**
     * Enhanced search repository over a non-FTS entity ({@link TestAuthor}), for pinning the fail-fast rejection of
     * {@code rebuildFullTextSearchData()}. Never registered as a bean: the fragment is instantiated directly in steps,
     * so no schema or repository scaffolding is needed.
     */
    private interface NonFtsSearchRepository
            extends EnhancedJpaSearchRepository<TestAuthor, String, TestDocumentNotFoundException, Void> {

        /**
         * {@inheritDoc}
         */
        @Override
        default BooleanBuilder convertToSearchBuilder(Void filter) {
            return new BooleanBuilder();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        default Page<TestAuthor> findByFilter(Void filter, Pageable pageable) {
            return Page.empty();
        }
    }
}
