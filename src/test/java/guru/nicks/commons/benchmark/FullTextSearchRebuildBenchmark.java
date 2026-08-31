package guru.nicks.commons.benchmark;

import guru.nicks.commons.jpa.JpaInference;
import guru.nicks.commons.jpa.domain.FullTextSearchAwareEntity;
import guru.nicks.commons.utils.text.NgramUtilsConfig;

import ch.qos.logback.classic.Level;
import jakarta.annotation.Nonnull;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * JMH benchmark for the FTS n-gram rebuild pipeline of {@link FullTextSearchAwareEntity} (FTS_OPTIMIZATION_PLAN.md
 * §5.3). Scenarios:
 * <ul>
 *   <li>{@code unchangedContentUpdate} - entity whose FTS content was already built, then saved with identical supplier
 *       values: measures the checksum short-circuit path (the primary case the checksum exists for)</li>
 *   <li>{@code freshInsert100Words} - full rebuild of a ~100-word entity per invocation (lemma cache warms up during
 *       the first iterations, which is fine and realistic)</li>
 *   <li>{@code largeText1000Words} - full rebuild of a ~1000-word entity per invocation</li>
 *   <li>{@code maxNgramCountSaturation} - full rebuild under a tiny {@code maxNgramCount} cap the text saturates:
 *       measures the cap-enforcement cost</li>
 * </ul>
 * <p>
 * Word texts are generated deterministically from a seeded {@link Random} over a fixed vocabulary (stop words,
 * irregular words, accented words plus formulaic unique words). The default length cap applies: the benchmark entity
 * reports {@link JpaInference#DEFAULT_SQL_DIALECT} = Postgres, i.e. 1 MB − 1 = 1,048,575 characters, so the length
 * cap never fires for these texts - exactly the production default behavior.
 * <p>
 * Run with {@code mvn jmh:benchmark -Djmh.include=FullTextSearchRebuildBenchmark -Djmh.profiler=gc} (the
 * {@code gc.alloc.rate.norm} column is the key metric) - the class is not a JUnit test, so the Cucumber suite and
 * Surefire ignore it, exactly like commons-design-patterns' {@code PipelineBenchmark}.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
public class FullTextSearchRebuildBenchmark {

    /**
     * Word count of the small (fresh-insert) texts.
     */
    private static final int SMALL_TEXT_WORD_COUNT = 100;

    /**
     * Word count of the large texts.
     */
    private static final int LARGE_TEXT_WORD_COUNT = 1000;

    /**
     * Number of distinct text variants per scenario - rotating through them guarantees that consecutive invocations
     * never hit the stored checksum, i.e. every invocation takes the full rebuild path.
     */
    private static final int TEXT_VARIANT_COUNT = 8;

    /**
     * Base seed for the deterministic text generation (scenario-specific offsets are added to it).
     */
    private static final long TEXT_SEED = 20260101L;

    /**
     * Tiny ngram cap for the saturation scenario - a 100-word text yields far more ngrams than this.
     */
    private static final int SATURATING_MAX_NGRAM_COUNT = 64;

    /**
     * Number of formulaic words of each pattern added to the vocabulary, so that a 100-word text consists of mostly
     * distinct words (realistic ngram counts, ~7 ngrams per word).
     */
    private static final int FORMULAIC_WORD_COUNT = 256;

    /**
     * Fixed vocabulary mixing stop words (filtered by English morph analysis), irregular words (exercise the RiTa lemma
     * path: geese → goose, ran → run, ...), accented words (exercise accent-reduction copies) and regular vocabulary.
     */
    private static final String[] VOCABULARY = {
            "the", "a", "of", "was", "is", "it", "and", "to", "be", "been",
            "geese", "ran", "mice", "children", "feet", "teeth", "better", "went", "taken", "wrote",
            "café", "naïve", "piñata", "señor", "Ärger", "über",
            "server", "cluster", "deploy", "monitor", "index", "search", "vector", "query", "cache", "stream",
            "buffer", "token", "ngram", "checksum", "digest", "entity", "persist", "merge", "flush", "batch",
            "repository", "transaction", "optimistic", "locking", "pagination", "projection", "criteria", "predicate"
    };

    private BenchmarkFtsEntity unchangedEntity;
    private BenchmarkFtsEntity freshInsertEntity;
    private BenchmarkFtsEntity largeTextEntity;
    private BenchmarkFtsEntity saturationEntity;

    private String[] hundredWordVariants;
    private String[] thousandWordVariants;

    private int hundredWordCursor;
    private int thousandWordCursor;
    private int saturationCursor;

    /**
     * Creates the deterministic word pool the texts are drawn from: the fixed vocabulary plus formulaic unique words
     * ("widget0", "cluster0", ...) that keep a 100-word text mostly free of duplicates.
     *
     * @return word pool to draw words from
     */
    private static String[] createWordPool() {
        List<String> pool = new ArrayList<>(VOCABULARY.length + 2 * FORMULAIC_WORD_COUNT);
        pool.addAll(List.of(VOCABULARY));

        for (int i = 0; i < FORMULAIC_WORD_COUNT; i++) {
            pool.add("widget" + i);
            pool.add("cluster" + i);
        }

        return pool.toArray(new String[0]);
    }

    /**
     * Generates the deterministic text variants for one scenario.
     *
     * @param wordCount number of words per variant
     * @param seed      scenario-specific seed (variants differ, but are reproducible across runs)
     * @param wordPool  word pool to draw words from
     * @return {@value #TEXT_VARIANT_COUNT} space-joined text variants of {@code wordCount} words each
     */
    private static String[] generateTextVariants(int wordCount, long seed, String[] wordPool) {
        Random random = new Random(seed);
        String[] variants = new String[TEXT_VARIANT_COUNT];

        for (int variant = 0; variant < TEXT_VARIANT_COUNT; variant++) {
            StringBuilder sb = new StringBuilder(wordCount * 8);

            for (int word = 0; word < wordCount; word++) {
                if (word > 0) {
                    sb.append(' ');
                }

                sb.append(wordPool[random.nextInt(wordPool.length)]);
            }

            variants[variant] = sb.toString();
        }

        return variants;
    }

    @Setup
    public void setup() {
        // the entity logs INFO per changed-content rebuild - unsilenced, that would dominate both console output
        // and the measured timings
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(FullTextSearchAwareEntity.class))
                .setLevel(Level.WARN);

        String[] wordPool = createWordPool();
        hundredWordVariants = generateTextVariants(SMALL_TEXT_WORD_COUNT, TEXT_SEED, wordPool);
        thousandWordVariants = generateTextVariants(LARGE_TEXT_WORD_COUNT, TEXT_SEED + 1, wordPool);

        unchangedEntity = new BenchmarkFtsEntity("Quarterly release notes", hundredWordVariants[0],
                NgramUtilsConfig.DEFAULT);

        // prime checksum + search data so that benchmark invocations take the checksum short-circuit path
        unchangedEntity.rebuildFullTextSearchData();

        freshInsertEntity = new BenchmarkFtsEntity("Fresh insert", hundredWordVariants[0], NgramUtilsConfig.DEFAULT);
        largeTextEntity = new BenchmarkFtsEntity("Large document", thousandWordVariants[0], NgramUtilsConfig.DEFAULT);
        saturationEntity = new BenchmarkFtsEntity("Saturated config", hundredWordVariants[0],
                new SaturationNgramUtilsConfig());
    }

    /**
     * Primary case: entity saved (PreUpdate) with unchanged FTS-relevant content - the streaming checksum matches and
     * the n-gram rebuild is skipped entirely.
     *
     * @param bh blackhole consuming the (unchanged) checksum to prevent dead-code elimination
     */
    @Benchmark
    public void unchangedContentUpdate(Blackhole bh) {
        unchangedEntity.rebuildFullTextSearchData();
        bh.consume(unchangedEntity.getFullTextSearchDataChecksum());
    }

    /**
     * Fresh-insert cost for a typical ~100-word entity: every invocation rotates to a different text variant, so the
     * stored checksum never matches and the full rebuild path runs.
     *
     * @param bh blackhole consuming the rebuilt search data
     */
    @Benchmark
    public void freshInsert100Words(Blackhole bh) {
        freshInsertEntity.setText(hundredWordVariants[hundredWordCursor]);
        hundredWordCursor = (hundredWordCursor + 1) % TEXT_VARIANT_COUNT;

        freshInsertEntity.rebuildFullTextSearchData();
        bh.consume(freshInsertEntity.getFullTextSearchData());
    }

    /**
     * Full rebuild cost for a ~1000-word entity (10× the fresh-insert scenario).
     *
     * @param bh blackhole consuming the rebuilt search data
     */
    @Benchmark
    public void largeText1000Words(Blackhole bh) {
        largeTextEntity.setText(thousandWordVariants[thousandWordCursor]);
        thousandWordCursor = (thousandWordCursor + 1) % TEXT_VARIANT_COUNT;

        largeTextEntity.rebuildFullTextSearchData();
        bh.consume(largeTextEntity.getFullTextSearchData());
    }

    /**
     * Cap-enforcement cost: same ~100-word entity, but a tiny {@code maxNgramCount} (64) the text saturates - the
     * prefix-ngram phase is truncated to the cap and the infix phase is discarded entirely.
     *
     * @param bh blackhole consuming the rebuilt search data
     */
    @Benchmark
    public void maxNgramCountSaturation(Blackhole bh) {
        saturationEntity.setText(hundredWordVariants[saturationCursor]);
        saturationCursor = (saturationCursor + 1) % TEXT_VARIANT_COUNT;

        saturationEntity.rebuildFullTextSearchData();
        bh.consume(saturationEntity.getFullTextSearchData());
    }

    /**
     * Benchmark-local FTS entity double (existing cucumber doubles are deliberately not reused): a plain subclass with
     * a fixed title, a mutable text field and a fixed {@link NgramUtilsConfig}, modeled after the module's
     * {@code ChunkingTestEntity}/{@code ConfigurableTestEntity}. The supplier collection is pre-allocated once so that
     * the measured allocations come from the rebuild pipeline, not from per-call lambda/list creation.
     */
    private static class BenchmarkFtsEntity extends FullTextSearchAwareEntity<Long> {

        private final String title;
        private final NgramUtilsConfig ngramUtilsConfig;
        private final Collection<Supplier<String>> suppliers;

        // Postgres default: 1 MB - 1 = 1,048,575 characters (EnhancedSqlDialect.POSTGRES)
        private final int maxFullTextSearchDataLength =
                JpaInference.DEFAULT_SQL_DIALECT.getMaxFullTextSearchDataLength();

        private String text;
        private String fullTextSearchData;

        /**
         * Creates the entity with its immutable settings.
         *
         * @param title            fixed short field value, as a typical 'name'/'title' property
         * @param text             initial value of the mutable large text field
         * @param ngramUtilsConfig ngram configuration the rebuild must use
         */
        private BenchmarkFtsEntity(String title, String text, NgramUtilsConfig ngramUtilsConfig) {
            this.title = title;
            this.text = text;
            this.ngramUtilsConfig = ngramUtilsConfig;

            // 'this.' is load-bearing: bare 'title'/'text' would capture the constructor parameters, freezing
            // the suppliers at construction-time values and hiding later setText() calls from the rebuild
            this.suppliers = List.of(() -> this.title, () -> this.text);
        }

        @Override
        public Long getId() {
            return 1L;
        }

        @Override
        public String getFullTextSearchData() {
            return fullTextSearchData;
        }

        @Override
        public void setFullTextSearchData(String value) {
            fullTextSearchData = value;
        }

        @Override
        public int getMaxFullTextSearchDataLength() {
            return maxFullTextSearchDataLength;
        }

        @Nonnull
        @Override
        public NgramUtilsConfig getNgramUtilsConfig() {
            return ngramUtilsConfig;
        }

        @Nonnull
        @Override
        protected Collection<Supplier<String>> getFullTextSearchDataSuppliers() {
            return suppliers;
        }

        /**
         * Replaces the mutable text field value, simulating a content change before save.
         *
         * @param text new text field value
         */
        private void setText(String text) {
            this.text = text;
        }
    }

    /**
     * Benchmark-local ngram config double: every setting at its interface default except a tiny {@code maxNgramCount}
     * that the benchmark text saturates.
     */
    private static class SaturationNgramUtilsConfig implements NgramUtilsConfig {

        @Override
        public int getMaxNgramCount() {
            return SATURATING_MAX_NGRAM_COUNT;
        }
    }
}
