package guru.nicks.commons.cucumber;

import guru.nicks.commons.cucumber.domain.IgnoreJpaFieldsTestMapperImpl;
import guru.nicks.commons.cucumber.domain.TestEntity;
import guru.nicks.commons.cucumber.domain.TestEntityDto;
import guru.nicks.commons.jpa.domain.AuditDetails;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps characterizing the {@code IgnoreJpaAuditFields} and {@code IgnoreJpaFullTextSearchFields} MapStruct
 * meta-annotations via the generated {@link IgnoreJpaFieldsTestMapperImpl}: JPA audit fields (filled by Spring Data
 * JPA auditing) and full-text search fields (maintained by the entity itself) must never be copied from DTOs, in any
 * mapping direction, while business fields must always be. Entity and DTO sentinels are pairwise distinct so that a
 * copy in the wrong direction cannot pass by accident. Scenarios are pure in-memory mapper calls and need no database.
 */
public class IgnoreJpaFieldsMapperSteps {

    private static final Instant ENTITY_CREATED_DATE = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant ENTITY_LAST_MODIFIED_DATE = Instant.parse("2026-01-01T10:30:00Z");
    private static final String ENTITY_FTS_DATA = "entity-fts-data";
    private static final String ENTITY_FTS_CHECKSUM = "entity-fts-checksum";

    private static final Instant DTO_CREATED_DATE = Instant.parse("2026-02-02T12:00:00Z");
    private static final Instant DTO_LAST_MODIFIED_DATE = Instant.parse("2026-02-02T12:30:00Z");
    private static final String DTO_FTS_DATA = "dto-fts-data";
    private static final String DTO_FTS_CHECKSUM = "dto-fts-checksum";

    private final IgnoreJpaFieldsTestMapperImpl mapper = new IgnoreJpaFieldsTestMapperImpl();

    private TestEntity sourceEntity;
    private TestEntityDto sourceDto;
    private TestEntityDto mappedDto;
    private TestEntity createdEntity;
    private TestEntity updatedEntity;

    /**
     * Creates an entity with sentinel values in all six JPA-managed fields and all business fields.
     */
    @Given("a fully populated test entity")
    public void aFullyPopulatedTestEntity() {
        sourceEntity = newEntity();
    }

    /**
     * Creates a DTO with sentinel values in all six JPA-managed fields and all business fields, all distinct from
     * the entity sentinels.
     */
    @Given("a fully populated test entity DTO")
    public void aFullyPopulatedTestEntityDto() {
        sourceDto = newDto();
    }

    /**
     * Creates the update target with sentinel managed fields that must survive the DTO update.
     */
    @Given("an existing test entity with sentinel managed fields")
    public void anExistingTestEntityWithSentinelManagedFields() {
        updatedEntity = newEntity();
    }

    /**
     * Control mapping: without the meta-annotations the generator copies the managed fields too.
     */
    @When("the entity is mapped to a DTO without the meta-annotations")
    public void theEntityIsMappedToADtoWithoutTheMetaAnnotations() {
        mappedDto = mapper.toDtoWithoutMetaAnnotations(requireSourceEntity());
    }

    /**
     * Maps via the method annotated with {@code @IgnoreJpaAuditFields} only.
     */
    @When("the entity is mapped to a DTO ignoring audit fields")
    public void theEntityIsMappedToADtoIgnoringAuditFields() {
        mappedDto = mapper.toDtoIgnoringAuditFields(requireSourceEntity());
    }

    /**
     * Maps via the method annotated with {@code @IgnoreJpaFullTextSearchFields} only.
     */
    @When("the entity is mapped to a DTO ignoring full-text search fields")
    public void theEntityIsMappedToADtoIgnoringFullTextSearchFields() {
        mappedDto = mapper.toDtoIgnoringFullTextSearchFields(requireSourceEntity());
    }

    /**
     * Maps via the method annotated with both meta-annotations.
     */
    @When("the entity is mapped to a DTO ignoring all managed fields")
    public void theEntityIsMappedToADtoIgnoringAllManagedFields() {
        mappedDto = mapper.toDtoIgnoringAllManagedFields(requireSourceEntity());
    }

    /**
     * Maps the DTO to a new entity via the method annotated with both meta-annotations.
     */
    @When("the DTO is mapped to a new entity")
    public void theDtoIsMappedToANewEntity() {
        createdEntity = mapper.toEntity(requireSourceDto());
    }

    /**
     * Updates the existing sentinel entity from the DTO in place - the production use case of the meta-annotations.
     */
    @When("the DTO is mapped onto the existing entity")
    public void theDtoIsMappedOntoTheExistingEntity() {
        mapper.updateEntity(requireUpdatedEntity(), requireSourceDto());
    }

