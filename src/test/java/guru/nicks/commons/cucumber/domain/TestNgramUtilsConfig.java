package guru.nicks.commons.cucumber.domain;

import guru.nicks.commons.utils.text.NgramUtilsConfig;

/**
 * Mutable {@link NgramUtilsConfig} whose every setting defaults to the interface default value - lets characterization
 * scenarios override single knobs while keeping the rest at production defaults. Fluent setters return
 * {@code this}, so scenarios can chain tweaks in one step.
 */
public class TestNgramUtilsConfig implements NgramUtilsConfig {

    private boolean reduceAccents = true;
    private boolean englishMorphAnalysis = true;
    private boolean russianMorphAnalysis = false;
    private int maxNgramCount = Short.MAX_VALUE;
    private int minNgramLength = 3;
    private int maxPrefixNgramLength = 6;
    private int maxInfixNgramLength = 3;

    @Override
    public boolean isReduceAccents() {
        return reduceAccents;
    }

    /**
     * @param reduceAccents whether to reduce accents, such as {@code ä → a}
     * @return this config, for chaining
     */
    public TestNgramUtilsConfig setReduceAccents(boolean reduceAccents) {
        this.reduceAccents = reduceAccents;
        return this;
    }

    @Override
    public boolean tryEnglishMorphAnalysis() {
        return englishMorphAnalysis;
    }

    /**
     * @param tryEnglishMorphAnalysis whether to add English lemma ngrams (and filter stop words)
     * @return this config, for chaining
     */
    public TestNgramUtilsConfig setTryEnglishMorphAnalysis(boolean tryEnglishMorphAnalysis) {
        this.englishMorphAnalysis = tryEnglishMorphAnalysis;
        return this;
    }

    @Override
    public boolean tryRussianMorphAnalysis() {
        return russianMorphAnalysis;
    }

    /**
     * @param tryRussianMorphAnalysis whether to add Russian lemma ngrams
     * @return this config, for chaining
     */
    public TestNgramUtilsConfig setTryRussianMorphAnalysis(boolean tryRussianMorphAnalysis) {
        this.russianMorphAnalysis = tryRussianMorphAnalysis;
        return this;
    }

    @Override
    public int getMaxNgramCount() {
        return maxNgramCount;
    }

    /**
     * @param maxNgramCount maximum number of ngrams per phase
     * @return this config, for chaining
     */
    public TestNgramUtilsConfig setMaxNgramCount(int maxNgramCount) {
        this.maxNgramCount = maxNgramCount;
        return this;
    }

    @Override
    public int getMinNgramLength() {
        return minNgramLength;
    }

    /**
     * @param minNgramLength minimum ngram length (both prefix and infix)
     * @return this config, for chaining
     */
    public TestNgramUtilsConfig setMinNgramLength(int minNgramLength) {
        this.minNgramLength = minNgramLength;
        return this;
    }

    @Override
    public int getMaxPrefixNgramLength() {
        return maxPrefixNgramLength;
    }

    /**
     * @param maxPrefixNgramLength maximum prefix ngram length
     * @return this config, for chaining
     */
    public TestNgramUtilsConfig setMaxPrefixNgramLength(int maxPrefixNgramLength) {
        this.maxPrefixNgramLength = maxPrefixNgramLength;
        return this;
    }

    @Override
    public int getMaxInfixNgramLength() {
        return maxInfixNgramLength;
    }

    /**
     * @param maxInfixNgramLength maximum infix ngram length
     * @return this config, for chaining
     */
    public TestNgramUtilsConfig setMaxInfixNgramLength(int maxInfixNgramLength) {
        this.maxInfixNgramLength = maxInfixNgramLength;
        return this;
    }
}
