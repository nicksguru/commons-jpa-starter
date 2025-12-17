@db #@disabled
Feature: JpaCrudService

  Scenario: Entity is saved successfully
    When an entity is saved
    Then the entity should be saved successfully

  Scenario: Multiple entities are saved successfully
    When multiple entities are saved
    Then all entities should be saved successfully

  Scenario: Entity is deleted successfully
    Given an entity exists
    When the entity is deleted
    Then the entity should be deleted successfully

  Scenario: Entity is deleted by ID successfully
    Given an entity with ID "1" exists
    When the entity with ID "1" is deleted
    Then the entity should be deleted successfully by ID "1"

  Scenario: Multiple entities are deleted by IDs successfully
    Given entities with IDs "1,2,3" exist
    When entities with IDs "1,2,3" are deleted
    Then the entities should be deleted successfully

  Scenario: Check if entity exists by ID
    Given an entity with ID "1" exists
    When existence is checked for ID "1"
    Then the existence result should be true

  Scenario: Check if entity does not exist by ID
    Given no entity with ID "999" exists
    When existence is checked for ID "999"
    Then the existence result should be false

  Scenario: Find entity by ID successfully
    Given an entity with ID "1" exists
    When an entity is found by ID "1"
    Then the entity should be found

  Scenario: Get entity by ID successfully
    Given an entity with ID "1" exists
    When an entity is retrieved by ID "1"
    Then the entity should be retrieved
    And no exception should be thrown

  Scenario: Get entity by non-existent ID
    Given no entity with ID "999" exists
    When an entity is retrieved by ID "999"
    Then TestNotFoundException should be thrown

  Scenario: Find all entities by IDs preserving order
    Given entities with IDs "3,1,2" exist
    When entities are found by IDs "3,1,2"
    Then entities should be found in the order "3,1,2"

  Scenario: Find all entities with pagination
    Given multiple entities exist
    When all entities are found with page 0 and size 10
    Then a page of entities should be returned
