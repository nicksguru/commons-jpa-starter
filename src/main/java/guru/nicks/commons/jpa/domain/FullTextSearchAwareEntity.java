package guru.nicks.commons.jpa.domain;

import guru.nicks.commons.utils.text.FullTextSearchUtils;
import guru.nicks.commons.utils.text.NgramUtils;
import guru.nicks.commons.utils.text.NgramUtilsConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Basic;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Collection;
import java.util.function.Supplier;

import static guru.nicks.commons.validation.dsl.ValiDsl.checkNotNull;

/**
 * Base class for entities that support full-text search capabilities. The following columns are required (in Liquibase
 * syntax) depending on the database being used. For example, for PostgreSQL:
 * <pre>
 *  &lt;column name="full_text_search_data" type="tsvector"/&gt;
 *  &lt;column name="full_text_search_data_checksum" type="varchar(255)"/&gt;
 * </pre>
 * For the above example, an abstract subclass should be created with the following property:
 * <pre>
 *  &#64;ToString.Exclude
 *  &#64;Type(PostgreSQLTSVectorType.class)
 *  private String fullTextSearchData;
 * </pre>
 * <p>
 * This implementation uses n-grams for better partial word matching and handles automatic generation of search data
 * during entity persistence operations:
 * <ul>
 *   <li>search data checksum helps avoid overwriting costly n-gram recalculation for unchanged content</li>
 *   <li>n-grams are generated from entity text fields to support partial and fuzzy matching</li>
 *   <li>search data is automatically updated on entity insert/update</li>
 *   <li>maximum length of search data is limited by {@link EnhancedSqlDialect#getMaxFullTextSearchDataLength()}</li>
 * </ul>
 *
 * @param <ID> entity ID type
 * @see #getFullTextSearchDataSuppliers()
 */
@MappedSuperclass
//
@NoArgsConstructor
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
// for entity graphs
@FieldNameConstants
@SuperBuilder
@Slf4j
@SuppressWarnings("java:S119") // allow non-single-letter type names in generics
public abstract class FullTextSearchAwareEntity<ID> extends AuditableEntity<ID> {

    /**
     * Non-existing property name which indicates the intention to sort by the search rank (desc).
     *
     * @see #initSortCriteria(String, Pageable)
     */
    public static final String SEARCH_RANK_PSEUDOFIELD = "_searchRank";

    /**
     * Property name for subclasses to declare for holding full-text search data.
     */
    public static final String FULL_TEXT_SEARCH_DATA_PROPERTY = "fullTextSearchData";

    /**
     * Assigned by {@link #rebuildFullTextSearchData(boolean)} and stored in DB to avoid costly ngram recalculation if
     * the search content has not changed.
     */
    @ToString.Exclude
    @Basic // formally optional (applied by default), but QueryDSL doesn't see this property without this annotation
    private String fullTextSearchDataChecksum;

