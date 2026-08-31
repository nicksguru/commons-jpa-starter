package guru.nicks.commons.jpa.domain;

import guru.nicks.commons.utils.crypto.ChecksumUtils;
import guru.nicks.commons.utils.text.EnglishUtils;
import guru.nicks.commons.utils.text.NgramUtils;
import guru.nicks.commons.utils.text.NgramUtilsConfig;
import guru.nicks.commons.utils.text.TextUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.Basic;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.SequencedSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
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
     * Initial {@link StringBuilder} capacity for accumulating n-grams.
     */
    private static final int ESTIMATED_FTS_BUILDER_CAPACITY = 1024;

    /**
     * Estimated length of the entity field for generating n-grams.
     */
    private static final int ESTIMATED_FTS_AWARE_FIELD_LENGTH = 50;

    /**
     * UTF-8 bytes of the single-space separator inserted between kept supplier values, cached to avoid re-encoding it
     * per value while streaming the checksum.
     */
    private static final byte[] FTS_VALUE_SEPARATOR_BYTES = " ".getBytes(StandardCharsets.UTF_8);

    /**
     * Assigned by {@link #rebuildFullTextSearchData()} and stored in DB to avoid costly ngram recalculation if the
     * search content has not changed.
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
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
     * Splits text into chunks for FTS.
     *
     * @param text   source text
     * @param config ngram utils configuration
     * @return set of chunks to use for FTS:
     *         <ul>
     *             <li>original unique words shorter then {@link NgramUtilsConfig#getMinNgramLength()} - with accents
     *                 reduced (such as {@code ä → a}) if {@link NgramUtilsConfig#isReduceAccents()} is on and stop
     *                 words (such as 'the', 'a', 'was', 'I') removed if
     *                 {@link NgramUtilsConfig#tryEnglishMorphAnalysis()} is on</li>
     *             <li>ngrams created according to {@link NgramUtilsConfig}</li>
     *         </ul>
     */
    public static SequencedSet<String> createFullTextSearchChunks(String text, NgramUtilsConfig config) {
        // tokenize once - both the short-words phase and ngram creation below reuse the same word set
        SequencedSet<String> uniqueWords = TextUtils.collectUniqueWords(text, config.isReduceAccents());

        // add words that are shorter than the minimum ngram length, otherwise they'll be omitted
        SequencedSet<String> chunks = uniqueWords.stream()
                .filter(word -> word.length() < config.getMinNgramLength())
                // either English morph analysis is off or the word is not an English stop word (fast path - words
                // from collectUniqueWords are already lowercase and trimmed)
                .filter(word -> !config.tryEnglishMorphAnalysis() || !EnglishUtils.stopWord(word, true))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        chunks.addAll(NgramUtils.createNgrams(uniqueWords, NgramUtils.Mode.ALL, config));

        // this should never happen after the TextUtils call, but just in case
        if (chunks.stream().anyMatch(ngram ->
                ngram.contains("'") || ngram.contains("\"") || ngram.contains("--") || ngram.contains(";"))) {
            throw new IllegalArgumentException("Invalid characters (SQL injection?) in search text");
        }

        return chunks;
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
     * <p>
     * The checksum is streamed while the supplier values are being collected, so on the common unchanged-content path
     * neither the joined text nor its byte representation is ever materialized.
     */
    @PrePersist
    @PreUpdate
    @SuppressWarnings("JpaEntityListenerInspection") // it's OK to have the same callback in parent class
    public void rebuildFullTextSearchData() {
        // compute checksum of raw text, not of ngrams (the point is to avoid calculating ngrams for unchanged text)
        FullTextSearchData ftsData = callFullTextSearchDataSuppliers();
        String newChecksum = ftsData.checksum();

        // ignore blank checksum - this should never happen, but just to prevent the app from crashing in case of a bug
        if (StringUtils.isBlank(newChecksum)) {
            log.error("FTS checksum blank - this should never happen! Rebuilding FTS ngrams for [{}] ID '{}' anyway.",
                    getClass().getName(), getId());
        }
        // do nothing if search content has not changed since previous computation
        else if (newChecksum.equals(fullTextSearchDataChecksum)) {
            if (log.isTraceEnabled()) {
                log.trace("Not rebuilding FTS chunks: content not changed for [{}] ID '{}'",
                        getClass().getName(), getId());
            }

            return;
        }

        // Content has changed - only now pay for materializing the joined text.
        // In Postgres, tsvector doesn't look exactly like this, but it doesn't matter - it can be written as a string.
        setFullTextSearchData(buildFullTextSearchData(ftsData.builder(), getNgramUtilsConfig()));
        fullTextSearchDataChecksum = newChecksum;

        if (log.isTraceEnabled()) {
            log.trace("Content changed - rebuilt FTS data for [{}] ID '{}': '{}'", getClass().getName(), getId(),
                    FullTextSearchAwareEntity.FULL_TEXT_SEARCH_DATA_PROPERTY);
        } else {
            log.info("Content changed - rebuilt FTS data for [{}] ID '{}':", getClass().getName(), getId());
        }
    }

    /**
     * Collects full-text search data from {@link #getFullTextSearchDataSuppliers()} in a single pass, appending each
     * kept value to a builder (needed only when the content has changed) while feeding an SHA-256 digest with exactly
     * the bytes the joined text would produce: UTF-8 bytes of each value plus a single-space separator between values
     * ({@code null} suppliers and blank values are skipped, an empty suppliers collection yields the digest of zero
     * bytes).
     * <p>
     * The resulting checksum is therefore byte-identical to
     * {@link ChecksumUtils#computeJsonChecksum(Object) ChecksumUtils.computeJsonChecksum(joinedText)}, which avoids a
     * one-time rebuild of existing DB rows.
     *
     * @return collected search data: the accumulated builder plus the streaming checksum of the same content
     */
    @Nonnull
    private FullTextSearchData callFullTextSearchDataSuppliers() {
        Collection<Supplier<String>> suppliers = getFullTextSearchDataSuppliers();
        var digest = createEmptyDigest();

        // no suppliers - digest of zero bytes, matching the checksum of an empty text
        if (CollectionUtils.isEmpty(suppliers)) {
            return new FullTextSearchData(new StringBuilder(0), encodeChecksum(digest));
        }

        // estimate initial capacity based on field count and average field length
        int estimatedCapacity = Math.max(
                ESTIMATED_FTS_BUILDER_CAPACITY,
                suppliers.size() * ESTIMATED_FTS_AWARE_FIELD_LENGTH);
        var sb = new StringBuilder(estimatedCapacity);

        // do not process each field individually - let the ngram creator detect unique words;
        // this is more memory-effective than 'Collectors.joining(" ")' for large texts
        for (Supplier<String> supplier : suppliers) {
            if (supplier == null) {
                continue;
            }

            String value = supplier.get();

            if (StringUtils.isBlank(value)) {
                continue;
            }

            // update BOTH the builder AND the digest
            if (!sb.isEmpty()) {
                sb.append(" ");
                digest.update(FTS_VALUE_SEPARATOR_BYTES);
            }

            // update BOTH the builder AND the digest
            sb.append(value);
            digest.update(value.getBytes(StandardCharsets.UTF_8));
        }

        // at this point, the builder is not materialized, but the digest is
        return new FullTextSearchData(sb, encodeChecksum(digest));
    }

    /**
     * Appends a single chunk using the following semantics: a single space before every chunk except the first, with
     * the separator counted as part of the chunk when checking the length cap.
     *
     * @param builder   builder accumulating the chunks
     * @param chunk     chunk to append
     * @param maxLength maximum length of the full-text search data
     * @return {@code true} if the chunk fit and was appended, {@code false} if it would exceed the length cap
     */
    private boolean appendFullTextSearchChunk(StringBuilder builder, String chunk, int maxLength) {
        int separatorLength = builder.isEmpty() ? 0 : 1;

        // stop appending chunks as soon as the limit is reached (break, not skip)
        if (builder.length() + separatorLength + chunk.length() > maxLength) {
            return false;
        }

        if (!builder.isEmpty()) {
            builder.append(' ');
        }

        builder.append(chunk);
        return true;
    }

    /**
     * Checks each word's characters for the SQL-injection-suspicious ones (''', '"', ';' and the '--' sequence). Every
     * chunk is a substring of a word (short words are words, ngrams are word substrings, lemmas derive from words), so
     * word-level checking is equivalent to chunk-level checking - and it also covers chunks the length cap would drop.
     *
     * @param uniqueWords unique words extracted from the search text
     * @throws IllegalArgumentException a word contains characters suspicious of SQL injection (effectively unreachable
     *                                  because tokenization strips them - kept for behavioral compatibility)
     */
    private void validateWordsAreSqlInjectionFree(Set<String> uniqueWords) {
        for (String word : uniqueWords) {
            // flag: the previous character was '-', to detect the '--' sequence
            boolean previousCharWasHyphen = false;

            for (int i = 0, wordLength = word.length(); i < wordLength; i++) {
                char character = word.charAt(i);

                if ((character == '\'')
                        || (character == '"')
                        || (character == ';')
                        || (previousCharWasHyphen && (character == '-'))) {
                    throw new IllegalArgumentException("Invalid characters (SQL injection?) in search text");
                }

                previousCharWasHyphen = (character == '-');
            }
        }
    }

    /**
     * Creates a fresh SHA-256 digest - the same algorithm {@link ChecksumUtils#computeJsonChecksum(Object)} uses.
     *
     * @return new digest instance (not thread-safe, never shared)
     * @throws IllegalStateException SHA-256 is unavailable (impossible on a compliant JVM)
     */
    @Nonnull
    private MessageDigest createEmptyDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        // every Java platform implementation is required to support SHA-256 - unreachable
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Completes the streaming checksum in exactly the format {@link ChecksumUtils#computeJsonChecksum(Object)}
     * produces: basic (non-MIME, non-url-safe) Base64 with padding and no line wrapping.
     *
     * @param digest digest fed with the content bytes
     * @return Base64-encoded checksum
     */
    @Nonnull
    private String encodeChecksum(MessageDigest digest) {
        return Base64.getEncoder().encodeToString(digest.digest());
    }

    /**
     * Validates the tokenized words, then appends short words and ngrams into a pre-sized builder, stopping at the
     * first chunk that would exceed the length cap.
     *
     * @param source builder holding joined search text to create chunks of
     * @param config ngram utils configuration
     * @return builder holding the length-capped chunk sequence, pre-sized to never exceed the length cap
     * @throws IllegalArgumentException search text contains characters suspicious of SQL injection (effectively
     *                                  unreachable because tokenization strips them - kept for behavioral
     *                                  compatibility)
     */
    @Nonnull
    private String buildFullTextSearchData(StringBuilder source, NgramUtilsConfig config) {
        // tokenize once - validation, the short-words phase and ngram creation below reuse the same word set
        SortedSet<String> uniqueWords = TextUtils.collectUniqueWords(source.toString(), config.isReduceAccents());
        validateWordsAreSqlInjectionFree(uniqueWords);

        int minNgramLength = config.getMinNgramLength();
        int maxPrefixNgramLength = config.getMaxPrefixNgramLength();
        int maxInfixNgramLength = config.getMaxInfixNgramLength();
        int maxNgramCount = config.getMaxNgramCount();

        // Pre-size the builder to min(cap, estimate): never allocate past the DB cap, yet skip the 1024 -> 1MB
        // doubling chain for large word sets. The estimate is deliberately rough (estimated chunk count times the
        // average chunk length) because StringBuilder grows gracefully when it is off.
        int averageChunkLength = (minNgramLength + Math.max(maxPrefixNgramLength, maxInfixNgramLength)) / 2 + 1;
        int estimatedNgramCount = Math.min(uniqueWords.size() * NgramUtils.ASSUMED_NGRAMS_PER_WORD, maxNgramCount);
        int estimatedTotalLength = (uniqueWords.size() + estimatedNgramCount) * averageChunkLength;

        // hoisted once per rebuild: the length cap used to be a virtual call per chunk, and the config getters are
        // interface default methods that may be computed in subclasses
        int maxLength = getMaxFullTextSearchDataLength();
        int estimatedCapacity = Math.min(Math.max(0, maxLength),
                Math.max(ESTIMATED_FTS_BUILDER_CAPACITY, estimatedTotalLength));
        var builder = new StringBuilder(estimatedCapacity);

        // phase 1: short words (alphabetical, from the sorted word set) - appended only if shorter than the minimum
        // ngram length (otherwise they're already part of their ngrams) and not filtered out as English stop words
        boolean lengthCapReached = false;

        for (String word : uniqueWords) {
            if (word.length() >= minNgramLength) {
                continue;
            }

            // either English morph analysis is off or the word is not an English stop word (fast path - words are
            // already lowercase and trimmed)
            if (config.tryEnglishMorphAnalysis() && EnglishUtils.stopWord(word, true)) {
                continue;
            }

            if (!appendFullTextSearchChunk(builder, word, maxLength)) {
                lengthCapReached = true;
                break;
            }
        }

        // phase 2: ngrams in creation order; skipped entirely if the length cap already stopped the short-words phase
        if (!lengthCapReached) {
            for (String ngram : NgramUtils.createNgrams(uniqueWords, NgramUtils.Mode.ALL, config)) {
                if (!appendFullTextSearchChunk(builder, ngram, maxLength)) {
                    break;
                }
            }
        }

        return builder.toString();
    }

    /**
     * Single-pass collection result: the accumulated builder of the joined search text plus the streaming checksum of
     * the exact bytes that text would produce. The builder is materialized (via {@code toString()}) only when the
     * content has changed, keeping the common unchanged-content path free of full-text copies.
     *
     * @param builder  joined search text (kept values separated by a single space, {@code null} suppliers and blank
     *                 values skipped)
     * @param checksum Base64 SHA-256 checksum of the joined text bytes, identical to
     *                 {@link ChecksumUtils#computeJsonChecksum(Object)} of the materialized text
     */
    private record FullTextSearchData(

            StringBuilder builder,
            String checksum) {
    }

}
