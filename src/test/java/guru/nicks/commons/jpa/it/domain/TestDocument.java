package guru.nicks.commons.jpa.it.domain;

import guru.nicks.commons.jpa.JpaInference;
import guru.nicks.commons.jpa.domain.FullTextSearchAwareEntity;
import guru.nicks.commons.jpa.it.TransactionInspector;
import guru.nicks.commons.utils.text.NgramUtilsConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nonnull;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
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
 * Test entity for the repository regression suite: full-text-search aware (its {@code name} feeds the ngram data),
 * has a JSON-ish {@code metadata} column for {@code JSON_CONTAINS} tests and a lazy {@code author} association for
 * entity-graph tests. The lifecycle callbacks record transaction state so that tests can verify the transactional
 * semantics of the fragment implementations (see {@code TransactionInspector}).
 */
@Entity
@Table(name = "test_document")
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
@SuperBuilder
public class TestDocument extends FullTextSearchAwareEntity<String> {

    /**
     * Mirrors {@code EnhancedSqlDialect.POSTGRES#getMaxFullTextSearchDataLength()}; duplicated as a constant because
     * annotation values must be compile-time constants.
     */
    private static final int MAX_FULL_TEXT_SEARCH_DATA_LENGTH = 1024 * 1024 - 1;

    @Id
    @Getter(onMethod_ = @Override)
    private String id;

    private String name;

    private String userId;

    // JSON-ish column, queried via createJsonContainsPredicate(...)
    private String metadata;

    // column name kept verbatim (no snake_case override) so that the SQL templates embedded by EnhancedSqlDialect,
    // which reference the camelCase property name, resolve in H2
    @Column(name = "fullTextSearchData", length = MAX_FULL_TEXT_SEARCH_DATA_LENGTH)
    private String fullTextSearchData;

    @Getter(value = AccessLevel.PROTECTED, onMethod_ = @Override)
    @JsonIgnore
    @ToString.Exclude
    @Transient
    private final Collection<Supplier<String>> fullTextSearchDataSuppliers = List.of(
            this::getName);

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "authorId")
    private TestAuthor author;

    @Override
    public int getMaxFullTextSearchDataLength() {
        return JpaInference.DEFAULT_SQL_DIALECT.getMaxFullTextSearchDataLength();
    }

    @Nonnull
    @Override
    public NgramUtilsConfig getNgramUtilsConfig() {
        return NgramUtilsConfig.DEFAULT;
    }

    @PostLoad
    private void captureTransactionOnLoad() {
        TransactionInspector.capture("load");
    }

    @PrePersist
    private void captureTransactionOnPersist() {
        TransactionInspector.capture("persist");
    }

}
