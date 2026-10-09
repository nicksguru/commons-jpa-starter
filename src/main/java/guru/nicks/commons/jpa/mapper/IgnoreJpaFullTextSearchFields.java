package guru.nicks.commons.jpa.mapper;

import guru.nicks.commons.jpa.domain.FullTextSearchAwareEntity;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For MapStruct mappers: ignores full-text search fields, see {@link FullTextSearchAwareEntity}. They are maintained by
 * the application on save, never copied from DTOs.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.CLASS)
@Mapping(target = "fullTextSearchData", ignore = true)
@Mapping(target = "fullTextSearchDataChecksum", ignore = true)
public @interface IgnoreJpaFullTextSearchFields {
}
