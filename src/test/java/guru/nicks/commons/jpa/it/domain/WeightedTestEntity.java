package guru.nicks.commons.jpa.it.domain;

import guru.nicks.commons.jpa.JpaInference;
import guru.nicks.commons.jpa.domain.FullTextSearchAwareEntity;
import guru.nicks.commons.utils.text.NgramUtilsConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nonnull;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Test entity for the weighted-tsvector regression scenario: identical to {@link TestEntity} in what feeds the ngram
 * data (its {@code name}), but its ngram config turns {@link NgramUtilsConfig#isWeightedTsvector()} on, so the stored
 * {@code fullTextSearchData} is the annotated {@code 'chunk':positionWeight} format ranked by the weight-aware H2
 * emulation of {@code FULL_TEXT_SEARCH_RANK}.
 */
@Entity
@Table(name = "weighted_test")
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
@SuperBuilder
public class WeightedTestEntity extends FullTextSearchAwareEntity<String> {

    /**
     * Mirrors {@code EnhancedSqlDialect.POSTGRES#getMaxFullTextSearchDataLength()}; duplicated as a constant because
     * annotation values must be compile-time constants.
     */
    private static final int MAX_FULL_TEXT_SEARCH_DATA_LENGTH = 1024 * 1024 - 1;

    /**
     * Production defaults plus the weighted tsvector emission - the config under test.
     */
    private static final NgramUtilsConfig WEIGHTED_CONFIG = new NgramUtilsConfig() {
        @Override
        public boolean isWeightedTsvector() {
            return true;
        }
    };

    @Id
    @Getter(onMethod_ = @Override)
    private String id;

    private String name;

    // column name kept verbatim (no snake_case override) so that the SQL templates embedded by EnhancedSqlDialect,
    // which reference the camelCase property name, resolve in H2
    @Column(name = "fullTextSearchData", length = MAX_FULL_TEXT_SEARCH_DATA_LENGTH)
    private String fullTextSearchData;

    @Override
    public int getMaxFullTextSearchDataLength() {
        return JpaInference.DEFAULT_SQL_DIALECT.getMaxFullTextSearchDataLength();
    }

    @Nonnull
    @Override
    public NgramUtilsConfig getNgramUtilsConfig() {
        return WEIGHTED_CONFIG;
    }

    @JsonIgnore
    @Transient
    @Override
    protected Collection<Supplier<String>> getFullTextSearchDataSuppliers() {
        return List.of(this::getName);
    }

}
