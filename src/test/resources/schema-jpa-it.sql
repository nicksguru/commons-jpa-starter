-- H2 aliases emulating the unified DB functions referenced by the EnhancedSqlDialect templates (in production,
-- PostgreSQL provides them as native functions created per-database; the tests map them to static Java methods).
CREATE ALIAS IF NOT EXISTS FULL_TEXT_SEARCH FOR 'guru.nicks.commons.jpa.it.H2Functions.fullTextSearch';
CREATE ALIAS IF NOT EXISTS FULL_TEXT_SEARCH_RANK FOR 'guru.nicks.commons.jpa.it.H2Functions.fullTextSearchRank';
CREATE ALIAS IF NOT EXISTS JSON_CONTAINS FOR 'guru.nicks.commons.jpa.it.H2Functions.jsonContains';
