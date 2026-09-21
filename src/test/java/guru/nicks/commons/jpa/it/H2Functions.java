package guru.nicks.commons.jpa.it;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * H2 implementations of the unified DB functions referenced by {@code EnhancedSqlDialect} templates. They're bound to
 * the database via {@code CREATE ALIAS} in {@code schema-jpa-it.sql} so that the FTS/JSON code paths are truly
 * executed against H2 in the regression tests.
 * <p>
 * The full-text functions accept both stored formats: the plain one (space-separated chunks) and the weighted tsvector
 * one ({@code 'chunk':positionWeight} chunks produced when {@code isWeightedTsvector()} is on). The weighted format is
 * parsed and ranked the way PostgreSQL would: exact-lexeme matching plus {@code ts_rank}'s default weights
 * ({@code A = 1.0}, {@code B = 0.4}), so that prefix ngram matches rank above infix ones.
 */
public final class H2Functions {

    /**
     * Annotation of a single lexeme in the weighted tsvector input format, e.g. {@code 'gur':1A}.
     */
    private static final Pattern WEIGHTED_CHUNK_PATTERN = Pattern.compile("'([^']+)':\\d+([AB])");

    /**
     * ts_rank default weights for the letters used by the weighted format (see the PostgreSQL manual).
     */
    private static final double WEIGHT_A = 1.0;

    private static final double WEIGHT_B = 0.4;

    private H2Functions() {
    }

    /**
     * Emulates {@code FULL_TEXT_SEARCH(columnValue, query)}: returns 1 when the stored ngram data contains at least
     * one of the search chunks.
     *
     * @param columnValue value of the {@code fullTextSearchData} column (plain or weighted chunks)
     * @param query search condition built by {@code EnhancedSqlDialect#createLenientFullTextSearchCondition(...)} -
     *              chunks joined with ' OR '
     * @return 1 when at least one chunk matches, 0 otherwise
     */
    public static int fullTextSearch(String columnValue, String query) {
        return ftsRank(columnValue, query) > 0 ? 1 : 0;
    }

    /**
     * Emulates {@code FULL_TEXT_SEARCH_RANK(columnValue, query)}: for the plain format, the more chunks match, the
     * greater is the rank; for the weighted format, matching chunks contribute their ts_rank default weights
     * ({@code A = 1.0}, {@code B = 0.4}), so a prefix ngram match outweighs an infix one.
     *
     * @param columnValue value of the {@code fullTextSearchData} column (plain or weighted chunks)
     * @param query search condition built by {@code EnhancedSqlDialect#createLenientFullTextSearchCondition(...)} -
     *              chunks joined with ' OR '
     * @return weighted sum (weighted format) or count (plain format) of matching chunks
     */
    public static double fullTextSearchRank(String columnValue, String query) {
        return ftsRank(columnValue, query);
    }

    /**
     * Emulates {@code JSON_CONTAINS(columnValue, jsonValue)}: checks whether the stored JSON contains the given
     * JSON-encoded value as a substring.
     *
     * @param columnValue value of a JSON column
     * @param jsonValue JSON-encoded value to look for (as produced by Jackson in
     *                  {@code EnhancedJpaSearchRepositoryFragmentImpl#createJsonContainsPredicate(...)})
     * @return 1 when the value is contained, 0 otherwise
     */
    public static int jsonContains(String columnValue, String jsonValue) {
        if (columnValue == null || jsonValue == null) {
            return 0;
        }

        return columnValue.contains(jsonValue) ? 1 : 0;
    }

    /**
     * Computes the search rank of the lenient condition against the stored ngram data. The weighted format (chunks
     * quoted and annotated) is detected by a leading quote: its lexemes are matched exactly (PostgreSQL matches whole
     * lexemes, no substrings) and summed with their weights, like {@code ts_rank} does. The plain format keeps the
     * legacy substring matching with the rank equal to the match count.
     *
     * @param columnValue value of the {@code fullTextSearchData} column (plain or weighted chunks)
     * @param query search condition - chunks joined with ' OR '
     * @return weighted sum (weighted format) or count (plain format) of matching chunks
     */
    private static double ftsRank(String columnValue, String query) {
        if (columnValue == null || StringUtils.isBlank(query)) {
            return 0;
        }

        if (columnValue.contains("'")) {
            Map<String, Double> lexemeWeights = collectLexemeWeights(columnValue);

            // the dialect joins the search chunks with ' OR '
            return Arrays.stream(query.split(" OR "))
                    .filter(StringUtils::isNotBlank)
                    .mapToDouble(chunk -> lexemeWeights.getOrDefault(chunk, 0.0))
                    .sum();
        }

        // the dialect joins the search chunks with ' OR '
        return Arrays.stream(query.split(" OR "))
                .filter(StringUtils::isNotBlank)
                .filter(columnValue::contains)
                .count();
    }

    /**
     * Parses the weighted tsvector input format into a lexeme-to-weight map.
     *
     * @param columnValue value of the {@code fullTextSearchData} column in the weighted format
     * @return map of every stored lexeme to its ts_rank default weight
     */
    private static Map<String, Double> collectLexemeWeights(String columnValue) {
        Map<String, Double> lexemeWeights = new HashMap<>();
        Matcher matcher = WEIGHTED_CHUNK_PATTERN.matcher(columnValue);

        while (matcher.find()) {
            lexemeWeights.put(matcher.group(1), "A".equals(matcher.group(2)) ? WEIGHT_A : WEIGHT_B);
        }

        return lexemeWeights;
    }

}
