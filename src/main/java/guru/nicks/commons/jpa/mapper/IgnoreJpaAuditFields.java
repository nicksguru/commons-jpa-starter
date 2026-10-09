package guru.nicks.commons.jpa.mapper;

import guru.nicks.commons.jpa.domain.AuditableEntity;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For MapStruct mappers: ignores JPA audit fields, see {@link AuditableEntity}. They are filled by Spring Data JPA
 * auditing, never copied from DTOs.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.CLASS)
@Mapping(target = "createdDate", ignore = true)
@Mapping(target = "createdBy", ignore = true)
@Mapping(target = "lastModifiedDate", ignore = true)
@Mapping(target = "lastModifiedBy", ignore = true)
public @interface IgnoreJpaAuditFields {
}
