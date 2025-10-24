@db #@disabled
Feature: Enhanced JPA Search Operations

  Background:
    Given a test JPA repository

  Scenario: Creating entity graph
    When an entity graph is created in the repository
    Then the entity graph should be created in EntityManager

  Scenario Outline: Converting search filter to predicate
    Given a plain search text "<searchText>"
    When the filter is converted to a search predicate
    Then the predicate should <expectedResult>
    Examples:
      | searchText | expectedResult |
      | test       | not be empty   |
      |            | be empty       |
      | null       | be empty       |

  Scenario Outline: Finding entities by filter with full-text search
    Given a plain search text "<searchText>"
    And full-text search text "<fullTextSearchText>"
    And pagination with page 0, size 10, and sort by "<requestedSortBy>" direction "<requestedDirection>"
    When entities are found by filter
    Then the result should be sorted by "<appliedSortBy>" "<appliedSortDirection>"
    Examples:
      | searchText | fullTextSearchText | requestedSortBy | requestedDirection | appliedSortBy | appliedSortDirection | comments                       |
      | test       | search term        | createdDate     | DESC               | createdDate   | DESC                 |                                |
      | test       |                    | createdDate     | ASC                | createdDate   | ASC                  | no FTS                         |
      | test       | search term        | _searchRank     |                    | _searchRank   | DESC                 |                                |
      | test       | search term        |                 | ASC                | _searchRank   | DESC                 | FTS direction enforced to DESC |

  Scenario: Adding search condition if value is not null
    Given a boolean builder
    And a non-null value "test"
    When andIfNotNull is called with the value
    Then the condition should be added to the boolean builder

  Scenario: Not adding search condition if value is null
    Given a boolean builder
    And a null value
    When andIfNotNull is called with the value
    Then the condition should not be added to the boolean builder

  Scenario: Adding search condition if string is not blank
    Given a boolean builder
    And a non-blank string "test"
    When andIfNotBlank is called with the string
    Then the condition should be added to the boolean builder

  Scenario: Not adding search condition if string is blank
    Given a boolean builder
    And a blank string " "
    When andIfNotBlank is called with the string
    Then the condition should not be added to the boolean builder
    And no exception should be thrown

  Scenario: Creating JSON contains predicate
    Given a property name "jsonData"
    And a JSON value
      | key1 | value1 |
      | key2 | value2 |
    When a JSON contains predicate is created
    Then the predicate should contain the JSON template

  Scenario: Applying pagination and sort to query
    Given pagination with page 1, size 20, and sort by "name" direction "ASC"
    When pagination and sort are applied to the query
    Then the query should have offset 20 and limit 20
    And the query should be sorted by "name" direction "ASC"
