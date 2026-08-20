package guru.nicks.commons.jpa.it;

import guru.nicks.commons.jpa.it.domain.TestAuthor;
import guru.nicks.commons.jpa.it.domain.TestDocument;
import guru.nicks.commons.jpa.it.domain.TestDocumentFilter;
import guru.nicks.commons.jpa.it.repo.TestAuthorRepository;
import guru.nicks.commons.jpa.it.repo.TestDocumentRepository;

import com.querydsl.core.BooleanBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests pinning {@code EnhancedJpaSearchRepositoryFragment}: filter predicates with pagination/sorting,
 * andIfNotNull/andIfNotBlank helpers, the JSON-contains predicate (executed against the H2-emulated JSON_CONTAINS
 * function) and the full-text search ngram path (executed against the H2-emulated FULL_TEXT_SEARCH function).
 */
@SpringBootTest(classes = JpaItTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("jpa-it")
class EnhancedJpaSearchRepositoryFragmentTests {

    // DI
    @Autowired
    private TestDocumentRepository documentRepository;

    // DI
    @Autowired
    private TestAuthorRepository authorRepository;

    // DI
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void beforeEachTest() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(tx -> {
            documentRepository.deleteAllInBatch();
            authorRepository.deleteAllInBatch();
        });
    }

    /**
     * findByFilter applies the filter predicate, pagination and sorting at once.
     */
    @Test
    void findByFilterAppliesPredicatePaginationAndSorting() {
        persistDefaultDocuments();

        var filter = new TestDocumentFilter("document", "user-1", null, null);

        var firstPage = documentRepository.findByFilter(filter, PageRequest.of(0, 1, Sort.by("name")));
        assertThat(firstPage.getTotalElements()).as("total elements").isEqualTo(2);
        assertThat(firstPage.getTotalPages()).as("total pages").isEqualTo(2);
        assertThat(firstPage.getContent())
                .extracting(TestDocument::getName)
                .containsExactly("Alpha red document");

        var secondPage = documentRepository.findByFilter(filter, PageRequest.of(1, 1, Sort.by("name")));
        assertThat(secondPage.getContent())
                .extracting(TestDocument::getName)
                .containsExactly("Beta blue document");
    }

    /**
     * findByFilter with a null filter or all-null filter fields returns everything.
     */
    @Test
    void findByFilterWithoutConditionsReturnsEverything() {
        persistDefaultDocuments();

        assertThat(documentRepository.findByFilter(null, PageRequest.of(0, 10)).getTotalElements())
                .as("null filter")
                .isEqualTo(3);
        assertThat(documentRepository.findByFilter(
                        new TestDocumentFilter(null, null, null, null), PageRequest.of(0, 10)).getTotalElements())
                .as("empty filter")
                .isEqualTo(3);
    }

    /**
     * andIfNotNull adds the condition only when the supplier yields a non-null value.
     */
    @Test
    void andIfNotNullAddsConditionOnlyForNonNullValues() {
        var builder = new BooleanBuilder();
        var invoked = new AtomicBoolean(false);
        Supplier<String> nullValue = () -> null;
        Supplier<String> presentValue = () -> "Alpha red document";

        documentRepository.andIfNotNull(nullValue, builder, value -> {
            invoked.set(true);
            return TestDocumentRepository.DOCUMENT_PATH.getString(TestDocument.Fields.name).eq(value);
        });
        assertThat(invoked).as("condition evaluated for null value").isFalse();
        assertThat(builder.hasValue()).as("builder after null value").isFalse();

        documentRepository.andIfNotNull(presentValue, builder, value -> {
            invoked.set(true);
            return TestDocumentRepository.DOCUMENT_PATH.getString(TestDocument.Fields.name).eq(value);
        });
        assertThat(invoked).as("condition evaluated for non-null value").isTrue();
        assertThat(builder.hasValue()).as("builder after non-null value").isTrue();
    }

    /**
     * andIfNotBlank adds the condition only when the supplier yields a non-blank value.
     */
    @Test
    void andIfNotBlankAddsConditionOnlyForNonBlankValues() {
        var builder = new BooleanBuilder();
        var invoked = new AtomicBoolean(false);
        Supplier<String> blankValue = () -> " ";
        Supplier<String> presentValue = () -> "text";

        documentRepository.andIfNotBlank(blankValue, builder, value -> {
            invoked.set(true);
            return TestDocumentRepository.DOCUMENT_PATH.getString(TestDocument.Fields.name).eq(value);
        });
        assertThat(invoked).as("condition evaluated for blank value").isFalse();
        assertThat(builder.hasValue()).as("builder after blank value").isFalse();

        documentRepository.andIfNotBlank(presentValue, builder, value -> {
            invoked.set(true);
            return TestDocumentRepository.DOCUMENT_PATH.getString(TestDocument.Fields.name).eq(value);
        });
        assertThat(invoked).as("condition evaluated for non-blank value").isTrue();
        assertThat(builder.hasValue()).as("builder after non-blank value").isTrue();
    }

    /**
     * createJsonContainsPredicate produces a working predicate: executed against the H2-emulated JSON_CONTAINS
     * function, it filters by the JSON-encoded value.
     */
    @Test
    void createJsonContainsPredicateFiltersByJsonValue() {
        persistDefaultDocuments();

        var filter = new TestDocumentFilter(null, null, "red", null);
        var page = documentRepository.findByFilter(filter, PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(TestDocument::getName)
                .containsExactly("Alpha red document");
    }

    /**
     * createJsonContainsPredicate JSON-quotes the value: double quotes are escaped by Jackson and can't terminate
     * the SQL string literal (SQL injection guard).
     */
    @Test
    void createJsonContainsPredicateJsonQuotesValueToPreventSqlInjection() {
        var predicate = documentRepository.createJsonContainsPredicate("metadata", "red");

        assertThat(predicate.toString())
                .contains("JSON_CONTAINS")
                .contains("metadata")
                .contains("\"red\"");

        // the attacker's double quote is JSON-escaped, so it cannot terminate the SQL string literal early
        var malicious = documentRepository.createJsonContainsPredicate("metadata", "re\"d; DROP TABLE x; --");
        assertThat(malicious.toString())
                .contains("\\\"d;")
                // no UNESCAPED double quote directly before the payload (the escaped one is preceded by a backslash)
                .doesNotMatch(".*[^\\\\]\"d;.*");
    }

    /**
     * createJsonContainsPredicate rejects property names that don't exist on the entity. The repository proxy
     * translates the original IllegalArgumentException into InvalidDataAccessApiUsageException, so the root cause
     * is asserted.
     */
    @Test
    void createJsonContainsPredicateRejectsInvalidColumnName() {
        assertThatThrownBy(() -> documentRepository.createJsonContainsPredicate("meta;data", "red"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Full-text search finds entities by ngram (fuzzy) match via the emulated FULL_TEXT_SEARCH function: a
     * deliberately misspelled word still matches.
     */
    @Test
    void findByFilterFindsByNgramFuzzyMatch() {
        persistDefaultDocuments();

        // 'alpa' doesn't occur verbatim anywhere - only the ngram overlap with 'alpha' matches
        var filter = new TestDocumentFilter(null, null, null, "alpa");
        var page = documentRepository.findByFilter(filter, PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(TestDocument::getName)
                .containsExactly("Alpha red document");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    /**
     * Full-text search with null/blank search text returns unfiltered results (no FTS condition applied).
     */
    @Test
    void findByFilterWithoutFullTextSearchReturnsUnfilteredResults() {
        persistDefaultDocuments();

        var nullSearch = new TestDocumentFilter(null, null, null, null);
        var blankSearch = new TestDocumentFilter(null, null, null, " ");

        assertThat(documentRepository.findByFilter(nullSearch, PageRequest.of(0, 10)).getTotalElements())
                .as("null full-text search")
                .isEqualTo(3);
        assertThat(documentRepository.findByFilter(blankSearch, PageRequest.of(0, 10)).getTotalElements())
                .as("blank full-text search")
                .isEqualTo(3);
    }

    private void persistDefaultDocuments() {
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

}
