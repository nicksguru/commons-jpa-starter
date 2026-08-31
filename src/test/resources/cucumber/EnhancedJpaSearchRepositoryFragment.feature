@db #@disabled
Feature: EnhancedJpaSearchRepositoryFragment functionality
  EnhancedJpaSearchRepositoryFragment should correctly apply filter predicates with pagination and sorting, add
  conditions only for non-null/non-blank values, JSON-quote values against SQL injection, reject invalid property
  names, find entities by ngram fuzzy match via the H2-emulated FULL_TEXT_SEARCH and JSON_CONTAINS functions, and
  batch-rebuild stale FTS ngrams via rebuildFullTextSearchData()

  Scenario: findByFilter applies the filter predicate, pagination and sorting at once
    Given the default documents exist
    When documents are searched with a filter for name "document" and user "user-1" requesting page 1 of size 1 sorted by name
    Then the total elements should be 2
    And the total pages should be 2
    And the page content names should be "Alpha red document"
    When documents are searched with a filter for name "document" and user "user-1" requesting page 2 of size 1 sorted by name
    Then the page content names should be "Beta blue document"

  Scenario: findByFilter without conditions returns everything
    Given the default documents exist
    When documents are searched with a null filter
    Then the total elements should be 3
    When documents are searched with an empty filter
    Then the total elements should be 3

  Scenario: andIfNotNull adds the condition only for non-null values
    When andIfNotNull is invoked with a null value
    Then the condition should not be evaluated and the builder should stay empty
    When andIfNotNull is invoked with a present value
    Then the condition should be evaluated and the builder should have a value

  Scenario: andIfNotBlank adds the condition only for non-blank values
    When andIfNotBlank is invoked with a blank value
    Then the condition should not be evaluated and the builder should stay empty
    When andIfNotBlank is invoked with a non-blank value
    Then the condition should be evaluated and the builder should have a value

  Scenario: createJsonContainsPredicate filters by the JSON value
    Given the default documents exist
    When documents are searched with a filter for metadata color "red"
    Then the page content names should be "Alpha red document"

  Scenario: createJsonContainsPredicate JSON-quotes the value to prevent SQL injection
    When a JSON contains predicate is created for property "metadata" and value "red"
    Then the predicate should reference JSON_CONTAINS on "metadata" with the JSON-quoted value "red"
    When a JSON contains predicate is created for property "metadata" and value "re\"d; DROP TABLE x; --"
    And the malicious double quote should be escaped so it cannot terminate the SQL string literal

  Scenario: createJsonContainsPredicate rejects an invalid property name
    When a JSON contains predicate is created for property "meta;data" and value "red"
    Then IllegalArgumentException should be the root cause of the failure

  Scenario: Full-text search finds entities by ngram fuzzy match
    Given the default documents exist
    When documents are searched with a full-text search for "alpa"
    Then the page content names should be "Alpha red document"
    And the total elements should be 1

  Scenario: Full-text search with null or blank text returns unfiltered results
    Given the default documents exist
    When documents are searched with a null full-text search
    Then the total elements should be 3
    When documents are searched with a blank full-text search
    Then the total elements should be 3

  Scenario: rebuildFullTextSearchData rebuilds stale FTS data
    Given the default documents exist
    When the full-text search data of all documents is corrupted by a bulk JPQL update
    And documents are searched with a full-text search for "alpa"
    Then the total elements should be 0
    When the full-text search data is rebuilt
    Then the number of processed documents should be 3
    When documents are searched with a full-text search for "red document"
    Then the total elements should be 3
    And the first page content name should be "Alpha red document"

  Scenario: rebuildFullTextSearchData returns 0 when there is nothing to rebuild
    When the full-text search data is rebuilt
    Then the number of processed documents should be 0

  Scenario: rebuildFullTextSearchData rejects a repository of a non-FTS entity type
    When the full-text search data is rebuilt for a repository of a non-FTS entity type
    Then IllegalStateException should be thrown
