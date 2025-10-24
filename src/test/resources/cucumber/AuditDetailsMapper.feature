@db #@disabled
Feature: Audit Details Mapper
  Maps AuditableEntity objects to AuditDetailsDto objects

  Scenario: Mapping a null AuditableEntity
    Given a null AuditableEntity
    When the entity is mapped to AuditDetailsDto
    Then the AuditDetailsDto should be null

  Scenario: Mapping an AuditableEntity with null audit details
    Given an AuditableEntity with null audit details
    When the entity is mapped to AuditDetailsDto
    Then the AuditDetailsDto should not be null
    And the AuditDetailsDto should have null created by
    And the AuditDetailsDto should have null last modified by

  Scenario Outline: Mapping an AuditableEntity with audit details
    Given an AuditableEntity with the following audit details:
      | createdByUserId | createdByTraceId | lastModifiedByUserId | lastModifiedByTraceId |
      | <createdUserId> | <createdTraceId> | <modifiedUserId>     | <modifiedTraceId>     |
    When the entity is mapped to AuditDetailsDto
    Then the AuditDetailsDto should not be null
    And the AuditDetailsDto should have created by with userId "<createdUserId>" and traceId "<createdTraceId>"
    And the AuditDetailsDto should have last modified by with userId "<modifiedUserId>" and traceId "<modifiedTraceId>"
    Examples:
      | createdUserId | createdTraceId | modifiedUserId | modifiedTraceId |
      | user1         | trace1         | user2          | trace2          |
      | admin         | trace-admin    | admin          | trace-admin     |
      | system        | null           | null           | null            |
