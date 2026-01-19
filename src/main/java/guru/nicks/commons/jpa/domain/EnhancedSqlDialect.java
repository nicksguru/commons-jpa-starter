package guru.nicks.commons.jpa.domain;

import com.querydsl.core.types.dsl.BooleanTemplate;
import com.querydsl.core.types.dsl.StringTemplate;
import org.springframework.util.unit.DataSize;

import java.time.Instant;
import java.time.LocalDate;
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

        @Override
        public String getTimestampToDateTemplate() {
            return "DATE(%s AT TIME ZONE '%s')";
        }

        @Override
        public String getTimestampToDateBeginningOfWeekTemplate() {
            return "DATE(DATE_TRUNC('week', %s AT TIME ZONE '%s'))";
        }

        @Override
        public String getTimestampToDateBeginningOfMonthTemplate() {
            return "DATE(DATE_TRUNC('month', %s AT TIME ZONE '%s'))";
        }

        @Override
        public String getTimestampToDateBeginningOfQuarterTemplate() {
            return "DATE(DATE_TRUNC('quarter', %s AT TIME ZONE '%s'))";
        }

        @Override
        public String getTimestampToDateBeginningOfYearTemplate() {
            return "DATE(DATE_TRUNC('year', %s AT TIME ZONE '%s'))";
        }

        @Override
        public String getTimestampAsDateRangeTemplate() {
            return "DATE(%s AT TIME ZONE '%s') BETWEEN ? AND ?";
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
     * Template arguments: sequence name (please sanitize to avoid SQL injection!).
     *
     * @return template for getting the next value of a sequence
     */
    public abstract String getNextSequenceValueTemplate();

    /**
     * Template arguments: column name, time zone (e.g. '+05:30' or 'Europe/Paris') - please sanitize them to avoid SQL
     * injection!
     *
     * @return template for getting the date part of a timestamp (e.g., {@link LocalDate} for {@link Instant}) - may
     *         differ depending on the time zone, e.g. '2026-01-01 00:00:00' in UTC is '2025-12-31 23:00:00' in UTC-1
     */
    public abstract String getTimestampToDateTemplate();

    /**
     * Template arguments: column name, time zone (e.g. '+05:30' or 'Europe/Paris') - please sanitize them to avoid SQL
     * injection!
     *
     * @return template for getting the beginning of the week of a timestamp (the date part only - may differ depending
     *         on the time zone, e.g. '2026-01-01 00:00:00' in UTC is '2025-12-31 23:00:00' in UTC-1)
     */
    public abstract String getTimestampToDateBeginningOfWeekTemplate();

    /**
     * Template arguments: column name, time zone (e.g. '+05:30' or 'Europe/Paris') - please sanitize them to avoid SQL
     * injection!
     *
     * @return template for getting the beginning of the month of a timestamp (the date part only - may differ depending
     *         on the time zone, e.g. '2026-01-01 00:00:00' in UTC is '2025-12-31 23:00:00' in UTC-1)
     */
    public abstract String getTimestampToDateBeginningOfMonthTemplate();

    /**
     * Template arguments: column name, time zone (e.g. '+05:30' or 'Europe/Paris') - please sanitize them to avoid SQL
     * injection!
     *
     * @return template for getting the beginning of the quarter of a timestamp (the date part only - may differ
     *         depending on the time zone, e.g. '2026-01-01 00:00:00' in UTC is '2025-12-31 23:00:00' in UTC-1)
     */
    public abstract String getTimestampToDateBeginningOfQuarterTemplate();

    /**
     * Template arguments: column name, time zone (e.g. '+05:30' or 'Europe/Paris') - please sanitize them to avoid SQL
     * injection!
     *
     * @return template for getting the beginning of the year of a timestamp (the date part - may differ depending on
     *         the time zone, e.g. '2026-01-01 00:00:00' in UTC is '2025-12-31 23:00:00' in UTC-1)
     */
    public abstract String getTimestampToDateBeginningOfYearTemplate();

    /**
     * Template arguments: column name, time zone (e.g. '+05:30' or 'Europe/Paris') - please sanitize them to avoid SQL
     * injection! This is an extension of {@code timestamp_column BETWEEN ? AND ?} where the timestamp is first
     * converted to a date in the given time zone.
     *
     * @return template for getting a timestamp as a date range (the date part)
     */
    public abstract String getTimestampAsDateRangeTemplate();

}
