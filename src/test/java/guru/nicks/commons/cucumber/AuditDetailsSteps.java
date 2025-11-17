package guru.nicks.commons.cucumber;

import guru.nicks.commons.jpa.domain.AuditDetails;
import guru.nicks.commons.log.domain.LogContext;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.Data;
import org.mockito.MockedStatic;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class AuditDetailsSteps {

    private UserDetails userDetails;
    private AuditDetails auditDetails;
    private MockedStatic<LogContext> logContextMockedStatic;

    @Before
    public void beforeEachScenario() {
        logContextMockedStatic = mockStatic(LogContext.class);
        LogContext.TRACE_ID.clear();
    }

    @After
    public void afterEachScenario() {
        if (logContextMockedStatic != null) {
            logContextMockedStatic.close();
        }
    }

    @Given("a UserDetails implementation of type {string}")
    public void aUserDetailsImplementationOfType(String userDetailsType) {
        switch (userDetailsType) {
            case "TestUserPrincipal" -> userDetails = mock(TestUserPrincipal.class);
            case "User" -> userDetails = new User("username", "password", Collections.emptyList());
            default -> throw new IllegalArgumentException("Unsupported UserDetails type: " + userDetailsType);
        }
    }

    @Given("the user ID is set to {string}")
    public void theUserIdIsSetTo(String userId) {
        if (userId.equals("N/A")) {
            return;
        }

        if (userDetails instanceof TestUserPrincipal userPrincipal) {
            when(userPrincipal.getId())
                    .thenReturn("null".equals(userId) ? null : userId);
        }
    }

    @Given("the trace ID is set to {string}")
    public void theTraceIdIsSetTo(String traceId) {
        LogContext.TRACE_ID.put(traceId);
    }

    @When("AuditDetails is created from the UserDetails")
    public void auditDetailsIsCreatedFromTheUserDetails() {
        auditDetails = new AuditDetails(userDetails);
    }

    @When("AuditDetails is created with default constructor")
    public void auditDetailsIsCreatedWithDefaultConstructor() {
        auditDetails = new AuditDetails();
    }

    @Then("the AuditDetails should have user ID {string}")
    public void theAuditDetailsShouldHaveUserId(String expectedUserId) {
        if ("null".equals(expectedUserId)) {
            assertThat(auditDetails.getUserId())
                    .as("userId")
                    .isNull();
        } else {
            assertThat(auditDetails.getUserId())
                    .as("userId")
                    .isEqualTo(expectedUserId);
        }
    }

    @Then("the AuditDetails should have null user ID")
    public void theAuditDetailsShouldHaveNullUserID() {
        assertThat(auditDetails.getUserId())
                .as("userId")
                .isNull();
    }

    @Then("the AuditDetails should have trace ID {string}")
    public void theAuditDetailsShouldHaveTraceId(String expectedTraceId) {
        if ("null".equals(expectedTraceId)) {
            assertThat(auditDetails.getTraceId())
                    .as("traceId")
                    .isNull();
        } else {
            assertThat(auditDetails.getTraceId())
                    .as("traceId")
                    .isEqualTo(expectedTraceId);
        }
    }

    @Then("the AuditDetails should have null trace ID")
    public void theAuditDetailsShouldHaveNullTraceId() {
        assertThat(auditDetails.getTraceId())
                .as("traceId")
                .isNull();
    }

    @Data
    public static class TestUserPrincipal implements UserDetails {

        private final String id;

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return List.of();
        }

        @Override
        public String getPassword() {
            return "";
        }

        @Override
        public String getUsername() {
            return "";
        }

    }

}
