package guru.nicks.commons.jpa.domain;

import guru.nicks.commons.utils.crypto.ChecksumUtils;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Collection;
import java.util.Objects;
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
@NoArgsConstructor
@Getter
@Setter
//
@FieldNameConstants
@SuperBuilder
@ToString(callSuper = true)
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
     * Initial {@link StringBuilder} capacity for accumulating n-grams.
     */
    private static final int ESTIMATED_FTS_BUILDER_CAPACITY = 1024;

    /**
     * Estimated length of the entity field for generating n-grams.
     */
    private static final int ESTIMATED_FTS_AWARE_FIELD_LENGTH = 50;

    /**
     * Assigned by {@link #rebuildFullTextSearchNgrams()} and stored in DB to avoid costly ngram recalculation if the
     * search content has not changed.
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
     * persistent properties have changed in memory). Assigns {@link #getFullTextSearchData()} and
     * {@link #getFullTextSearchDataChecksum()} using {@link #getFullTextSearchDataSuppliers()} and {@link NgramUtils}.
     */
    @PrePersist
    @PreUpdate
    @SuppressWarnings("JpaEntityListenerInspection") // it's OK to have the same callbacks in parent class
    private void rebuildFullTextSearchNgrams() {
        // compute checksum of raw text, not of ngrams (the point is to avoid calculating ngrams for unchanged text)
        String text = callFullTextSearchDataSuppliers();
        String newChecksum = ChecksumUtils.computeJsonChecksumBase64(text);

        // ignore blank checksum - this should never happen, but just to prevent the app from crashing in case of a bug
        if (StringUtils.isBlank(newChecksum)) {
            log.error("FTS checksum blank - this should never happen! Rebuilding FTS ngrams for [{}] ID '{}' anyway.",
                    getClass().getName(), getId());
        }
        // do nothing if search content has not changed since previous ngram computation
        else if (newChecksum.equals(fullTextSearchDataChecksum)) {
            log.debug("Not rebuilding FTS ngrams: content not changed for [{}] ID '{}'", getClass().getName(), getId());
            return;
        }

        var builder = new StringBuilder(ESTIMATED_FTS_BUILDER_CAPACITY);
        var ngrams = NgramUtils.createNgrams(text, NgramUtils.Mode.ALL, getNgramUtilsConfig());
        // stop appending ngrams as soon as the limit is reached
        ngrams.stream()
                .takeWhile(ngram -> {
                    int separatorLength = builder.isEmpty() ? 0 : 1;
                    return builder.length() + separatorLength + ngram.length() <= getMaxFullTextSearchDataLength();
                }).forEach(ngram -> {
                    if (!builder.isEmpty()) {
                        builder.append(" ");
                    }

                    builder.append(ngram);
                });

        // in Postgres, tsvector doesn't look exactly like this, but it doesn't matter - it can be written as a string
        setFullTextSearchData(builder.toString());
        fullTextSearchDataChecksum = newChecksum;

        if (log.isTraceEnabled()) {
            log.trace("Rebuilt FTS ngrams for [{}] ID '{}': '{}'", getClass().getName(), getId(),
                    FullTextSearchAwareEntity.FULL_TEXT_SEARCH_DATA_PROPERTY);
        } else {
            log.debug("Rebuilt FTS ngrams for [{}] ID '{}':", getClass().getName(), getId());
        }
    }

    @Nonnull
    private String callFullTextSearchDataSuppliers() {
        Collection<Supplier<String>> suppliers = getFullTextSearchDataSuppliers();

        if (CollectionUtils.isEmpty(suppliers)) {
            return "";
        }

        // estimate initial capacity based on field count and average field length
        int estimatedCapacity = Math.max(
                ESTIMATED_FTS_BUILDER_CAPACITY,
                suppliers.size() * ESTIMATED_FTS_AWARE_FIELD_LENGTH);
        var builder = new StringBuilder(estimatedCapacity);

        // do not process each field individually - let the ngram creator detect unique words
        suppliers.stream()
                .filter(Objects::nonNull)
                .map(Supplier::get)
                .filter(StringUtils::isNotBlank)
                // this is more memory-effective than 'Collectors.joining(" ")' for large texts
                .forEach(str -> {
                    if (!builder.isEmpty()) {
                        builder.append(" ");
                    }

                    builder.append(str);
                });

        return builder.toString();
    }

}
