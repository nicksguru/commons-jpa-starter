@db #@disabled
Feature: UUID v7 Generator

  Background:
    Given a UuidV7Generator is configured for the "id" field of "TestEntityWithUuid"

  Scenario: Generate a new UUID when entity ID is null
    Given the entity has no ID set
    When the generator is called to generate an ID for the entity
    Then no exception should be thrown
    And a new UUID v7 should be generated

  Scenario: Return existing UUID when entity ID is already set
    Given the entity has an existing ID set
    When the generator is called to generate an ID for the entity
    Then no exception should be thrown
    And the existing UUID should be returned

  Scenario: Generator event types should be INSERT_ONLY
    When the generator event types are requested
    Then the event types should be INSERT_ONLY

  Scenario Outline: Constructor validation for ID field type
    Given a UuidV7Generator is configured for the "<fieldName>" field of "<entityClass>"
    When the generator is instantiated
    Then <exceptionExpected> should be thrown
    Examples:
      | fieldName | entityClass             | exceptionExpected | comments                   |
      | id        | TestEntityWithUuid      | no exception      |                            |
      | name      | TestEntityWithStringId  | an exception      | name is String, not UUID   |
      | value     | TestEntityWithIntegerId | an exception      | value is Integer, not UUID |

  Scenario: Handle exception during ID getter invocation
    Given the entity ID getter throws an exception
    When the generator is called to generate an ID for the entity
    Then the exception message should contain "Failed to call entity ID getter method"
