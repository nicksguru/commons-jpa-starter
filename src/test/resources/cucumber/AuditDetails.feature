@db #@disabled
Feature: AuditDetails functionality
  AuditDetails should correctly extract user ID and trace ID from various sources

  Scenario Outline: Creating AuditDetails from UserDetails
    Given a UserDetails implementation of type "<userDetailsType>"
    And the user ID is set to "<userId>"
    And the trace ID is set to "<traceId>"
    When AuditDetails is created from the UserDetails
    Then the AuditDetails should have user ID "<expectedUserId>"
    And the AuditDetails should have trace ID "<expectedTraceId>"
    Examples:
      | userDetailsType   | userId   | traceId   | expectedUserId | expectedTraceId |
      | TestUserPrincipal | user-123 | trace-456 | user-123       | trace-456       |
      | TestUserPrincipal | user-123 |           | user-123       | null            |
      | User              | N/A      | trace-456 | null           | trace-456       |

  Scenario: Creating AuditDetails with no UserDetails
    When AuditDetails is created with default constructor
    Then the AuditDetails should have null user ID
    And the AuditDetails should have null trace ID
