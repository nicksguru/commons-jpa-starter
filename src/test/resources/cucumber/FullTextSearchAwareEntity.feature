@db #@disabled
Feature: FullTextSearchAwareEntity functionality
  FullTextSearchAwareEntity should correctly handle full-text search data and sorting criteria

  Scenario Outline: Initializing sort criteria based on search text
    Given a search filter with text "<searchText>"
    When sort criteria are initialized with page <pageNumber> and size <pageSize>
    Then the pageable should have page <pageNumber> and size <pageSize>
    And the pageable should sort by "<expectedSortField>" in "<expectedSortDirection>" direction
    Examples:
      | searchText   | pageNumber | pageSize | expectedSortField | expectedSortDirection |
      | search terms | 0          | 10       | _searchRank       | DESC                  |
      |              | 0          | 10       | createdDate       | DESC                  |
      | null         | 0          | 10       | createdDate       | DESC                  |
      | search terms | 1          | 20       | _searchRank       | DESC                  |

  Scenario: Initializing sort criteria with existing sort criteria not by search rank
    Given a search filter with text "search terms"
    And existing sort criteria by "lastModifiedDate" in "ASC" direction
    When sort criteria are initialized with page 0 and size 10
    Then the pageable should have page 0 and size 10
    And the pageable should sort by "lastModifiedDate" in "ASC" direction

  Scenario: Initializing sort criteria with existing sort criteria by search rank (DESC must be enforced)
    Given a search filter with text "search terms"
    And existing sort criteria by "_searchRank" in "ASC" direction
    When sort criteria are initialized with page 0 and size 10
    Then the pageable should have page 0 and size 10
    And the pageable should sort by "_searchRank" in "DESC" direction

  Scenario Outline: Assigning full-text search data
    Given a test entity with search data "<searchData>"
    When full-text search data is collected
    Then the full-text search data should contain ngrams from "<searchData>"
    And the full-text search data length should not exceed the dialect maximum
    Examples:
      | searchData                                                                                                           |
      | simple search text                                                                                                   |
      | text with special characters: !@#$%^&*()                                                                             |
      | text with accented characters: áéíóú                                                                                 |
      | very long text that repeats many times very long text that repeats many times very long text that repeats many times |

  Scenario: Checksum prevents regeneration when search data hasn't changed
    Given a test entity with search data "initial search content"
    When full-text search data is collected
    Then the search data checksum should be calculated and stored
    When full-text search data is collected
    Then the full-text search data should not be regenerated
    And the search data checksum should remain unchanged

  Scenario: Checksum triggers regeneration when search data changes
    Given a test entity with search data "initial search content"
    When full-text search data is collected
    Then the search data checksum should be calculated and stored
    When the entity search data is changed to "updated search content"
    And full-text search data is collected
    Then the full-text search data should be regenerated
    And the search data checksum should be updated

  Scenario: Checksum handles null search data correctly
    Given a test entity with search data "null"
    When full-text search data is collected
    Then the search data checksum should represent empty content
    When the entity search data is changed to "some content"
    And full-text search data is collected
    Then the full-text search data should be regenerated
    And the search data checksum should be updated

  Scenario Outline: Checksum detects changes in individual search fields
    Given a test entity with multiple search fields:
      | field1   | field2   | field3   |
      | <field1> | <field2> | <field3> |
    When full-text search data is collected
    Then the search data checksum should be calculated and stored
    When the entity field "<changedField>" is changed to "<newValue>"
    And full-text search data is collected
    Then the full-text search data should be regenerated
    And the search data checksum should be updated
    Examples:
      | field1      | field2      | field3        | changedField | newValue          |
      | content one | content two | content three | field1       | new content one   |
      | content one | content two | content three | field2       | new content two   |
      | content one | content two | content three | field3       | new content three |

  Scenario: Checksum performance for large text content
    Given a test entity with large search data of size 10000 characters
    When full-text search data is collected
    Then the search data checksum should be calculated in less than 500 milliseconds
    When full-text search data is collected
    Then the full-text search data generation should be skipped
    And the operation should complete in less than 100 milliseconds
