package guru.nicks.commons.jpa.it;

import guru.nicks.commons.jpa.domain.EnhancedSqlDialect;
import guru.nicks.commons.jpa.it.domain.TestAuthor;
import guru.nicks.commons.jpa.it.domain.TestDocument;
import guru.nicks.commons.jpa.it.domain.TestDocumentNotFoundException;
import guru.nicks.commons.jpa.it.repo.TestAuthorRepository;
import guru.nicks.commons.jpa.it.repo.TestDocumentRepository;
import guru.nicks.commons.jpa.repository.EnhancedJpaRepositoryFragment;

import com.querydsl.core.types.dsl.PathBuilderFactory;
import jakarta.persistence.EntityManager;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression tests pinning the hybrid fragment infrastructure of {@code EnhancedJpaRepository}: fragment method routing
 * (getById), Querydsl execution on the same proxy, transactional semantics of fragment methods, batch saves,
 * order-preserving reads, entity graphs, and the untouched stock repository path. All calls run against a real
 * EntityManager backed by H2.
 */
@SpringBootTest(classes = JpaItTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("jpa-it")
class EnhancedJpaRepositoryFragmentTests {

    // DI
    @Autowired
    private TestDocumentRepository documentRepository;

    // DI
    @Autowired
    private TestAuthorRepository authorRepository;

    // DI
    @Autowired
    private EntityManager entityManager;

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
     * Fragment metadata (entity/exception class, dialect, exception class) is correctly inferred from repository
     * generics.
     */
    @Test
    void fragmentMetadataIsInferredFromRepositoryGenerics() {
        assertThat(documentRepository.getEntityClass()).isEqualTo(TestDocument.class);
        assertThat(documentRepository.getExceptionClass()).isEqualTo(TestDocumentNotFoundException.class);
        assertThat(documentRepository.getSqlDialect()).isEqualTo(EnhancedSqlDialect.POSTGRES);
        assertThat(documentRepository.getExceptionClass()).isAssignableFrom(TestDocumentNotFoundException.class);
    }

    /**
     * getById returns the persisted entity (a real instance, not a lazy proxy reference) when the entity exists.
     */
    @Test
    void getByIdReturnsPersistedEntityWhenFound() {
        var author = persistAuthor("author-1", "Alice Author");
        persistDocument("doc-1", "Alpha document", "user-1", null, author);

        var document = documentRepository.getById("doc-1");

        // the fragment must win over the deprecated SimpleJpaRepository.getById which returns a lazy reference
        assertThat(document.getClass()).isEqualTo(TestDocument.class);
        assertThat(document.getName()).isEqualTo("Alpha document");
    }

    /**
     * getById throws the configured exception type when the entity is absent.
     */
    @Test
    void getByIdThrowsConfiguredExceptionWhenMissing() {
        assertThatThrownBy(() -> documentRepository.getById("missing"))
                .isInstanceOf(TestDocumentNotFoundException.class);
    }

    /**
     * Querydsl methods (findOne/findAll/count) route to the stock Spring Data Querydsl fragment on the same proxy.
     */
    @Test
    void querydslExecutorWorksOnTheSameProxy() {
        var author = persistAuthor("author-1", "Alice Author");
        persistDocument("doc-1", "Alpha document", "user-1", null, author);
        persistDocument("doc-2", "Beta document", "user-1", null, author);
        persistDocument("doc-3", "Gamma document", "user-2", null, author);

        var document = new PathBuilderFactory().create(TestDocument.class);

        assertThat(documentRepository.findOne(document.getString(TestDocument.Fields.name).eq("Beta document")))
                .as("findOne by predicate")
                .isPresent();

        assertThat(documentRepository.findAll(document.getString(TestDocument.Fields.userId).eq("user-1"),
                Sort.by(TestDocument.Fields.name)))
                .as("findAll by predicate, sorted")
                .extracting(TestDocument::getName)
                .containsExactly("Alpha document", "Beta document");

        assertThat(documentRepository.count(document.getString(TestDocument.Fields.userId).eq("user-1")))
                .as("count by predicate")
                .isEqualTo(2);
    }

    /**
     * A fragment read method that uses the EntityManager directly (findByIdWithFetchGraph) runs inside a read-only
     * transaction: without the fragment's class-level @Transactional(readOnly = true) there would be no transaction at
     * all during the EntityManager#find call.
     */
    @Test
    void fragmentReadMethodRunsInReadOnlyTransaction() {
        var author = persistAuthor("author-1", "Alice Author");
        persistDocument("doc-1", "Alpha document", "user-1", null, author);

        var graph = documentRepository.createEntityGraph();
        graph.addAttributeNodes(TestDocument.Fields.author);

        TransactionInspector.startRecording();
        documentRepository.findByIdWithFetchGraph("doc-1", graph);

        var snapshot = TransactionInspector.snapshots().getLast();
        assertThat(snapshot.transactionActive()).isTrue();
        assertThat(snapshot.readOnly()).isTrue();
    }

    /**
     * saveAllAndFlushInBatches runs all batches inside ONE read-write transaction started by the fragment method: inner
     * save()/flush() calls join it instead of opening their own transactions.
     */
    @Test
    void saveAllAndFlushInBatchesRunsInSingleReadWriteTransaction() {
        var author = persistAuthor("author-1", "Alice Author");
        var entities = IntStream.rangeClosed(1, 2)
                .mapToObj(i -> newDocument("tx-doc-" + i, "Tx document " + i, "user-1", author))
                .toList();

        TransactionInspector.startRecording();
        documentRepository.saveAllAndFlushInBatches(entities, 1);

        var snapshots = TransactionInspector.snapshots().stream()
                .filter(snapshot -> "persist".equals(snapshot.event()))
                .toList();
        assertThat(snapshots).hasSize(2);
        snapshots.forEach(snapshot -> {
            assertThat(snapshot.transactionActive()).isTrue();
            assertThat(snapshot.readOnly()).isFalse();
        });

        // both inserts happened in the same EntityManager/transaction - per-batch transactions would differ
        assertThat(snapshots.getFirst().transactionIdentity())
                .isEqualTo(snapshots.getLast().transactionIdentity());
    }

    /**
     * saveAllAndFlushInBatches persists all entities (crossing batch boundaries), returns them in input order and
     * clears the persistence context.
     */
    @Test
    void saveAllAndFlushInBatchesPersistsAllInOrderAndClearsPersistenceContext() {
        var author = persistAuthor("author-1", "Alice Author");
        var entities = IntStream.rangeClosed(1, 7)
                .mapToObj(i -> newDocument("batch-doc-" + i, "Batch document " + i, "user-1", author))
                .toList();

        var result = transactionTemplate.execute(tx -> {
            var saved = documentRepository.saveAllAndFlushInBatches(entities, 3);

            // checked inside the same transaction: the fragment must have cleared the persistence context
            var anyStillManaged = saved.stream().anyMatch(entityManager::contains);
            return Map.entry(saved, anyStillManaged);
        });

        assertThat(result.getKey())
                .as("saved entities, in input order")
                .extracting(TestDocument::getId)
                .containsExactlyElementsOf(entities.stream().map(TestDocument::getId).toList());
        assertThat(result.getValue())
                .as("at least one entity still managed by the persistence context")
                .isFalse();
        assertThat(documentRepository.count())
                .as("all entities persisted")
                .isEqualTo(7);
    }

    /**
     * The single-argument saveAllAndFlushInBatches overload persists everything using the default batch size.
     */
    @Test
    void saveAllAndFlushInBatchesWithDefaultBatchSizePersistsEntities() {
        var author = persistAuthor("author-1", "Alice Author");
        var entities = List.of(
                newDocument("single-batch-1", "Single batch one", "user-1", author),
                newDocument("single-batch-2", "Single batch two", "user-1", author));

        var saved = documentRepository.saveAllAndFlushInBatches(entities);

        assertThat(saved)
                .extracting(TestDocument::getId)
                .containsExactly("single-batch-1", "single-batch-2");
        assertThat(documentRepository.count()).isEqualTo(2);
    }

    /**
     * findAllByIdPreserveOrder returns entities in the REQUEST order regardless of the DB natural order.
     */
    @Test
    void findAllByIdPreserveOrderReturnsEntitiesInRequestOrder() {
        var author = persistAuthor("author-1", "Alice Author");
        // persist in an order deliberately different from the request order
        persistDocument("doc-b", "Bravo", "user-1", null, author);
        persistDocument("doc-d", "Delta", "user-1", null, author);
        persistDocument("doc-a", "Alpha", "user-1", null, author);
        persistDocument("doc-c", "Charlie", "user-1", null, author);

        var found = documentRepository.findAllByIdPreserveOrder(List.of("doc-d", "doc-b", "doc-a"));

        assertThat(found)
                .extracting(TestDocument::getId)
                .containsExactly("doc-d", "doc-b", "doc-a");
    }

    /**
     * findAllByIdPreserveOrder deduplicates IDs by first occurrence: a repeated ID doesn't duplicate the entity nor
     * change its position.
     */
    @Test
    void findAllByIdPreserveOrderDeduplicatesByFirstOccurrence() {
        var author = persistAuthor("author-1", "Alice Author");
        persistDocument("doc-a", "Alpha", "user-1", null, author);
        persistDocument("doc-b", "Bravo", "user-1", null, author);
        persistDocument("doc-c", "Charlie", "user-1", null, author);

        var found = documentRepository.findAllByIdPreserveOrder(List.of("doc-c", "doc-a", "doc-c", "doc-b"));

        assertThat(found)
                .extracting(TestDocument::getId)
                .containsExactly("doc-c", "doc-a", "doc-b");
    }

    /**
     * createEntityGraph + findByIdWithFetchGraph eagerly fetches the lazy association: it's accessible on the detached
     * entity without a LazyInitializationException, unlike a plain findById.
     */
    @Test
    void findByIdWithFetchGraphEagerlyFetchesAssociation() {
        var author = persistAuthor("author-1", "Alice Author");
        persistDocument("doc-1", "Alpha document", "user-1", null, author);

        var graph = documentRepository.createEntityGraph();
        graph.addAttributeNodes(TestDocument.Fields.author);

        var fetched = transactionTemplate.execute(tx ->
                documentRepository.findByIdWithFetchGraph("doc-1", graph)).orElseThrow();

        // entity is detached after the transaction, but the association was eagerly fetched
        assertThat(fetched.getAuthor().getName()).isEqualTo("Alice Author");

        // negative control: without the fetch graph, the lazy association can't be initialized when detached
        var lazy = transactionTemplate.execute(tx -> documentRepository.findById("doc-1")).orElseThrow();
        assertThatThrownBy(() -> lazy.getAuthor().getName())
                .isInstanceOf(LazyInitializationException.class);
    }

    /**
     * A plain repository NOT extending the Enhanced interfaces still works normally in the same context (stock path
     * unaffected by the custom repository factory bean).
     */
    @Test
    void plainRepositoryStillWorksInSameContext() {
        var author = TestAuthor.builder().id("author-9").name("Plain Author").build();
        authorRepository.save(author);

        assertThat(authorRepository.findById("author-9")).isPresent();
        assertThat(authorRepository.count()).isEqualTo(1);
        // the stock repository proxy must not implement the custom fragment interface
        assertThat(authorRepository).isNotInstanceOf(EnhancedJpaRepositoryFragment.class);
    }

    private TestAuthor persistAuthor(String id, String name) {
        return transactionTemplate.execute(tx ->
                authorRepository.save(TestAuthor.builder().id(id).name(name).build()));
    }

    private TestDocument persistDocument(String id, String name, String userId, String metadata, TestAuthor author) {
        return transactionTemplate.execute(tx ->
                documentRepository.save(newDocument(id, name, userId, metadata, author)));
    }

    private TestDocument newDocument(String id, String name, String userId, TestAuthor author) {
        return newDocument(id, name, userId, null, author);
    }

    private TestDocument newDocument(String id, String name, String userId, String metadata, TestAuthor author) {
        return TestDocument.builder()
                .id(id)
                .name(name)
                .userId(userId)
                .metadata(metadata)
                .author(author)
                .build();
    }

}