    /**
     * Verifies that the mapped DTO carries the business fields of the source entity.
     */
    @Then("the DTO should have the business fields copied")
    public void theDtoShouldHaveTheBusinessFieldsCopied() {
        assertThat(mappedDto.getId())
                .as("dto id")
                .isEqualTo("entity-id");
        assertThat(mappedDto.getName())
                .as("dto name")
                .isEqualTo("entity-name");
        assertThat(mappedDto.getField1())
                .as("dto field1")
                .isEqualTo("entity-field1");
    }

    /**
     * Verifies that the mapped DTO carries all four audit fields of the source entity.
     */
    @Then("the DTO should have populated audit fields")
    public void theDtoShouldHavePopulatedAuditFields() {
        assertCopiedAuditFields(mappedDto);
    }

    /**
     * Verifies that all four audit fields were left unassigned on the mapped DTO.
     */
    @Then("the DTO should have null audit fields")
    public void theDtoShouldHaveNullAuditFields() {
        assertNullAuditFields(mappedDto);
    }

    /**
     * Verifies that the mapped DTO carries both full-text search fields of the source entity.
     */
    @Then("the DTO should have populated full-text search fields")
    public void theDtoShouldHavePopulatedFullTextSearchFields() {
        assertCopiedFullTextSearchFields(mappedDto);
    }

    /**
     * Verifies that both full-text search fields were left unassigned on the mapped DTO.
     */
    @Then("the DTO should have null full-text search fields")
    public void theDtoShouldHaveNullFullTextSearchFields() {
        assertNullFullTextSearchFields(mappedDto);
    }

    /**
     * Verifies that the created entity carries the business fields of the source DTO.
     */
    @Then("the created entity should have the business fields copied")
    public void theCreatedEntityShouldHaveTheBusinessFieldsCopied() {
        assertBusinessFieldsFromDto(createdEntity);
    }

    /**
     * Verifies that all four audit fields were left unassigned on the created entity.
     */
    @Then("the created entity should have null audit fields")
    public void theCreatedEntityShouldHaveNullAuditFields() {
        assertNullAuditFields(createdEntity);
    }

    /**
     * Verifies that both full-text search fields were left unassigned on the created entity.
     */
    @Then("the created entity should have null full-text search fields")
    public void theCreatedEntityShouldHaveNullFullTextSearchFields() {
        assertNullFullTextSearchFields(createdEntity);
    }

    /**
     * Verifies that the updated entity carries the business fields of the DTO.
     */
    @Then("the existing entity should have the business fields from the DTO")
    public void theExistingEntityShouldHaveTheBusinessFieldsFromTheDto() {
        assertBusinessFieldsFromDto(requireUpdatedEntity());
    }

    /**
     * Verifies that the pre-existing audit fields survived the DTO update unchanged.
     */
    @Then("the existing entity should keep its original audit fields")
    public void theExistingEntityShouldKeepItsOriginalAuditFields() {
        assertCopiedAuditFields(requireUpdatedEntity());
    }

    /**
     * Verifies that the pre-existing full-text search fields survived the DTO update unchanged.
     */
    @Then("the existing entity should keep its original full-text search fields")
    public void theExistingEntityShouldKeepItsOriginalFullTextSearchFields() {
        assertCopiedFullTextSearchFields(requireUpdatedEntity());
    }

    private TestEntity newEntity() {
        var entity = new TestEntity();
        entity.setId("entity-id");
        entity.setName("entity-name");
        entity.setField1("entity-field1");
        entity.setCreatedDate(ENTITY_CREATED_DATE);
        entity.setCreatedBy(newAuditDetails("entity-creator", "entity-created-trace"));
        entity.setLastModifiedDate(ENTITY_LAST_MODIFIED_DATE);
        entity.setLastModifiedBy(newAuditDetails("entity-modifier", "entity-modified-trace"));
        entity.setFullTextSearchData(ENTITY_FTS_DATA);
        entity.setFullTextSearchDataChecksum(ENTITY_FTS_CHECKSUM);

        return entity;
    }

    private TestEntityDto newDto() {
        var dto = new TestEntityDto();
        dto.setId("dto-id");
        dto.setName("dto-name");
        dto.setField1("dto-field1");
        dto.setCreatedDate(DTO_CREATED_DATE);
        dto.setCreatedBy(newAuditDetails("dto-creator", "dto-created-trace"));
        dto.setLastModifiedDate(DTO_LAST_MODIFIED_DATE);
        dto.setLastModifiedBy(newAuditDetails("dto-modifier", "dto-modified-trace"));
        dto.setFullTextSearchData(DTO_FTS_DATA);
        dto.setFullTextSearchDataChecksum(DTO_FTS_CHECKSUM);

        return dto;
    }

    private AuditDetails newAuditDetails(String userId, String traceId) {
        var auditDetails = new AuditDetails();
        auditDetails.setUserId(userId);
        auditDetails.setTraceId(traceId);

        return auditDetails;
    }

