package guru.nicks.commons.cucumber;

import guru.nicks.commons.cucumber.world.TextWorld;
import guru.nicks.commons.jpa.domain.EnhancedSqlDialect;
import guru.nicks.commons.jpa.it.TransactionInspector;
import guru.nicks.commons.jpa.it.domain.TestAuthor;
import guru.nicks.commons.jpa.it.domain.TestDocument;
import guru.nicks.commons.jpa.it.domain.TestDocumentNotFoundException;
import guru.nicks.commons.jpa.it.repo.TestAuthorRepository;
import guru.nicks.commons.jpa.it.repo.TestDocumentRepository;
import guru.nicks.commons.jpa.repository.EnhancedJpaRepositoryFragment;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.LazyInitializationException;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Step definitions pinning the hybrid fragment infrastructure of {@code EnhancedJpaRepository}: fragment method routing
 * (getById), Querydsl execution on the same proxy, transactional semantics of fragment methods, batch saves,
 * order-preserving reads, entity graphs, and the untouched stock repository path. All calls run against a real
 * EntityManager backed by H2.
 */
@RequiredArgsConstructor
public class EnhancedJpaRepositoryFragmentSteps {

    // DI
    private final TestDocumentRepository documentRepository;

    // DI
    private final TestAuthorRepository authorRepository;

    // DI
    private final EntityManager entityManager;

    // DI
    private final PlatformTransactionManager transactionManager;

    // DI
    private final TextWorld textWorld;

    private TransactionTemplate transactionTemplate;
    private TestAuthor lastAuthor;
    private List<String> newDocumentIds;
    private String newDocumentUserId;
    private TestDocument retrievedDocument;
    private Optional<TestDocument> foundDocument;
    private Iterable<TestDocument> foundDocuments;
    private List<TestDocument> savedDocuments;
    private boolean anyStillManaged;
    private TestDocument fetchedWithGraph;
    private TestDocument lazyDocument;
    private String lastSavedAuthorId;

