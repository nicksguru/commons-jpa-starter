@db #@disabled
Feature: Bad search repository startup validation
  A search repository declared WITHOUT the mandatory 'default' methods must fail context startup with the
  METHODS_TO_IMPLEMENT error instead of failing later at runtime with a StackOverflowError

  Scenario: Search repository without default methods fails context startup
    When an isolated application context boots with a search repository missing the default methods
    Then the startup should fail with IllegalArgumentException as the root cause
    And the cause chain should mention the missing methods "convertToSearchBuilder" and "findByFilter"
