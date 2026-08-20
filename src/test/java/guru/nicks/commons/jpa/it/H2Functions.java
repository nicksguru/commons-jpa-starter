package guru.nicks.commons.jpa.it;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

/**
 * H2 implementations of the unified DB functions referenced by {@code EnhancedSqlDialect} templates. They're bound to
 * the database via {@code CREATE ALIAS} in {@code schema-jpa-it.sql} so that the FTS/JSON code paths are truly
 * executed against H2 in the regression tests.
 */
public final class H2Functions {

    private H2Functions() {
    }

    /**
     * Emulates {@code FULL_TEXT_SEARCH(columnValue, query)}: returns 1 when the stored ngram data contains at least
     * one of the search chunks.
     *
     * @param columnValue value of the {@code fullTextSearchData} column (space-separated ngrams)
     * @param query search condition built by {@code EnhancedSqlDialect#createLenientFullTextSearchCondition(...)} -
     *              chunks joined with ' OR '
     * @return 1 when at least one chunk matches, 0 otherwise
     */
    public static int fullTextSearch(String columnValue, String query) {
        return countMatchingChunks(columnValue, query) > 0 ? 1 : 0;
    }

    /**
     * Emulates {@code FULL_TEXT_SEARCH_RANK(columnValue, query)}: the more chunks match, the greater is the rank.
     *
     * @param columnValue value of the {@code fullTextSearchData} column (space-separated ngrams)
     * @param query search condition built by {@code EnhancedSqlDialect#createLenientFullTextSearchCondition(...)} -
     *              chunks joined with ' OR '
     * @return number of matching chunks
     */
    public static double fullTextSearchRank(String columnValue, String query) {
        return countMatchingChunks(columnValue, query);
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
     * Counts how many chunks of the lenient search condition are contained in the stored ngram data.
     *
     * @param columnValue value of the {@code fullTextSearchData} column (space-separated ngrams)
     * @param query search condition - chunks joined with ' OR '
     * @return number of matching chunks
     */
    private static int countMatchingChunks(String columnValue, String query) {
        if (columnValue == null || StringUtils.isBlank(query)) {
            return 0;
        }

        // the dialect joins the search chunks with ' OR '
        return (int) Arrays.stream(query.split(" OR "))
                .filter(StringUtils::isNotBlank)
                .filter(columnValue::contains)
                .count();
    }

}
