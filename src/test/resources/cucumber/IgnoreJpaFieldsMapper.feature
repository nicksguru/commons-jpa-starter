Feature: MapStruct meta-annotations ignoring JPA-managed entity fields
  IgnoreJpaAuditFields and IgnoreJpaFullTextSearchFields must make the MapStruct code generator leave the
  JPA-managed fields unassigned in every mapping direction: audit fields are filled by Spring Data JPA auditing
  and full-text search fields are maintained by the entity itself, so they are never copied from DTOs. Business
  fields must always be copied.

  Scenario: Without the meta-annotations all managed fields are copied
    Given a fully populated test entity
    When the entity is mapped to a DTO without the meta-annotations
    Then the DTO should have the business fields copied
    And the DTO should have populated audit fields
    And the DTO should have populated full-text search fields

  Scenario: IgnoreJpaAuditFields ignores exactly the four audit fields
    Given a fully populated test entity
    When the entity is mapped to a DTO ignoring audit fields
    Then the DTO should have the business fields copied
    And the DTO should have null audit fields
    And the DTO should have populated full-text search fields

  Scenario: IgnoreJpaFullTextSearchFields ignores exactly the two full-text search fields
    Given a fully populated test entity
    When the entity is mapped to a DTO ignoring full-text search fields
    Then the DTO should have the business fields copied
    And the DTO should have populated audit fields
    And the DTO should have null full-text search fields

  Scenario: Both meta-annotations ignore all six managed fields
    Given a fully populated test entity
    When the entity is mapped to a DTO ignoring all managed fields
    Then the DTO should have the business fields copied
    And the DTO should have null audit fields
    And the DTO should have null full-text search fields

  Scenario: DTO to new entity leaves the managed fields unset
    Given a fully populated test entity DTO
    When the DTO is mapped to a new entity
    Then the created entity should have the business fields copied
    And the created entity should have null audit fields
    And the created entity should have null full-text search fields

  Scenario: DTO update keeps the managed fields of the existing entity
    Given a fully populated test entity DTO
    And an existing test entity with sentinel managed fields
    When the DTO is mapped onto the existing entity
    Then the existing entity should have the business fields from the DTO
    And the existing entity should keep its original audit fields
    And the existing entity should keep its original full-text search fields
