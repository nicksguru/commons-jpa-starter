-- Native PostgreSQL implementations of the unified DB functions referenced by the EnhancedSqlDialect templates (the
-- same functions production applications create per-database, see FullTextSearchAwareEntity's Javadoc; parameters
-- accept text because the test entities map fullTextSearchData/metadata as plain string columns).
--
-- The FTS functions work with both stored formats: the plain one (space-separated chunks) and the weighted tsvector one
-- ('chunk':positionWeight chunks produced when isWeightedTsvector() is on) - both are valid tsvector input.
--
-- NOTE: single-quoted bodies (not dollar-quoted) because Spring's SQL script parser is not dollar-quote-aware.
CREATE OR REPLACE FUNCTION FULL_TEXT_SEARCH(t text, q text) RETURNS int
    LANGUAGE sql
    STABLE AS
'SELECT CASE WHEN CAST(t AS tsvector) @@ websearch_to_tsquery(''simple'', q) THEN 1 ELSE 0 END';

CREATE OR REPLACE FUNCTION FULL_TEXT_SEARCH_RANK(t text, q text) RETURNS double precision
    LANGUAGE sql
    STABLE AS
'SELECT ts_rank(CAST(t AS tsvector), websearch_to_tsquery(''simple'', q))';

CREATE OR REPLACE FUNCTION JSON_CONTAINS(j text, v text) RETURNS int
    LANGUAGE sql
    STABLE AS
'SELECT CASE WHEN position(v in j) > 0 THEN 1 ELSE 0 END';
