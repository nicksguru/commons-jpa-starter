package guru.nicks.commons.jpa.domain;

import com.querydsl.core.types.dsl.BooleanTemplate;
import com.querydsl.core.types.dsl.StringTemplate;
import org.springframework.util.unit.DataSize;

import java.util.Collection;

/**
 * Abstraction of DB-specific queries.
 */
public enum EnhancedSqlDialect {

    POSTGRES {
        @Override
        public String getJsonContainsTemplate() {
            return "CAST(JSON_CONTAINS(%s , '%s') AS int) = 1";
        }

        @Override
        public String getFullTextSearchTemplate() {
            return "CAST(FULL_TEXT_SEARCH(%s , '%s') AS int) = 1";
        }

        @Override
        public String getFullTextSearchRankTemplate() {
            return "CAST(FULL_TEXT_SEARCH_RANK(%s , '%s') AS double)";
        }

        /**
         * Joins the words with 'OR', otherwise Postgres treats them as 'AND', i.e. if at least one search
         * word is missing from a DB record, such record won't match.
         */
        @Override
        public String createLenientFullTextSearchCondition(Collection<String> words) {
            return String.join(" OR ", words);
        }

        /**
         * As per the <a href="https://www.postgresql.org/docs/14/textsearch-limitations.html">Postgres manual</a>
         * (see {@code tsvector}).
         */
        @Override
        public int getMaxFullTextSearchDataLength() {
            return Math.toIntExact(DataSize.ofMegabytes(1).toBytes() - 1);
        }

        @Override
        public String getNextSequenceValueTemplate() {
            return "SELECT nextval('%s')";
        }
    };

    /**
     * Template arguments: column name (of type {@code jsonb} in Postgres); value (JSON-escaped). Please sanitize them
     * to avoid SQL injection!
     *
     * @return template for searching inside a JSON column (in the form {@code condition = 1}, to be wrapped in a
     *         {@link BooleanTemplate}
     */
    public abstract String getJsonContainsTemplate();

    /**
     * Template arguments: column name (of type {@code tsvector} in Postgres); value (SQL-escaped text). Please sanitize
     * them to avoid SQL injection!
     *
     * @return template for searching inside a full-text search column (in the form {@code condition = 1}, to be wrapped
     *         in a {@link BooleanTemplate}
     */
    public abstract String getFullTextSearchTemplate();

    /**
     * Template arguments: column name (of type {@code tsvector} in Postgres); value (SQL-escaped text). Please sanitize
     * them to avoid SQL injection!
     *
     * @return template returning full-text search rank (as a {@code double}, suitable for sorting by it), to be wrapped
     *         in {@link StringTemplate}
     */
    public abstract String getFullTextSearchRankTemplate();

    /**
     * Create such a full-text search condition that succeeds if at least one of the input words matches.
     *
     * @param words words or ngrams to search for (please sanitize them to avoid SQL injection!)
     * @return search query
     */
    public abstract String createLenientFullTextSearchCondition(Collection<String> words);

    /**
     * @return maximum size of the full-text search column, in bytes
     */
    public abstract int getMaxFullTextSearchDataLength();

    /**
     * Template arguments: sequence name (please sanitize it to avoid SQL injection!).
     *
     * @return template for getting the next value of a sequence
     */
    public abstract String getNextSequenceValueTemplate();

}
