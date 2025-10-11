@db #@disabled
Feature: UUID v7 Crockford Base32 Generator

  Scenario: Check generator event types
    Given a UuidV7CrockfordBase32Generator is created for a String property
    When event types are retrieved from the UuidV7CrockfordBase32Generator
    Then the generator event types should be "INSERT"

  Scenario: Generate UUID v7 encoded in Crockford Base32 format
    Given a UuidV7CrockfordBase32Generator is created for a String property
    And no exception should be thrown
    When the UuidV7CrockfordBase32Generator is called with a new entity
    Then a non-blank String ID should be generated
    And the generated ID should be 26 characters long
    And the generated ID should contain only valid Crockford Base32 characters
    And the generated ID should decode back to an UUID v7

  Scenario Outline: Skip generation when entity already has an ID
    Given a UuidV7CrockfordBase32Generator is created for a String property
    And no exception should be thrown
    And an entity with ID "<existingId>"
    When the UuidV7CrockfordBase32Generator is called with the existing entity
    Then the entity ID should remain "<existingId>"
    Examples:
      | existingId                   |
      | 0123456789abcdefghjkmnpqrstv |
      | test-id                      |
      | 12345                        |

  Scenario: Throw exception when target property type is not String
    When a UuidV7CrockfordBase32Generator is created for a non-String property
    Then an exception should be thrown