    /**
     * If sorting criteria are undefined (or {@value #SEARCH_RANK_PSEUDOFIELD} is mentioned there), sets the field name
     * to sort by: if the search text is not blank, sets {@value #SEARCH_RANK_PSEUDOFIELD} to sort by search rank
     * (desc), else sets {@link AuditableEntity.Fields#createdDate} (desc), which in Postgres gives a microsecond
     * precision.
     * <p>
     * The above means that if caller specified sort by search rank (asc), this method overrides it with 'desc'.
     *
     * @param fullTextSearch full-text search string, if any; can be {@code null}
     * @param pageable       pagination request
     * @return old pagination request if sort criteria were already there, new request otherwise
     */
    public static Pageable initSortCriteria(@Nullable String fullTextSearch, Pageable pageable) {
        checkNotNull(pageable, "pageable");

        // caller intends to sort, but not by search rank
        if (pageable.getSort().isSorted() && (pageable.getSort().getOrderFor(SEARCH_RANK_PSEUDOFIELD) == null)) {
            return pageable;
        }

        // sort by search rank (desc, even if caller specified asc) or by date of creation (desc)
        String sortField = StringUtils.isNotBlank(fullTextSearch)
                ? SEARCH_RANK_PSEUDOFIELD
                : AuditableEntity.Fields.createdDate;
        Sort newSort = Sort.by(
                Sort.Order.desc(sortField));

        return pageable.isUnpaged()
                ? Pageable.unpaged(newSort)
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), newSort);
    }

    /**
     * Assigned automatically during each insert/update using data from {@link #getFullTextSearchDataSuppliers()}.
     * <p>
     * WARNING: this field is only updated when using JPA to save documents. A rough estimate is that 100 words yield
     * 1000 ngrams.
     */
    public abstract String getFullTextSearchData();

    /**
     * Sets the full-text search data. Typically called internally when search ngrams are rebuilt.
     *
     * @param value the generated n-gram string to be persisted.
     */
    public abstract void setFullTextSearchData(String value);

    /**
     * @return the maximum length of the full-text search data field, presumably borrowed from
     *         {@link EnhancedSqlDialect#getMaxFullTextSearchDataLength()}
     */
    @JsonIgnore
    @Transient
    public abstract int getMaxFullTextSearchDataLength();

    /**
     * Returns the configuration for n-gram generation used in full-text search.
     * <p>
     * This configuration determines how text is tokenized and converted into n-grams for search indexing. Subclasses
     * must provide their own implementation to specify the n-gram generation parameters such as minimum and maximum
     * n-gram size.
     *
     * @return the n-gram configuration to use for generating search data
     */
    @JsonIgnore
    @Transient
    @Nonnull
    public abstract NgramUtilsConfig getNgramUtilsConfig();

    /**
     * Suppliers are responsible for explicit stringification of property values: lists, enums, numbers, etc. This gives
     * more predictable results then, for example, calling {@link Object#toString()} in this method.
     * <p>
     * With Lombok, subclasses can declare this as a field with a protected getter, annotated with
     * {@link ToString#exclude()}.
     *
     * @return search data suppliers, such as property getters; {@code null} suppliers and blank values are ignored
     */
    @JsonIgnore
    @Transient
    @Nonnull
    protected abstract Collection<Supplier<String>> getFullTextSearchDataSuppliers();

    /**
     * Called by Hibernate when it has decided to insert a new entity in DB or update an existing one (i.e. some
     * persistent properties have changed in memory).
     * <p>
     * Rebuilds {@link #getFullTextSearchData()} and {@link #getFullTextSearchDataChecksum()} using
     * {@link #getFullTextSearchDataSuppliers()} and {@link NgramUtils} if FTS content has changed since the last
     * rebuild (as per its checksum).
     * <p>
     * The checksum is streamed while the supplier values are being collected, so on the common unchanged-content path
     * neither the joined text nor its byte representation is ever materialized.
     */
    @PrePersist
    @PreUpdate
    @SuppressWarnings("JpaEntityListenerInspection") // it's OK to have the same callback in parent class
    private void rebuildFullTextSearchDataBeforeInsertOrUpdate() {
        rebuildFullTextSearchData(false);
    }

    /**
     * Rebuilds {@link #getFullTextSearchData()} and {@link #getFullTextSearchDataChecksum()} using
     * {@link #getFullTextSearchDataSuppliers()} and {@link NgramUtils}. This is what the JPA callbacks on insert/update
     * invoke (with {@code enforce = false}).
     * <p>
     * The checksum is streamed while the supplier values are being collected, so on the common unchanged-content path
     * neither the joined text nor its byte representation is ever materialized.
     *
     * @param enforce {@code true} rebuilds unconditionally, ignoring an identical checksum - use for batch reindexing
     *                after ngram-logic changes; {@code false} skips the rebuild when the checksum is unchanged, i.e.
     *                the JPA-callback behavior
     */
    public void rebuildFullTextSearchData(boolean enforce) {
        // compute checksum of raw text, not of ngrams (the point is to avoid re-calculating ngrams for unchanged text)
        FullTextSearchUtils.FtsDataSource ftsSource = FullTextSearchUtils.collectFtsDataSource(
                getFullTextSearchDataSuppliers());
        String newChecksum = ftsSource.checksum();

        // ignore blank checksum - this should never happen, but just to prevent the app from crashing in case of a bug
        if (StringUtils.isBlank(newChecksum)) {
            log.error("FTS checksum blank - this should never happen! Rebuilding FTS ngrams for [{}] ID '{}' anyway.",
                    getClass().getName(), getId());
        }
        // do nothing if search content has not changed since previous computation
        else if (!enforce && newChecksum.equals(fullTextSearchDataChecksum)) {
            if (log.isTraceEnabled()) {
                log.trace("Not rebuilding FTS chunks: content not changed for [{}] ID '{}'",
                        getClass().getName(), getId());
            }

            return;
        }

        // Content has changed - only now pay for materializing the joined text.
        // In Postgres, tsvector doesn't look exactly like this, but it doesn't matter - it can be written as a string.
        setFullTextSearchData(FullTextSearchUtils.buildFtsData(
                ftsSource.builder(), getNgramUtilsConfig(), getMaxFullTextSearchDataLength()));
        fullTextSearchDataChecksum = newChecksum;

        if (log.isTraceEnabled()) {
            log.trace("Content changed - rebuilt FTS data for [{}] ID '{}': '{}'", getClass().getName(), getId(),
                    FullTextSearchAwareEntity.FULL_TEXT_SEARCH_DATA_PROPERTY);
        } else {
            log.info("Content changed - rebuilt FTS data for [{}] ID '{}':", getClass().getName(), getId());
        }
    }

}
