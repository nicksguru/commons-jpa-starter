package guru.nicks.commons.cucumber;

import guru.nicks.commons.cucumber.domain.ChunkingTestEntity;
import guru.nicks.commons.cucumber.domain.TestNgramUtilsConfig;
import guru.nicks.commons.utils.text.FullTextSearchUtils;
import guru.nicks.commons.utils.text.NgramUtils;
import guru.nicks.commons.utils.text.NgramUtilsConfig;
import guru.nicks.commons.utils.text.TextUtils;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.SequencedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for the golden-output weights of {@link FullTextSearchUtils#createFtsChunks(String, NgramUtilsConfig)} and
 * {@link NgramUtils#createNgrams}: every expected value in the feature file is a hard-coded output of the current
 * implementation, so any refactor of the chunk ordering/dedup/cap logic must reproduce it byte-for-byte. Scenarios are
 * pure static-method (or direct entity rebuild) calls and need no database.
 * <p>
 * The weighted-tsvector scenarios characterize {@link NgramUtils#createWeightedNgrams} and the
 * {@link NgramUtilsConfig#isWeightedTsvector()} emission of {@link FullTextSearchUtils#buildFtsData}: annotated chunks
 * {@code 'chunk':positionWeight} with weight {@code A} for short words and prefix ngrams, {@code B} for infix ngrams.
 */
public class FullTextSearchChunksWeightsSteps {

    /**
     * Maximum lexeme position accepted by the Postgres tsvector input syntax - the emission wraps at it.
     */
    private static final int MAX_TSVECTOR_POSITION = 16383;

    /**
     * Annotation of a single lexeme in the weighted tsvector input format, e.g. {@code 'gur':1A}.
     */
    private static final Pattern WEIGHTED_CHUNK_PATTERN = Pattern.compile("'([^']+)':([0-9]+)([AB])");

    /**
     * Seed of the deterministic random word generator - fixed so the position-wrap scenario is reproducible.
     */
    private static final long GENERATED_WORDS_SEED = 42;

    private TestNgramUtilsConfig config;
    private SequencedSet<String> chunks;
    private SequencedSet<String> ngrams;
    private SequencedSet<NgramUtils.WeightedNgram> weightedNgrams;
    private ChunkingTestEntity entity;
    private String builtFtsData;

    /**
     * Starts every scenario from the production-default ngram settings, so each scenario tweaks only the knob it
     * characterizes.
     */
    @Given("default ngram config")
    public void defaultNgramConfig() {
        config = new TestNgramUtilsConfig();
    }

    /**
     * @param morph {@code on} or {@code off}, for {@link TestNgramUtilsConfig#setTryEnglishMorphAnalysis(boolean)}
     */
    @Given("ngram config with English morph analysis {word}")
    public void ngramConfigWithEnglishMorphAnalysis(String morph) {
        requireConfig().setTryEnglishMorphAnalysis(onOff(morph));
    }

    /**
     * @param reduce {@code on} or {@code off}, for {@link TestNgramUtilsConfig#setReduceAccents(boolean)}
     */
    @Given("ngram config with accent reduction {word}")
    public void ngramConfigWithAccentReduction(String reduce) {
        requireConfig().setReduceAccents(onOff(reduce));
    }

    /**
     * @param cap tiny maximum ngram count, for {@link TestNgramUtilsConfig#setMaxNgramCount(int)}
     */
    @Given("ngram config with max ngram count {int}")
    public void ngramConfigWithMaxNgramCount(int cap) {
        requireConfig().setMaxNgramCount(cap);
    }

    /**
     * @param weighted {@code on} or {@code off}, for {@link TestNgramUtilsConfig#setWeightedTsvector(boolean)}
     */
    @Given("ngram config with weighted tsvector {word}")
    public void ngramConfigWithWeightedTsvector(String weighted) {
        requireConfig().setWeightedTsvector(onOff(weighted));
    }

    /**
     * Creates chunks via the static hot-path entry point under characterization.
     *
     * @param text input text
     */
    @When("full-text search chunks are created from {string}")
    public void fullTextSearchChunksAreCreatedFrom(String text) {
        chunks = FullTextSearchUtils.createFtsChunks(text, requireConfig());
    }

    /**
     * Pins the exact chunk sequence (chunks never contain spaces, so a space-joined string is unambiguous).
     *
     * @param expected space-joined golden chunk sequence
     */
    @Then("the chunks are exactly {string}")
    public void theChunksAreExactly(String expected) {
        assertThat(chunks)
                .as("Exact chunk sequence of the current implementation")
                .containsExactlyElementsOf(Arrays.asList(expected.split(" ")));
    }

    /**
     * Verifies the word-level validation invariant: no chunk may carry SQL injection characters.
     */
    @Then("every chunk is free of SQL injection characters")
    public void everyChunkIsFreeOfSqlInjectionCharacters() {
        for (String chunk : chunks) {
            assertThat(chunk)
                    .as("Chunk must not contain SQL injection characters")
                    .doesNotContain("'", "\"", "--", ";");
        }
    }

    /**
     * Creates ngrams via {@link NgramUtils#createNgrams} directly, to pin the per-phase ordering.
     *
     * @param text     input text
     * @param modeName {@code ALL}, {@code PREFIX} or {@code INFIX}
     */
    @When("ngrams are created from {string} in mode {word}")
    public void ngramsAreCreatedFromInMode(String text, String modeName) {
        ngrams = NgramUtils.createNgrams(text, NgramUtils.Mode.valueOf(modeName), requireConfig());
    }

    /**
     * Pins the exact ngram sequence.
     *
     * @param expected space-joined golden ngram sequence
     */
    @Then("the ngrams are exactly {string}")
    public void theNgramsAreExactly(String expected) {
        assertThat(ngrams)
                .as("Exact ngram sequence of the current implementation")
                .containsExactlyElementsOf(Arrays.asList(expected.split(" ")));
    }

    /**
     * Creates weighted ngrams via {@link NgramUtils#createWeightedNgrams} directly, to pin the per-phase tiers.
     *
     * @param text input text
     */
    @When("weighted ngrams are created from {string}")
    public void weightedNgramsAreCreatedFrom(String text) {
        // same tokenization the entity rebuild performs internally
        var uniqueWords = TextUtils.collectUniqueWords(text, requireConfig().isReduceAccents());
        weightedNgrams = NgramUtils.createWeightedNgrams(uniqueWords, requireConfig());
    }

    /**
     * Pins the exact weighted ngram sequence, each ngram rendered as {@code ngram:A} or {@code ngram:B}.
     *
     * @param expected space-joined golden sequence of {@code ngram:weight} pairs
     */
    @Then("the weighted ngrams are exactly {string}")
    public void theWeightedNgramsAreExactly(String expected) {
        var rendered = weightedNgrams.stream()
                .map(weightedNgram -> weightedNgram.ngram() + ":" + (weightedNgram.highPriority() ? "A" : "B"))
                .toList();

        assertThat(rendered)
                .as("Exact weighted ngram sequence of the current implementation")
                .containsExactlyElementsOf(Arrays.asList(expected.split(" ")));
    }

    /**
     * Creates an entity whose rebuild is exercised with a tiny dialect length limit.
     *
     * @param searchData text the single supplier returns
     * @param maxLength  simulated {@code getMaxFullTextSearchDataLength()} limit
     */
    @Given("a chunking test entity with search data {string} and max full-text search data length {int}")
    public void aChunkingTestEntityWithSearchDataAndMaxFullTextSearchDataLength(String searchData, int maxLength) {
        entity = new ChunkingTestEntity();
        entity.addSupplier(() -> searchData);
        entity.setMaxFullTextSearchDataLength(maxLength);
        entity.setNgramUtilsConfig(requireConfig());
    }

    /**
     * Invokes the entity lifecycle callback directly - no database involved.
     */
    @When("the chunking entity rebuilds its full-text search ngrams")
    public void theChunkingEntityRebuildsItsFullTextSearchNgrams() {
        entity.rebuildFullTextSearchData(false);
    }

    /**
     * Pins the exact materialized search data string after the length-capped append.
     *
     * @param expected golden full-text search data string
     */
    @Then("the full-text search data of the chunking entity is exactly {string}")
    public void theFullTextSearchDataOfTheChunkingEntityIsExactly(String expected) {
        assertThat(entity.getFullTextSearchData())
                .as("Exact full-text search data of the current implementation")
                .isEqualTo(expected);
    }

    /**
     * Builds weighted full-text search data from a deterministic corpus of generated random words - enough distinct
     * ngrams to cross the tsvector position limit, exercising the wrap of the position counter.
     *
     * @param wordCount number of generated words
     */
    @When("weighted full-text search data is built from {int} generated words")
    public void weightedFullTextSearchDataIsBuiltFromGeneratedWords(int wordCount) {
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

        builtFtsData = FullTextSearchUtils.buildFtsData(source, requireConfig(), Integer.MAX_VALUE);
    }

    /**
     * Verifies the emitted positions are the expected 1-based sequence wrapped at the Postgres limit: strictly
     * incrementing by 1 and jumping back to 1 right after {@value #MAX_TSVECTOR_POSITION}, which also proves the corpus
     * produced more chunks than the limit itself.
     */
    @Then("the tsvector position annotations wrap at 16383")
    public void theTsvectorPositionAnnotationsWrap() {
        List<Integer> positions = new ArrayList<>();
        Matcher matcher = WEIGHTED_CHUNK_PATTERN.matcher(builtFtsData);

        while (matcher.find()) {
            positions.add(Integer.parseInt(matcher.group(2)));
        }

        assertThat(positions)
                .as("Positions of the weighted corpus - must exceed the wrap limit to make this scenario meaningful")
                .hasSizeGreaterThan(MAX_TSVECTOR_POSITION);

        for (int i = 1; i < positions.size(); i++) {
            assertThat(positions.get(i))
                    .as("Position %d", i)
                    .isEqualTo(positions.get(i - 1) % MAX_TSVECTOR_POSITION + 1);
        }
    }

    /**
     * @return config, initializing it lazily in case a scenario skips the 'default ngram config' step
     */
    private TestNgramUtilsConfig requireConfig() {
        if (config == null) {
            config = new TestNgramUtilsConfig();
        }

        return config;
    }

    /**
     * @param flag {@code on} or {@code off}
     * @return boolean value of the flag
     */
    private boolean onOff(String flag) {
        return switch (flag) {
            case "on" -> true;
            case "off" -> false;
            default -> throw new IllegalArgumentException("Expected 'on' or 'off', got: " + flag);
        };
    }
}
