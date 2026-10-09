package guru.nicks.commons.cucumber.domain;

import guru.nicks.commons.jpa.domain.AuditDetails;

import lombok.Data;

import java.time.Instant;

/**
 * Test DTO mirroring the properties of {@link TestEntity}: the six JPA-managed fields (audit + full-text search) whose
 * copying the {@code IgnoreJpa*} MapStruct meta-annotations must suppress, plus business fields that must always be
 * copied. Sentinel values live in {@code IgnoreJpaFieldsMapperSteps}.
 */
@Data
public class TestEntityDto {

    private String id;
    private String name;
    private String field1;

    private Instant createdDate;
    private AuditDetails createdBy;
    private Instant lastModifiedDate;
    private AuditDetails lastModifiedBy;

    private String fullTextSearchData;
    private String fullTextSearchDataChecksum;

}