    private void assertCopiedAuditFields(TestEntityDto dto) {
        assertThat(dto.getCreatedDate())
                .as("dto createdDate")
                .isEqualTo(ENTITY_CREATED_DATE);
        assertAuditDetails(dto.getCreatedBy(), "entity-creator", "entity-created-trace", "dto createdBy");
        assertThat(dto.getLastModifiedDate())
                .as("dto lastModifiedDate")
                .isEqualTo(ENTITY_LAST_MODIFIED_DATE);
        assertAuditDetails(dto.getLastModifiedBy(), "entity-modifier", "entity-modified-trace", "dto lastModifiedBy");
    }

    private void assertCopiedAuditFields(TestEntity entity) {
        assertThat(entity.getCreatedDate())
                .as("entity createdDate")
                .isEqualTo(ENTITY_CREATED_DATE);
        assertAuditDetails(entity.getCreatedBy(), "entity-creator", "entity-created-trace", "entity createdBy");
        assertThat(entity.getLastModifiedDate())
                .as("entity lastModifiedDate")
                .isEqualTo(ENTITY_LAST_MODIFIED_DATE);
        assertAuditDetails(entity.getLastModifiedBy(), "entity-modifier", "entity-modified-trace",
                "entity lastModifiedBy");
    }

    private void assertNullAuditFields(TestEntityDto dto) {
        assertThat(dto.getCreatedDate())
                .as("dto createdDate")
                .isNull();
        assertThat(dto.getCreatedBy())
                .as("dto createdBy")
                .isNull();
        assertThat(dto.getLastModifiedDate())
                .as("dto lastModifiedDate")
                .isNull();
        assertThat(dto.getLastModifiedBy())
                .as("dto lastModifiedBy")
                .isNull();
    }

    private void assertNullAuditFields(TestEntity entity) {
        assertThat(entity.getCreatedDate())
                .as("entity createdDate")
                .isNull();
        assertThat(entity.getCreatedBy())
                .as("entity createdBy")
                .isNull();
        assertThat(entity.getLastModifiedDate())
                .as("entity lastModifiedDate")
                .isNull();
        assertThat(entity.getLastModifiedBy())
                .as("entity lastModifiedBy")
                .isNull();
    }

    private void assertCopiedFullTextSearchFields(TestEntityDto dto) {
        assertThat(dto.getFullTextSearchData())
                .as("dto fullTextSearchData")
                .isEqualTo(ENTITY_FTS_DATA);
        assertThat(dto.getFullTextSearchDataChecksum())
                .as("dto fullTextSearchDataChecksum")
                .isEqualTo(ENTITY_FTS_CHECKSUM);
    }

    private void assertCopiedFullTextSearchFields(TestEntity entity) {
        assertThat(entity.getFullTextSearchData())
                .as("entity fullTextSearchData")
                .isEqualTo(ENTITY_FTS_DATA);
        assertThat(entity.getFullTextSearchDataChecksum())
                .as("entity fullTextSearchDataChecksum")
                .isEqualTo(ENTITY_FTS_CHECKSUM);
    }

    private void assertNullFullTextSearchFields(TestEntityDto dto) {
        assertThat(dto.getFullTextSearchData())
                .as("dto fullTextSearchData")
                .isNull();
        assertThat(dto.getFullTextSearchDataChecksum())
                .as("dto fullTextSearchDataChecksum")
                .isNull();
    }

    private void assertNullFullTextSearchFields(TestEntity entity) {
        assertThat(entity.getFullTextSearchData())
                .as("entity fullTextSearchData")
                .isNull();
        assertThat(entity.getFullTextSearchDataChecksum())
                .as("entity fullTextSearchDataChecksum")
                .isNull();
    }

    private void assertBusinessFieldsFromDto(TestEntity entity) {
        assertThat(entity.getId())
                .as("entity id")
                .isEqualTo("dto-id");
        assertThat(entity.getName())
                .as("entity name")
                .isEqualTo("dto-name");
        assertThat(entity.getField1())
                .as("entity field1")
                .isEqualTo("dto-field1");
    }

    private void assertAuditDetails(AuditDetails auditDetails, String userId, String traceId, String description) {
        assertThat(auditDetails)
                .as(description)
                .isNotNull();
        assertThat(auditDetails.getUserId())
                .as(description + " userId")
                .isEqualTo(userId);
        assertThat(auditDetails.getTraceId())
                .as(description + " traceId")
                .isEqualTo(traceId);
    }

    private TestEntity requireSourceEntity() {
        assertThat(sourceEntity)
                .as("source entity must be created by a preceding Given step")
                .isNotNull();

        return sourceEntity;
    }

    private TestEntityDto requireSourceDto() {
        assertThat(sourceDto)
                .as("source DTO must be created by a preceding Given step")
                .isNotNull();

        return sourceDto;
    }

    private TestEntity requireUpdatedEntity() {
        assertThat(updatedEntity)
                .as("updated entity must be created by a preceding Given step")
                .isNotNull();

        return updatedEntity;
    }

}
