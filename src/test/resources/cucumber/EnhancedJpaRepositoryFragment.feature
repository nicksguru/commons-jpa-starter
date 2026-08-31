@db #@disabled
Feature: EnhancedJpaRepositoryFragment functionality
  EnhancedJpaRepositoryFragment should correctly infer fragment metadata from repository generics, route fragment
  methods and the Querydsl executor on the same proxy, run fragment reads in read-only transactions, save in batches
  inside one read-write transaction, preserve read order, eagerly fetch lazy associations via entity graphs, and leave
  stock repositories untouched

  Scenario: Fragment metadata is inferred from repository generics
    Then the fragment entity class should be TestDocument
    And the fragment exception class should be TestDocumentNotFoundException
    And the fragment SQL dialect should be POSTGRES
    And the fragment exception class should be assignable from TestDocumentNotFoundException

  Scenario: getById returns the persisted entity when found
    Given an author "author-1" named "Alice Author"
    And a document "doc-1" named "Alpha document" owned by "user-1"
    When the document "doc-1" is retrieved by ID
    Then the retrieved document should be a real instance named "Alpha document"

  Scenario: getById throws the configured exception when the entity is missing
    When the document "missing" is retrieved by ID
    Then TestDocumentNotFoundException should be thrown

  Scenario: Querydsl executor works on the same proxy
    Given an author "author-1" named "Alice Author"
    And a document "doc-1" named "Alpha document" owned by "user-1"
    And a document "doc-2" named "Beta document" owned by "user-1"
    And a document "doc-3" named "Gamma document" owned by "user-2"
    When a document is searched by exact name "Beta document"
    Then the document should be found
    When documents are searched by user "user-1" sorted by name
    Then the found document names should be "Alpha document,Beta document"
    And the document count for user "user-1" should be 2

  Scenario: Fragment read method runs in a read-only transaction
    Given an author "author-1" named "Alice Author"
    And a document "doc-1" named "Alpha document" owned by "user-1"
    When the document "doc-1" is fetched with an entity graph for the author
    Then the fetch should have run inside an active read-only transaction

  Scenario: saveAllAndFlushInBatches runs in a single read-write transaction
    Given an author "author-1" named "Alice Author"
    And 2 new documents "tx-doc" owned by "user-1"
    When the new documents are saved in batches of 1
    Then all batches should run inside one active read-write transaction

  Scenario: saveAllAndFlushInBatches persists all in order and clears the persistence context
    Given an author "author-1" named "Alice Author"
    And 7 new documents "batch-doc" owned by "user-1"
    When the new documents are saved in batches of 3 within a surrounding transaction
    Then the saved documents should be in input order
    And the persistence context should be cleared
    And the document count should be 7

  Scenario: saveAllAndFlushInBatches with the default batch size persists entities
    Given an author "author-1" named "Alice Author"
    And 2 new documents "single-batch" owned by "user-1"
    When the new documents are saved in batches with the default batch size
    Then the saved documents should be in input order
    And the document count should be 2

  Scenario: findAllByIdPreserveOrder returns entities in request order
    Given an author "author-1" named "Alice Author"
    And documents "doc-b,doc-d,doc-a,doc-c" owned by "user-1"
    When documents are found by IDs "doc-d,doc-b,doc-a" preserving order
    Then the found document IDs should be "doc-d,doc-b,doc-a"

  Scenario: findAllByIdPreserveOrder deduplicates IDs by first occurrence
    Given an author "author-1" named "Alice Author"
    And documents "doc-a,doc-b,doc-c" owned by "user-1"
    When documents are found by IDs "doc-c,doc-a,doc-c,doc-b" preserving order
    Then the found document IDs should be "doc-c,doc-a,doc-b"

  Scenario: findByIdWithFetchGraph eagerly fetches the lazy association
    Given an author "author-1" named "Alice Author"
    And a document "doc-1" named "Alpha document" owned by "user-1"
    When the document "doc-1" is fetched with an entity graph for the author
    Then the author association should be eagerly initialized with name "Alice Author"
    When the document "doc-1" is fetched without an entity graph
    Then accessing the author association should throw LazyInitializationException

  Scenario: Stock repository is unaffected in the same context
    When a plain author "author-9" named "Plain Author" is saved via the stock repository
    Then the stock repository should find the author
    And the stock repository should not implement the enhanced fragment
