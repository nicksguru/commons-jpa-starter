package guru.nicks.cucumber;

import guru.nicks.jpa.domain.AuditDetails;
import guru.nicks.jpa.domain.AuditableEntity;
import guru.nicks.jpa.mapper.AuditDetailsMapper;
import guru.nicks.jpa.mapper.AuditDetailsMapperImpl;
import guru.nicks.rest.v1.dto.AuditDetailsDto;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.DataTableType;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.Builder;
import lombok.Value;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AuditDetailsMapperSteps {

    @Mock
    private AuditableEntity<?> auditableEntity;
    private AutoCloseable closeableMocks;

    private AuditDetailsMapper auditDetailsMapper;
    private AuditDetailsDto auditDetailsDto;

    @Before
    public void beforeEachScenario() {
        closeableMocks = MockitoAnnotations.openMocks(this);
        auditDetailsMapper = new AuditDetailsMapperImpl();
    }

    @After
    public void afterEachScenario() throws Exception {
        closeableMocks.close();
    }

    @DataTableType
    public AuditDetailsData createAuditDetailsData(Map<String, String> entry) {
        return AuditDetailsData.builder()
                .createdByUserId(convertNullString(entry.get("createdByUserId")))
                .createdByTraceId(convertNullString(entry.get("createdByTraceId")))
                .lastModifiedByUserId(convertNullString(entry.get("lastModifiedByUserId")))
                .lastModifiedByTraceId(convertNullString(entry.get("lastModifiedByTraceId")))
                .build();
    }

    @Given("a null AuditableEntity")
    public void aNullAuditableEntity() {
        auditableEntity = null;
    }

    @Given("an AuditableEntity with null audit details")
    public void anAuditableEntityWithNullAuditDetails() {
        when(auditableEntity.getCreatedBy())
                .thenReturn(null);
        when(auditableEntity.getLastModifiedBy())
                .thenReturn(null);
    }

    @Given("an AuditableEntity with the following audit details:")
    public void anAuditableEntityWithTheFollowingAuditDetails(AuditDetailsData auditDetailsData) {
        // mock AuditDetails for createdBy
        var createdBy = mock(AuditDetails.class);
        when(createdBy.getUserId())
                .thenReturn(auditDetailsData.getCreatedByUserId());
        when(createdBy.getTraceId())
                .thenReturn(auditDetailsData.getCreatedByTraceId());

        // mock AuditDetails for lastModifiedBy
        var lastModifiedBy = mock(AuditDetails.class);
        when(lastModifiedBy.getUserId())
                .thenReturn(auditDetailsData.getLastModifiedByUserId());
        when(lastModifiedBy.getTraceId())
                .thenReturn(auditDetailsData.getLastModifiedByTraceId());

        // mock the audit details
        when(auditableEntity.getCreatedBy())
                .thenReturn(createdBy);
        when(auditableEntity.getLastModifiedBy())
                .thenReturn(lastModifiedBy);
    }

    @When("the entity is mapped to AuditDetailsDto")
    public void theEntityIsMappedToAuditDetailsDto() {
        auditDetailsDto = auditDetailsMapper.toDto(auditableEntity);
    }

    @Then("the AuditDetailsDto should be null")
    public void auditDetailsDtoShouldBeNull() {
        assertThat(auditDetailsDto)
                .as("audit details dto")
                .isNull();
    }

    @Then("the AuditDetailsDto should not be null")
    public void auditDetailsDtoShouldNotBeNull() {
        assertThat(auditDetailsDto)
                .as("audit details dto")
                .isNotNull();
    }

    @Then("the AuditDetailsDto should have null created by")
    public void theAuditDetailsDtoShouldHaveNullCreatedBy() {
        assertThat(auditDetailsDto)
                .as("audit details dto")
                .isNotNull();
        assertThat(auditDetailsDto.getCreatedBy())
                .as("created by")
                .isNull();
    }

    @Then("the AuditDetailsDto should have null last modified by")
    public void theAuditDetailsDtoShouldHaveNullLastModifiedBy() {
        assertThat(auditDetailsDto)
                .as("audit details dto")
                .isNotNull();
        assertThat(auditDetailsDto.getLastModifiedBy())
                .as("last modified by")
                .isNull();
    }

    @Then("the AuditDetailsDto should have created by with userId {string} and traceId {string}")
    public void theAuditDetailsDtoShouldHaveCreatedByWithUserIdAndTraceId(String userId, String traceId) {
        assertThat(auditDetailsDto)
                .as("audit details dto")
                .isNotNull();

        if ("null".equals(userId) && "null".equals(traceId)) {
            assertThat(auditDetailsDto.getCreatedBy())
                    .as("created by")
                    .isNull();
            return;
        }

        assertThat(auditDetailsDto.getCreatedBy())
                .as("created by")
                .isNotNull();

        if (!"null".equals(userId)) {
            assertThat(auditDetailsDto.getCreatedBy().getUserId())
                    .as("created by user id")
                    .isEqualTo(userId);
        } else {
            assertThat(auditDetailsDto.getCreatedBy().getUserId())
                    .as("created by user id")
                    .isNull();
        }

        if (!"null".equals(traceId)) {
            assertThat(auditDetailsDto.getCreatedBy().getTraceId())
                    .as("created by trace id")
                    .isEqualTo(traceId);
        } else {
            assertThat(auditDetailsDto.getCreatedBy().getTraceId())
                    .as("created by trace id")
                    .isNull();
        }
    }

    @Then("the AuditDetailsDto should have last modified by with userId {string} and traceId {string}")
    public void theAuditDetailsDtoShouldHaveLastModifiedByWithUserIdAndTraceId(String userId, String traceId) {
        assertThat(auditDetailsDto)
                .as("audit details dto")
                .isNotNull();

        assertThat(auditDetailsDto.getLastModifiedBy())
                .as("last modified by")
                .isNotNull();

        if (!"null".equals(userId)) {
            assertThat(auditDetailsDto.getLastModifiedBy().getUserId())
                    .as("last modified by user id")
                    .isEqualTo(userId);
        } else {
            assertThat(auditDetailsDto.getLastModifiedBy().getUserId())
                    .as("last modified by user id")
                    .isNull();
        }

        if (!"null".equals(traceId)) {
            assertThat(auditDetailsDto.getLastModifiedBy().getTraceId())
                    .as("last modified by trace id")
                    .isEqualTo(traceId);
        } else {
            assertThat(auditDetailsDto.getLastModifiedBy().getTraceId())
                    .as("last modified by trace id")
                    .isNull();
        }
    }

    /**
     * Converts the string "null" to an actual null value.
     *
     * @param value The string value to convert.
     * @return null if the input is "null", otherwise the input value.
     */
    private String convertNullString(String value) {
        return "null".equals(value) ? null : value;
    }

    @Value
    @Builder
    public static class AuditDetailsData {

        String createdByUserId;
        String createdByTraceId;
        String lastModifiedByUserId;
        String lastModifiedByTraceId;

    }

}
