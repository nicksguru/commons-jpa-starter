package guru.nicks.commons.cucumber.domain;

import guru.nicks.commons.jpa.domain.FullTextSearchAwareEntity;
import guru.nicks.commons.utils.NgramUtilsConfig;

import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

@NoArgsConstructor
@Getter
@Setter
@FieldNameConstants
//
@Jacksonized
@SuperBuilder
// no @EqualsAndHashCode because entities are supposed to be distinguished by their ID
@ToString(callSuper = true)
public class TestEntity extends FullTextSearchAwareEntity<String> {

    @Getter(value = AccessLevel.PROTECTED, onMethod_ = @Override)
    @ToString.Exclude
    @Transient
    private final Collection<Supplier<String>> fullTextSearchDataSuppliers = List.of(
            this::getField1, this::getField2, this::getField3);

    @Getter(onMethod_ = @Override)
    private String id;

    private String name;

    private String field1;
    private String field2;
    private String field3;

    @ToString.Exclude
    private String fullTextSearchData;

    @Override
    public NgramUtilsConfig getNgramUtilsConfig() {
        return NgramUtilsConfig.DEFAULT;
    }

}
