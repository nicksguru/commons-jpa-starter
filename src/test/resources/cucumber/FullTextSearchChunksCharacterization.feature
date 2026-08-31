Feature: createFullTextSearchChunks characterization

  Background:
    Given default ngram config

  Scenario: Short words come first, alphabetically, with stop words excluded
    When full-text search chunks are created from "zebra ox hi the a"
    Then the chunks are exactly "hi ox zeb zebr zebra ebr bra"

  Scenario Outline: Stop words participate in all phases only while English morph analysis is off
    Given ngram config with English morph analysis <morph>
    When full-text search chunks are created from "the was cat"
    Then the chunks are exactly "<chunks>"
    Examples:
      | morph | chunks      |
      | on    | cat         |
      | off   | cat the was |

  Scenario: Irregular lemma ngrams follow the raw ngrams of the same word
    When full-text search chunks are created from "ran"
    Then the chunks are exactly "ran run"

  Scenario: Irregular noun lemma comes from the irregular-forms map before stemming
    When full-text search chunks are created from "geese"
    Then the chunks are exactly "gee gees geese goo goos goose ees ese oos ose"

  Scenario: Lemma ngrams are skipped when the lemma equals the word
    When full-text search chunks are created from "cat"
    Then the chunks are exactly "cat"

  Scenario: Prefix/infix overlap dedup is first-wins across the prefix to infix phases
    When full-text search chunks are created from "abcd bcd"
    Then the chunks are exactly "abc abcd bcd"

  Scenario Outline: Tiny maxNgramCount truncates the prefix phase and discards infix ngrams
    Given ngram config with max ngram count <cap>
    When full-text search chunks are created from "ox abcdef"
    Then the chunks are exactly "<chunks>"
    Examples:
      | cap | chunks                           |
      | 4   | ox abc abcd abcde abcdef         |
      | 6   | ox abc abcd abcde abcdef bcd cde |

  # the separator is counted whenever the builder is non-empty and appending breaks (not skips) at the first
  # chunk that would not fit
  Scenario Outline: Append stops at the first chunk that would not fit the max search data length
    Given a chunking test entity with search data "ox cat zebra" and max full-text search data length <maxLength>
    When the chunking entity rebuilds its full-text search ngrams
    Then the full-text search data of the chunking entity is exactly "<data>"
    Examples:
      | maxLength | data                          |
      | 6         | ox cat                        |
      | 9         | ox cat                        |
      | 12        | ox cat zeb                    |
      | 15        | ox cat zeb zebr               |
      | 100       | ox cat zeb zebr zebra ebr bra |

  Scenario Outline: Accent reduction changes the produced chunks
    Given ngram config with accent reduction <reduce>
    When full-text search chunks are created from "<input>"
    Then the chunks are exactly "<chunks>"
    Examples:
      | reduce | input | chunks       |
      | on     | café  | caf cafe afe |
      | off    | café  | caf café afé |
      | on     | éëё   | eeе          |
      | off    | éëё   | éëё          |

  # word-level validation sanity: word splitting runs BEFORE the chunk validation, so quotes, double dashes and
  # semicolons never survive into chunks - the defensive IllegalArgumentException("Invalid characters (SQL
  # injection?) in search text") is unreachable via text input and such inputs are sanitized instead of rejected
  Scenario Outline: SQL injection-looking inputs are sanitized, not thrown at
    When full-text search chunks are created from "<input>"
    Then the chunks are exactly "<chunks>"
    And every chunk is free of SQL injection characters
    Examples:
      | input                   | chunks                                                                  |
      | word' OR '1'='1         | 1 wor word ord                                                          |
      | word;DROP TABLE users-- | dro drop tab tabl table use user users wor word rop abl ble ser ers ord |

  Scenario Outline: NgramUtils.createNgrams produces prefix ngrams before infix ones
    When ngrams are created from "abcd bcd" in mode <mode>
    Then the ngrams are exactly "<ngrams>"
    Examples:
      | mode   | ngrams       |
      | ALL    | abc abcd bcd |
      | PREFIX | abc abcd bcd |
      | INFIX  | bcd          |
