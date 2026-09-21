package guru.nicks.commons.jpa.it;

import guru.nicks.commons.jpa.domain.EnhancedSqlDialect;
import guru.nicks.commons.test.TimescaleDbContainerProvider;
import guru.nicks.commons.utils.text.FullTextSearchUtils;
import guru.nicks.commons.utils.text.NgramUtilsConfig;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Postgres behavior the weighted tsvector format rests on, against a real Postgres (TimescaleDB-HA) container
 * - plain JDBC, no Spring context. Verifies that:
 * <ul>
 *     <li>the entity-built annotated string ({@code 'chunk':positionWeight}) survives the {@code CAST(? AS tsvector)}
 *         input parsing with weights and positions intact</li>
 *     <li>{@code ts_rank}'s default weight array ranks an A-match (prefix ngram) above a B-match (infix ngram)</li>
 *     <li>the lenient ' OR '-joined websearch query matches weighted data</li>
 *     <li>a corpus exceeding the 16383-position limit (the wrapped counter) is accepted</li>
 * </ul>
 * Requires Docker (same as the container-based tests of the other modules).
 */
class WeightedTsvectorPostgresTest {

    private static final NgramUtilsConfig WEIGHTED_CONFIG = new NgramUtilsConfig() {
        @Override
        public boolean isWeightedTsvector() {
            return true;
        }
    };

    /**
     * Seed of the deterministic random word generator - fixed so the wrap test corpus is reproducible.
     */
    private static final long GENERATED_WORDS_SEED = 42;

    private static JdbcDatabaseContainer<?> container;
    private static Connection connection;

    @BeforeAll
    static void startPostgres() throws SQLException {
        container = new TimescaleDbContainerProvider().newInstance();
        container.start();
        connection = container.createConnection("");

        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE weighted_doc (id text PRIMARY KEY, fts tsvector)");
        }
    }

    @AfterAll
    static void stopPostgres() throws SQLException {
        if (connection != null) {
            connection.close();
        }

        if (container != null) {
            container.stop();
        }
    }

    /**
     * Wipes the table before every test so that each one asserts over its own documents only - e.g. the 32k-lexeme
     * wrap corpus must not leak into the ranking assertions.
     */
    @BeforeEach
    void cleanupTable() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM weighted_doc");
        }
    }

    @Test
    void weightsAndPositionsSurviveTheTsvectorCast() throws SQLException {
        // 'geese' output is pinned by the cucumber characterization goldens - no lemma surprises
        insertWeightedDoc("doc-plain", "geese");

        // the stored format round-trips: lexemes keep their positions and weight letters
        assertThat(selectFtsText("doc-plain"))
                .contains("'gee':1A", "'geese':3A", "'goose':6A")
                .contains("'ees':7B", "'ose':10B");
    }

    @Test
    void prefixNgramMatchOutranksInfixNgramMatch() throws SQLException {
        // 'amp' is a prefix ngram of 'ampere' (weight A) and an infix ngram of 'lamp' (weight B)
        insertWeightedDoc("doc-ampere", "ampere thing");
        insertWeightedDoc("doc-lamp", "lamp thing");

        assertThat(tsRank("doc-ampere", "amp"))
                .as("ts_rank of the weight-A (prefix) match")
                .isGreaterThan(tsRank("doc-lamp", "amp"));

        // ordered by rank desc, the prefix-matching document comes first
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM weighted_doc WHERE fts @@ websearch_to_tsquery('simple', ?)"
                        + " ORDER BY ts_rank(fts, websearch_to_tsquery('simple', ?)) DESC")) {
            statement.setString(1, "amp");
            statement.setString(2, "amp");

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo("doc-ampere");
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo("doc-lamp");
            }
        }
    }

    @Test
    void lenientOrQueryMatchesWeightedData() throws SQLException {
        insertWeightedDoc("doc-lenient", "guru nick");

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT fts @@ websearch_to_tsquery('simple', ?) FROM weighted_doc WHERE id = 'doc-lenient'")) {
            statement.setString(1, "gur OR uru OR nothingmatching");

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getBoolean(1))
                        .as("lenient ' OR ' query matches the weighted tsvector")
                        .isTrue();
            }
        }
    }

    @Test
    void corpusExceedingThePositionLimitIsAccepted() throws SQLException {
        // random words yield far more than 16383 distinct ngrams, so the wrapped position counter is exercised -
        // an out-of-range position would make the tsvector cast fail
        String ftsData = buildWeightedFtsData(generatedWords(4000));
        assertThat(countAnnotatedChunks(ftsData)).isGreaterThan(16383);

        insertWeightedData("doc-wrap", ftsData);
        assertThat(selectFtsText("doc-wrap")).contains(":1A");
    }

    /**
     * @param id    document ID
     * @param words space-joined words whose weighted ngram data to store
     */
    private static void insertWeightedDoc(String id, String words) throws SQLException {
        insertWeightedData(id, buildWeightedFtsData(new StringBuilder(words)));
    }

    /**
     * @param id      document ID
     * @param ftsData entity-built weighted tsvector input string
     */
    private static void insertWeightedData(String id, String ftsData) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO weighted_doc (id, fts) VALUES (?, CAST(? AS tsvector)) ON CONFLICT (id)"
                        + " DO UPDATE SET fts = EXCLUDED.fts")) {
            statement.setString(1, id);
            statement.setString(2, ftsData);
            statement.executeUpdate();
        }
    }

    /**
     * @param id document ID
     * @return the stored tsvector rendered back to its text representation
     */
    private static String selectFtsText(String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT fts::text FROM weighted_doc WHERE id = ?")) {
            statement.setString(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    /**
     * @param id      document ID
     * @param tsquery websearch query body, e.g. a single chunk
     * @return ts_rank of the document for the query
     */
    private static double tsRank(String id, String tsquery) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT ts_rank(fts, websearch_to_tsquery('simple', ?)) FROM weighted_doc WHERE id = ?")) {
            statement.setString(1, tsquery);
            statement.setString(2, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getDouble(1);
            }
        }
    }

    /**
     * @param source joined words
     * @return the weighted tsvector input string the entity rebuild would produce
     */
    private static String buildWeightedFtsData(StringBuilder source) {
        return FullTextSearchUtils.buildFtsData(source, WEIGHTED_CONFIG,
                EnhancedSqlDialect.POSTGRES.getMaxFullTextSearchDataLength());
    }

    /**
     * @param wordCount number of random words to generate
     * @return builder holding the words joined with single spaces
     */
    private static StringBuilder generatedWords(int wordCount) {
        var random = new Random(GENERATED_WORDS_SEED);
        var source = new StringBuilder(wordCount * 12);

        for (int i = 0; i < wordCount; i++) {
            if (!source.isEmpty()) {
                source.append(' ');
            }

            random.ints('a', 'z' + 1)
                    .limit(8 + random.nextInt(5))
                    .forEach(source::appendCodePoint);
        }

        return source;
    }

    /**
     * @param ftsData weighted tsvector input string
     * @return number of annotated chunks in it
     */
    private static int countAnnotatedChunks(String ftsData) {
        int count = 0;

        for (int i = ftsData.indexOf("':"); i >= 0; i = ftsData.indexOf("':", i + 1)) {
            count++;
        }

        return count;
    }

}