    /**
     * Replicates the JUnit {@code @BeforeEach} cleanup: every scenario starts with empty tables.
     */
    @Before
    public void beforeEachScenario() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(tx -> {
            documentRepository.deleteAllInBatch();
            authorRepository.deleteAllInBatch();
        });
    }

    /**
     * Verifies fragment metadata inference from repository generics.
     *
     * @param simpleName expected entity class simple name
     */
    @Then("the fragment entity class should be {word}")
    public void theFragmentEntityClassShouldBe(String simpleName) {
        assertThat(documentRepository.getEntityClass())
                .as("entity class")
                .isEqualTo(domainClass(simpleName));
    }

    /**
     * Verifies fragment metadata inference from repository generics.
     *
     * @param simpleName expected exception class simple name
     */
    @Then("the fragment exception class should be {word}")
    public void theFragmentExceptionClassShouldBe(String simpleName) {
        assertThat(documentRepository.getExceptionClass())
                .as("exception class")
                .isEqualTo(domainClass(simpleName));
    }

    /**
     * Verifies fragment metadata inference from repository generics.
     *
     * @param dialectName expected SQL dialect name
     */
    @Then("the fragment SQL dialect should be {word}")
    public void theFragmentSqlDialectShouldBe(String dialectName) {
        assertThat(documentRepository.getSqlDialect())
                .as("SQL dialect")
                .isEqualTo(EnhancedSqlDialect.valueOf(dialectName));
    }

    /**
     * Verifies that the inferred exception class is assignable from the declared exception type.
     *
     * @param simpleName exception class simple name to assign from
     */
    @Then("the fragment exception class should be assignable from {word}")
    public void theFragmentExceptionClassShouldBeAssignableFrom(String simpleName) {
        assertThat(documentRepository.getExceptionClass())
                .as("exception class assignability")
                .isAssignableFrom(domainClass(simpleName));
    }

    /**
     * Persists an author referenced by the documents of the same scenario.
     *
     * @param id   author ID
     * @param name author name
     */
    @Given("an author {string} named {string}")
    public void anAuthorNamed(String id, String name) {
        lastAuthor = persistAuthor(id, name);
    }

    /**
     * Persists a single document owned by the previously persisted author.
     *
     * @param id     document ID
     * @param name   document name
     * @param userId owning user ID
     */
    @Given("a document {string} named {string} owned by {string}")
    public void aDocumentNamedOwnedBy(String id, String name, String userId) {
        persistDocument(id, name, userId, lastAuthor);
    }

    /**
     * Persists multiple documents (comma-separated IDs) owned by the previously persisted author.
     *
     * @param ids    comma-separated document IDs
     * @param userId owning user ID
     */
    @Given("documents {string} owned by {string}")
    public void documentsOwnedBy(String ids, String userId) {
        for (var id : ids.split(",")) {
            persistDocument(id, "Document " + id, userId, lastAuthor);
        }
    }

    /**
     * Prepares new (not yet persisted) documents with generated IDs for batch-save scenarios.
     *
     * @param count  number of documents
     * @param prefix ID prefix, suffixes are appended as '-1', '-2', ...
     * @param userId owning user ID
     */
    @Given("{int} new documents {string} owned by {string}")
    public void newDocumentsOwnedBy(int count, String prefix, String userId) {
        newDocumentIds = IntStream.rangeClosed(1, count)
                .mapToObj(i -> prefix + "-" + i)
                .toList();
        newDocumentUserId = userId;
    }

    /**
     * Retrieves a document by ID via the fragment's getById.
     *
     * @param id document ID
     */
    @When("the document {string} is retrieved by ID")
    public void theDocumentIsRetrievedById(String id) {
        try {
            retrievedDocument = documentRepository.getById(id);
        } catch (RuntimeException e) {
            textWorld.setLastException(e);
        }
    }

    /**
     * Verifies that getById returned a real entity instance (not a lazy proxy reference).
     *
     * @param name expected document name
     */
    @Then("the retrieved document should be a real instance named {string}")
    public void theRetrievedDocumentShouldBeARealInstanceNamed(String name) {
        assertThat(textWorld.getLastException()).as("last exception").isNull();

        // the fragment must win over the deprecated SimpleJpaRepository.getById which returns a lazy reference
        assertThat(retrievedDocument.getClass()).isEqualTo(TestDocument.class);
        assertThat(retrievedDocument.getName()).isEqualTo(name);
    }

    /**
     * Verifies that getById threw the configured exception type.
     */
    @Then("TestDocumentNotFoundException should be thrown")
    public void testDocumentNotFoundExceptionShouldBeThrown() {
        assertThat(textWorld.getLastException())
                .as("last exception")
                .isInstanceOf(TestDocumentNotFoundException.class);
    }

    /**
     * Runs findOne by exact name via the stock Querydsl fragment.
     *
     * @param name document name to match
     */
    @When("a document is searched by exact name {string}")
    public void aDocumentIsSearchedByExactName(String name) {
        foundDocument = documentRepository.findOne(
                TestDocumentRepository.DOCUMENT_PATH.getString(TestDocument.Fields.name).eq(name));
    }

    /**
     * Verifies that the Querydsl findOne predicate matched.
     */
    @Then("the document should be found")
    public void theDocumentShouldBeFound() {
        assertThat(foundDocument).as("findOne by predicate").isPresent();
    }

    /**
     * Runs findAll by user ID sorted by name via the stock Querydsl fragment.
     *
     * @param userId user ID to match
     */
    @When("documents are searched by user {string} sorted by name")
    public void documentsAreSearchedByUserSortedByName(String userId) {
        foundDocuments = documentRepository.findAll(
                TestDocumentRepository.DOCUMENT_PATH.getString(TestDocument.Fields.userId).eq(userId),
                Sort.by(TestDocument.Fields.name));
    }

    /**
     * Verifies the names of documents found by the Querydsl executor.
     *
     * @param names comma-separated expected names, in order
     */
    @Then("the found document names should be {string}")
    public void theFoundDocumentNamesShouldBe(String names) {
        assertThat(foundDocuments)
                .as("findAll by predicate, sorted")
                .extracting(TestDocument::getName)
                .containsExactly(names.split(","));
    }

    /**
     * Verifies the Querydsl count by user ID predicate.
     *
     * @param userId   user ID to match
     * @param expected expected count
     */
    @Then("the document count for user {string} should be {int}")
    public void theDocumentCountForUserShouldBe(String userId, int expected) {
        assertThat(documentRepository.count(
                TestDocumentRepository.DOCUMENT_PATH.getString(TestDocument.Fields.userId).eq(userId)))
                .as("count by predicate")
                .isEqualTo(expected);
    }

    /**
     * Fetches a document via findByIdWithFetchGraph with an entity graph on the lazy author association, without a
     * surrounding transaction: the fragment's class-level read-only transaction must start on its own.
     *
     * @param id document ID
     */
    @When("the document {string} is fetched with an entity graph for the author")
    public void theDocumentIsFetchedWithAnEntityGraphForTheAuthor(String id) {
        var graph = documentRepository.createEntityGraph();
        graph.addAttributeNodes(TestDocument.Fields.author);

        TransactionInspector.startRecording();
        fetchedWithGraph = documentRepository.findByIdWithFetchGraph(id, graph).orElse(null);
    }

    /**
     * Verifies that the fragment read method ran inside an active read-only transaction.
     */
    @Then("the fetch should have run inside an active read-only transaction")
    public void theFetchShouldHaveRunInsideAnActiveReadOnlyTransaction() {
        var snapshot = TransactionInspector.snapshots().getLast();
        assertThat(snapshot.transactionActive()).as("transaction active").isTrue();
        assertThat(snapshot.readOnly()).as("read-only transaction").isTrue();
    }

    /**
     * Verifies that the fetch graph eagerly initialized the lazy association on the detached entity.
     *
     * @param name expected author name
     */
    @Then("the author association should be eagerly initialized with name {string}")
    public void theAuthorAssociationShouldBeEagerlyInitializedWithName(String name) {
        // entity is detached after the fragment transaction, but the association was eagerly fetched
        assertThat(fetchedWithGraph.getAuthor().getName()).isEqualTo(name);
    }

    /**
     * Fetches a document via plain findById inside a transaction that ends before the assertion (negative control).
     *
     * @param id document ID
     */
    @When("the document {string} is fetched without an entity graph")
    public void theDocumentIsFetchedWithoutAnEntityGraph(String id) {
        lazyDocument = transactionTemplate.execute(tx -> documentRepository.findById(id)).orElseThrow();
    }

    /**
     * Verifies that accessing the lazy association on the detached entity fails without the fetch graph.
     */
    @Then("accessing the author association should throw LazyInitializationException")
    public void accessingTheAuthorAssociationShouldThrowLazyInitializationException() {
        // negative control: without the fetch graph, the lazy association can't be initialized when detached
        assertThatThrownBy(() -> lazyDocument.getAuthor().getName())
                .isInstanceOf(LazyInitializationException.class);
    }

    /**
     * Saves the new documents in batches without a surrounding transaction: the fragment method must start its own
     * read-write transaction that inner save()/flush() calls join.
     *
     * @param batchSize batch size
     */
    @When("the new documents are saved in batches of {int}")
    public void theNewDocumentsAreSavedInBatchesOf(int batchSize) {
        TransactionInspector.startRecording();
        savedDocuments = documentRepository.saveAllAndFlushInBatches(newDocumentEntities(), batchSize);
    }

    /**
     * Verifies that all batches ran inside ONE active read-write transaction with the same EntityManager identity.
     */
    @Then("all batches should run inside one active read-write transaction")
    public void allBatchesShouldRunInsideOneActiveReadWriteTransaction() {
        var snapshots = TransactionInspector.snapshots().stream()
                .filter(snapshot -> "persist".equals(snapshot.event()))
                .toList();
        assertThat(snapshots).as("persist snapshots").hasSize(2);
        snapshots.forEach(snapshot -> {
            assertThat(snapshot.transactionActive()).as("transaction active").isTrue();
            assertThat(snapshot.readOnly()).as("read-only transaction").isFalse();
        });

        // both inserts happened in the same EntityManager/transaction - per-batch transactions would differ
        assertThat(snapshots.getFirst().transactionIdentity())
                .isEqualTo(snapshots.getLast().transactionIdentity());
    }

    /**
     * Saves the new documents in batches inside a surrounding transaction so that persistence-context clearing can be
     * checked from within the same transaction.
     *
     * @param batchSize batch size
     */
    @When("the new documents are saved in batches of {int} within a surrounding transaction")
    public void theNewDocumentsAreSavedInBatchesOfWithinASurroundingTransaction(int batchSize) {
        transactionTemplate.executeWithoutResult(tx -> {
            savedDocuments = documentRepository.saveAllAndFlushInBatches(newDocumentEntities(), batchSize);

            // checked inside the same transaction: the fragment must have cleared the persistence context
            anyStillManaged = savedDocuments.stream().anyMatch(entityManager::contains);
        });
    }

    /**
     * Saves the new documents via the single-argument overload using the default batch size.
     */
    @When("the new documents are saved in batches with the default batch size")
    public void theNewDocumentsAreSavedInBatchesWithTheDefaultBatchSize() {
        savedDocuments = documentRepository.saveAllAndFlushInBatches(newDocumentEntities());
    }

    /**
     * Verifies that the saved entities are returned in input order.
     */
    @Then("the saved documents should be in input order")
    public void theSavedDocumentsShouldBeInInputOrder() {
        assertThat(savedDocuments)
                .as("saved entities, in input order")
                .extracting(TestDocument::getId)
                .containsExactlyElementsOf(newDocumentIds);
    }

    /**
     * Verifies that the fragment cleared the persistence context during batch saving.
     */
    @Then("the persistence context should be cleared")
    public void thePersistenceContextShouldBeCleared() {
        assertThat(anyStillManaged)
                .as("at least one entity still managed by the persistence context")
                .isFalse();
    }

    /**
     * Verifies the total document count in the database.
     *
     * @param expected expected count
     */
    @Then("the document count should be {int}")
    public void theDocumentCountShouldBe(int expected) {
        assertThat(documentRepository.count())
                .as("all entities persisted")
                .isEqualTo(expected);
    }

    /**
     * Runs findAllByIdPreserveOrder with the requested IDs.
     *
     * @param ids comma-separated IDs to request
     */
    @When("documents are found by IDs {string} preserving order")
    public void documentsAreFoundByIdsPreservingOrder(String ids) {
        foundDocuments = documentRepository.findAllByIdPreserveOrder(List.of(ids.split(",")));
    }

    /**
     * Verifies the IDs of documents returned by order-preserving reads.
     *
     * @param ids comma-separated expected IDs, in order
     */
    @Then("the found document IDs should be {string}")
    public void theFoundDocumentIdsShouldBe(String ids) {
        assertThat(foundDocuments)
                .extracting(TestDocument::getId)
                .containsExactly(ids.split(","));
    }

    /**
     * Saves a plain author via the stock repository (no Enhanced interface).
     *
     * @param id   author ID
     * @param name author name
     */
    @When("a plain author {string} named {string} is saved via the stock repository")
    public void aPlainAuthorNamedIsSavedViaTheStockRepository(String id, String name) {
        authorRepository.save(TestAuthor.builder().id(id).name(name).build());
        lastSavedAuthorId = id;
    }

    /**
     * Verifies that the stock repository works normally in the same context.
     */
    @Then("the stock repository should find the author")
    public void theStockRepositoryShouldFindTheAuthor() {
        assertThat(authorRepository.findById(lastSavedAuthorId)).as("found by ID").isPresent();
        assertThat(authorRepository.count()).as("author count").isEqualTo(1);
    }

    /**
     * Verifies that the stock repository proxy does not implement the custom fragment interface.
     */
    @Then("the stock repository should not implement the enhanced fragment")
    public void theStockRepositoryShouldNotImplementTheEnhancedFragment() {
        assertThat(authorRepository).isNotInstanceOf(EnhancedJpaRepositoryFragment.class);
    }

    /**
     * Persists an author in its own transaction.
     *
     * @param id   author ID
     * @param name author name
     * @return persisted (detached after the transaction) author
     */
    private TestAuthor persistAuthor(String id, String name) {
        return transactionTemplate.execute(tx ->
                authorRepository.save(TestAuthor.builder().id(id).name(name).build()));
    }

    /**
     * Persists a document in its own transaction.
     *
     * @param id     document ID
     * @param name   document name
     * @param userId owning user ID
     * @param author author association
     * @return persisted (detached after the transaction) document
     */
    private TestDocument persistDocument(String id, String name, String userId, TestAuthor author) {
        return transactionTemplate.execute(tx ->
                documentRepository.save(newDocument(id, name, userId, author)));
    }

    /**
     * Builds the new documents prepared by the 'new documents' Given step.
     *
     * @return unsaved documents referencing the last persisted author
     */
    private List<TestDocument> newDocumentEntities() {
        return newDocumentIds.stream()
                .map(id -> newDocument(id, "Document " + id, newDocumentUserId, lastAuthor))
                .toList();
    }

    /**
     * Builds a test document.
     *
     * @param id     document ID
     * @param name   document name
     * @param userId owning user ID
     * @param author author association
     * @return new document
     */
    private TestDocument newDocument(String id, String name, String userId, TestAuthor author) {
        return TestDocument.builder()
                .id(id)
                .name(name)
                .userId(userId)
                .author(author)
                .build();
    }

    /**
     * Resolves a test domain class by its simple name.
     *
     * @param simpleName simple class name used in the feature file
     * @return domain class
     * @throws IllegalArgumentException simple name is unknown
     */
    private Class<?> domainClass(String simpleName) {
        return switch (simpleName) {
            case "TestDocument" -> TestDocument.class;
            case "TestDocumentNotFoundException" -> TestDocumentNotFoundException.class;
            default -> throw new IllegalArgumentException("Unknown test domain class: " + simpleName);
        };
    }
}
