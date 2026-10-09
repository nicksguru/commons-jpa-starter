package guru.nicks.commons.cucumber.domain;

import guru.nicks.commons.jpa.mapper.IgnoreJpaAuditFields;
import guru.nicks.commons.jpa.mapper.IgnoreJpaFullTextSearchFields;
import guru.nicks.commons.mapper.DefaultMapStructConfig;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

/**
 * Test-only mapper characterizing the {@link IgnoreJpaAuditFields} and {@link IgnoreJpaFullTextSearchFields}
 * meta-annotations on all mapping directions. The unannotated method is the control proving the managed fields would
 * otherwise be copied; {@link #updateEntity(TestEntity, TestEntityDto)} is the production use case: JPA audit and
 * full-text search fields must survive an update from a DTO because they are maintained elsewhere.
 */
@Mapper(config = DefaultMapStructConfig.class)
public interface IgnoreJpaFieldsTestMapper {

    TestEntityDto toDtoWithoutMetaAnnotations(TestEntity source);

    @IgnoreJpaAuditFields
    TestEntityDto toDtoIgnoringAuditFields(TestEntity source);

    @IgnoreJpaFullTextSearchFields
    TestEntityDto toDtoIgnoringFullTextSearchFields(TestEntity source);

    @IgnoreJpaAuditFields
    @IgnoreJpaFullTextSearchFields
    TestEntityDto toDtoIgnoringAllManagedFields(TestEntity source);

    @IgnoreJpaAuditFields
    @IgnoreJpaFullTextSearchFields
    TestEntity toEntity(TestEntityDto source);

    @IgnoreJpaAuditFields
    @IgnoreJpaFullTextSearchFields
    void updateEntity(@MappingTarget TestEntity target, TestEntityDto source);

}
