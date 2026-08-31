package guru.nicks.commons.cucumber;

import guru.nicks.commons.cucumber.domain.ChunkingTestEntity;
import guru.nicks.commons.cucumber.domain.TestNgramUtilsConfig;
import guru.nicks.commons.jpa.domain.FullTextSearchAwareEntity;
import guru.nicks.commons.utils.text.NgramUtils;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Arrays;
import java.util.SequencedSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Steps for the golden-output characterization of {@link FullTextSearchAwareEntity#createFullTextSearchChunks} and
 * {@link NgramUtils#createNgrams}: every expected value in the feature file is a hard-coded output of the current
 * implementation, so any refactor of the chunk ordering/dedup/cap logic must reproduce it byte-for-byte. Scenarios are
 * pure static-method (or direct entity rebuild) calls and need no database.
 */
public class FullTextSearchChunksCharacterizationSteps {

    private TestNgramUtilsConfig config;
    private SequencedSet<String> chunks;
    private SequencedSet<String> ngrams;
    private ChunkingTestEntity entity;

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
     * Creates chunks via the static hot-path entry point under characterization.
     *
     * @param text input text
     */
    @When("full-text search chunks are created from {string}")
    public void fullTextSearchChunksAreCreatedFrom(String text) {
        chunks = FullTextSearchAwareEntity.createFullTextSearchChunks(text, requireConfig());
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
        entity.rebuildFullTextSearchData();
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
