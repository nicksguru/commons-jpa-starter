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

  Scenario Outline: Testing createFullTextSearchChunks with edge cases
    Given a test entity with search data "<searchData>"
    When full-text search chunks are created
    Then the chunks should be valid and contain "<expectedContent>" if present
    And no chunks should contain SQL injection characters
    Examples:
      | searchData                                          | expectedContent                                          |
      | null                                                |                                                          |
      |                                                     |                                                          |
      | \t\n\r                                              |                                                          |
      | the a an it was were be been                        |                                                          |
      | ox?                                                 | ox                                                       |
      | a...                                                |                                                          |
      | ab<>                                                |                                                          |
      | abc{}                                               | abc                                                      |
      | 1234567890                                          |                                                          |
      | áéíóúäëïöü                                          | aei aeio aeiou aeioua eio iou oua uae                    |
      | THE QUICK BROWN FOX                                 | quick brown fox                                          |
      | word word word word                                 | word                                                     |
      | test@example.com                                    | test exampl com                                          |
      | hello-world_test                                    | hello world test                                         |
      | 😀😃😄😁😆😅😂🤣😊😇                                |                                                          |
      | word1 word2 word3 word1 word2                       | word1 word2 word3                                        |
      | supercalifragilisticexpialidocious                  | sup supe super superc agi ali cal   cex cio doc erc      |
      | Iñtërnâtiônàlizætiøn                                | int inte inter intern ali ati ern ion izæ iøn liz        |
      | hello\nworld\ttest\r\n                              | hello world test                                         |
      | <script>alert('xss')</script>                       | script alert xss script                                  |
      | &lt;tag&gt;&amp;text&quot;                          | tag amp text                                             |
      | a b c d e f g h i j k l m n o p q r s t u v w x y z | b c d e f g h j k l m n o p q r s t u v w x y z          |
      | A B C D E F G H I J K L M N O P Q R S T U V W X Y Z | b c d e f g h j k l m n o p q r s t u v w x y z          |
      | word' OR '1'='1                                     | word or 1                                                |
      | word--comment                                       | com comm comme commen wor word ent men mme omm ord       |
      | word;DROP TABLE users--                             | word drop table users                                    |
      | don't stop-that's it;now                            | t s don now sto stop top                                 |
      | https://example.com/path/to/resource                | com  exa  exam  examp  exampl  htt  http  https  pat     |
      | /path/to/file.txt                                   | fil file pat path txt ath ile                            |
      | user@example.com                                    | com exa exam examp exampl use user amp mpl ple ser       |
      | 123                                                 | 123                                                      |
      | abc123                                              | abc123                                                   |
      | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa                      | aaa aaaa aaaaa aaaaaa                                    |
      | test-test test_test                                 | test test test                                           |
      | it's don't won't can't                              | s t can don won                                          |
      | variable_name another_variable                      | ano anot anoth anothe nam name var vari varia variab     |
      | hello世界 world                                       | hello world                                              |
      | 123-456-7890                                        | 123 456 7890                                             |
      | $100.50                                             | 100 50                                                   |
      | C++ Java Python                                     | java python                                              |
      | &nbsp;&amp;&lt;&gt;                                 |                                                          |
      | word1word2word3                                     | wor word word1 word1w 1wo 2wo d1w d2w ord rd1 rd2 rd3    |
      | a-b-c-d-e-f-g                                       | b c d e f g                                              |
      | one,two,three,four                                  | one two three four                                       |
      | 'quoted text' another                               | ano anot anoth anothe quo quot quote quoted tex text ext |
      | (parentheses) [brackets] {braces}                   | bra brac brace braces brack bracke par pare paren parent |
