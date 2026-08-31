package guru.nicks.commons.cucumber;

import guru.nicks.commons.cucumber.domain.ConfigurableTestEntity;
import guru.nicks.commons.cucumber.domain.TestEntity;
import guru.nicks.commons.jpa.domain.FullTextSearchAwareEntity;
import guru.nicks.commons.utils.crypto.ChecksumUtils;
import guru.nicks.commons.utils.text.NgramUtils;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.Builder;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public class FullTextSearchAwareEntitySteps {

    /**
     * ~12 KB of mixed Cyrillic and ASCII, exercising multibyte UTF-8 encoding in the streaming checksum.
     */
    private static final String LARGE_TEXT = "слово word content ".repeat(600);

    private TestEntity entity;
    private TestSearchFilter searchFilter;

    private Pageable pageable;
    private Pageable resultPageable;

    private String previousFullTextSearchData;
    private String previousChecksum;

    // entity with a mutable supplier list, accepting null suppliers - the fixed TestEntity cannot express that
    private ConfigurableTestEntity configurableEntity;
    private AtomicReference<String> mutableSupplierValue;

    private long operationStartTime;
    private long operationEndTime;
    private SequencedSet<String> createdChunks;

    @Given("a search filter with text {string}")
    public void aSearchFilterWithText(String searchText) {
        var builder = TestSearchFilter.builder();

        if (!"null".equals(searchText)) {
            builder.searchText(searchText);
        }

        searchFilter = builder.build();
    }

    @Given("existing sort criteria by {string} in {string} direction")
    public void existingSortCriteriaBy(String field, String direction) {
        Sort.Direction sortDirection = Sort.Direction.valueOf(direction);
        pageable = PageRequest.of(0, 10, Sort.by(sortDirection, field));
    }

    @When("sort criteria are initialized with page {int} and size {int}")
    public void sortCriteriaAreInitializedWithPageAndSize(int pageNumber, int pageSize) {
        if (pageable == null) {
            pageable = PageRequest.of(pageNumber, pageSize);
        }

        resultPageable = FullTextSearchAwareEntity.initSortCriteria(searchFilter.searchText(), pageable);
    }

    @Then("the pageable should have page {int} and size {int}")
    public void thePageableShouldHavePageAndSize(int pageNumber, int pageSize) {
        assertThat(resultPageable.getPageNumber())
                .as("pageNumber")
                .isEqualTo(pageNumber);

        assertThat(resultPageable.getPageSize())
                .as("pageSize")
                .isEqualTo(pageSize);
    }

    @Then("the pageable should sort by {string} in {string} direction")
    public void thePageableShouldSortByInDirection(String field, String direction) {
        Sort.Direction expectedDirection = Sort.Direction.valueOf(direction);
        Sort.Order order = resultPageable.getSort().getOrderFor(field);

        assertThat(order)
                .as("Sort order for field %s should exist", field)
                .isNotNull();

        assertThat(order.getDirection())
                .as("Sort direction")
                .isEqualTo(expectedDirection);
    }

    @Given("a test entity with search data {string}")
    public void aTestEntityWithSearchData(String searchData) {
        entity = new TestEntity();

        if (!"null".equals(searchData)) {
            entity.setField1(searchData);
        }
    }

    @Given("a test entity with multiple search fields:")
    public void aTestEntityWithMultipleSearchFields(DataTable dataTable) {
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);
        Map<String, String> row = rows.getFirst();

        entity = new TestEntity();
        entity.setField1(row.get("field1"));
        entity.setField2(row.get("field2"));
        entity.setField3(row.get("field3"));
    }

    @Given("a test entity with large search data of size {int} characters")
    public void aTestEntityWithLargeSearchDataOfSizeCharacters(int size) {
        entity = new TestEntity();
        entity.setField1(RandomStringUtils.insecure().nextAlphanumeric(size));
    }

    @When("full-text search data is collected")
    public void fullTextSearchDataIsCollected() {
        previousFullTextSearchData = entity.getFullTextSearchData();
        previousChecksum = entity.getFullTextSearchDataChecksum();

        // measure performance
        operationStartTime = System.currentTimeMillis();
        callAssignFullTextSearchData(entity);
        operationEndTime = System.currentTimeMillis();
    }

    @When("the entity search data is changed to {string}")
    public void theEntitySearchDataIsChangedTo(String newContent) {
        entity.setField1(newContent);
    }

    @When("the entity field {string} is changed to {string}")
    public void theEntityFieldIsChangedTo(String fieldName, String newValue) throws Exception {
        Field field = TestEntity.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(entity, newValue);
    }

    @Then("the full-text search data should contain ngrams from {string}")
    public void theFullTextSearchDataShouldContainNgramsFrom(String searchData) {
        if ("null".equals(searchData)) {
            // If search data is null, the full-text search data should be empty or null
            assertThat(StringUtils.isBlank(entity.getFullTextSearchData()))
                    .as("Full-text search data should be empty for null search data")
                    .isTrue();
            return;
        }

        List<String> expectedNgrams = List.copyOf(NgramUtils.createNgrams(searchData,
                NgramUtils.Mode.ALL, entity.getNgramUtilsConfig()));
        String fullTextData = entity.getFullTextSearchData();
        // check if full-text search data contains the expected ngrams
        assertThat(fullTextData)
                .as("Full-text search data should not be null")
                .isNotNull();

        // check a sample of ngrams (checking all might be too much for large texts)
        int sampleSize = Math.min(20, expectedNgrams.size());

        for (int i = 0; i < sampleSize; i++) {
            String ngram = expectedNgrams.get(i);

            assertThat(fullTextData)
                    .as("Full-text search data should contain ngram: " + ngram)
                    .contains(ngram);
        }
    }

    @Then("the full-text search data length should not exceed the dialect maximum")
    public void theFullTextSearchDataLengthShouldNotExceedTheDialectMaximum() {
        String fullTextData = entity.getFullTextSearchData();

        if (fullTextData != null) {
            int maxLength = entity.getMaxFullTextSearchDataLength();

            assertThat(fullTextData.length())
                    .as("Full-text search data length should not exceed " + maxLength)
                    .isLessThanOrEqualTo(maxLength);
        }
    }

    @Then("the search data checksum should be calculated and stored")
    public void theSearchDataChecksumShouldBeCalculatedAndStored() {
        assertThat(entity.getFullTextSearchDataChecksum())
                .as("Search data checksum should not be blank")
                .isNotBlank();
    }

    @Then("the full-text search data should not be regenerated")
    public void theFullTextSearchDataShouldNotBeRegenerated() {
        assertThat(entity.getFullTextSearchData())
                .as("Full-text search data should remain unchanged")
                .isEqualTo(previousFullTextSearchData);
    }

    @Then("the search data checksum should remain unchanged")
    public void theSearchDataChecksumShouldRemainUnchanged() {
        assertThat(activeEntity().getFullTextSearchDataChecksum())
                .as("Search data checksum should remain unchanged")
                .isEqualTo(previousChecksum);
    }

    @Then("the full-text search data should be regenerated")
    public void theFullTextSearchDataShouldBeRegenerated() {
        // if the data was null before, it should now have content
        if (StringUtils.isEmpty(previousFullTextSearchData)) {
            assertThat(entity.getFullTextSearchData())
                    .as("Full-text search data should be generated")
                    .isNotNull();
        }
        // otherwise, it should be different from the original
        else {
            assertThat(entity.getFullTextSearchData())
                    .as("Full-text search data should be different after content change")
                    .isNotEqualTo(previousFullTextSearchData);
        }
    }

    @Then("the search data checksum should be updated")
    public void theSearchDataChecksumShouldBeUpdated() {
        // if original was null, new one should not be null
        if (StringUtils.isEmpty(previousChecksum)) {
            assertThat(entity.getFullTextSearchDataChecksum())
                    .as("New checksum should not be null")
                    .isNotNull();
        }
        // otherwise, it should be different from the original
        else {
            assertThat(entity.getFullTextSearchDataChecksum())
                    .as("Search data checksum should be updated after content change")
                    .isNotEqualTo(previousChecksum);
        }
    }

    @Then("the search data checksum should represent empty content")
    public void theSearchDataChecksumShouldRepresentEmptyContent() {
        assertThat(entity.getFullTextSearchDataChecksum())
                .as("Checksum for empty content should not be null")
                .isNotNull();

        assertThat(entity.getFullTextSearchDataChecksum())
                .as("Checksum for empty content should not be empty")
                .isNotEmpty();
    }

    @Then("the search data checksum should be calculated in less than {int} milliseconds")
    public void theSearchDataChecksumShouldBeCalculatedInLessThanMilliseconds(int maxTime) {
        long duration = operationEndTime - operationStartTime;

        assertThat(duration)
                .as("Checksum calculation took " + duration + "ms, which exceeds the limit of " + maxTime + "ms")
                .isLessThan(maxTime);
    }

    @Then("the full-text search data generation should be skipped")
    public void theFullTextSearchDataGenerationShouldBeSkipped() {
        assertThat(entity.getFullTextSearchData())
                .as("Full-text search data should remain unchanged when generation is skipped")
                .isEqualTo(previousFullTextSearchData);
    }

    @Then("the operation should complete in less than {int} milliseconds")
    public void theOperationShouldCompleteInLessThanMilliseconds(int maxTime) {
        long duration = operationEndTime - operationStartTime;

        assertThat(duration)
                .as("Operation took " + duration + "ms, which exceeds the limit of " + maxTime + "ms")
                .isLessThan(maxTime);
    }

    @When("full-text search chunks are created")
    public void fullTextSearchChunksAreCreated() {
        String text = entity.getField1();
        createdChunks = FullTextSearchAwareEntity.createFullTextSearchChunks(text, entity.getNgramUtilsConfig());
    }

    @Then("the chunks should be valid and contain {string} if present")
    public void theChunksShouldBeValidAndContainIfPresent(String expectedContent) {
        assertThat(createdChunks)
                .as("Chunks should not be null")
                .isNotNull();

        if (StringUtils.isNotBlank(expectedContent)) {
            String[] expectedWords = expectedContent.split(" ");

            for (String word : expectedWords) {
                boolean containsWord = createdChunks.contains(word)
                        || createdChunks.stream().anyMatch(chunk -> chunk.contains(word));

                assertThat(containsWord)
                        .as("Chunks '%s' should contain word or its ngrams: '%s'", createdChunks, word)
                        .isTrue();
            }
        }
    }

    @Then("no chunks should contain SQL injection characters")
    public void noChunksShouldContainSqlInjectionCharacters() {
        for (String chunk : createdChunks) {
            assertThat(chunk)
                    .as("Chunk should not contain single quote: " + chunk)
                    .doesNotContain("'");

            assertThat(chunk)
                    .as("Chunk should not contain double quote: " + chunk)
                    .doesNotContain("\"");

            assertThat(chunk)
                    .as("Chunk should not contain double dash: " + chunk)
                    .doesNotContain("--");

            assertThat(chunk)
                    .as("Chunk should not contain semicolon: " + chunk)
                    .doesNotContain(";");
        }
    }

    /**
     * Creates a configurable entity from a semicolon-separated list of supplier tokens: {@code none} yields an empty
     * suppliers collection, {@code null} a null supplier entry, {@code null-value} a supplier returning null,
     * {@code blank} an empty string, {@code space}/{@code tab}/{@code newline} whitespace-only values and
     * {@code large-text} the ~12 KB mixed-script text; anything else is the literal value.
     *
     * @param suppliersSpec semicolon-separated supplier tokens
     */
    @Given("a configurable test entity with search data suppliers {string}")
    public void aConfigurableTestEntityWithSearchDataSuppliers(String suppliersSpec) {
        configurableEntity = new ConfigurableTestEntity();

        // 'none' means an empty suppliers collection
        if (!"none".equals(suppliersSpec)) {
            for (String token : suppliersSpec.split(";")) {
                configurableEntity.addSupplier(tokenToSupplier(token));
            }
        }
    }

    /**
     * Creates a configurable entity whose single supplier reads from a mutable reference, so later steps can change the
     * supplied content without touching the supplier list.
     *
     * @param initialValue text the supplier returns initially
     */
    @Given("a configurable test entity with a mutable search data supplier initially returning {string}")
    public void aConfigurableTestEntityWithAMutableSearchDataSupplierInitiallyReturning(String initialValue) {
        configurableEntity = new ConfigurableTestEntity();
        mutableSupplierValue = new AtomicReference<>(initialValue);
        configurableEntity.addSupplier(mutableSupplierValue::get);
    }

    /**
     * Rebuilds the ngrams of the configurable entity directly ({@code rebuildFullTextSearchNgrams()} is public) while
     * remembering the previous data and checksum, so 'remain unchanged'-style steps can reference them.
     */
    @When("the configurable entity rebuilds its full-text search ngrams")
    public void theConfigurableEntityRebuildsItsFullTextSearchNgrams() {
        previousFullTextSearchData = configurableEntity.getFullTextSearchData();
        previousChecksum = configurableEntity.getFullTextSearchDataChecksum();

        configurableEntity.rebuildFullTextSearchData();
    }

    /**
     * Changes the text returned by the mutable supplier.
     *
     * @param newValue text the supplier returns from now on
     */
    @When("the mutable search data supplier is changed to return {string}")
    public void theMutableSearchDataSupplierIsChangedToReturn(String newValue) {
        mutableSupplierValue.set(newValue);
    }

    /**
     * Injects a sentinel into the search data field directly, bypassing the rebuild pipeline.
     *
     * @param sentinel value to inject manually
     */
    @When("the full-text search data is manually set to {string}")
    public void theFullTextSearchDataIsManuallySetTo(String sentinel) {
        configurableEntity.setFullTextSearchData(sentinel);
    }

    /**
     * Verifies that the rebuild produced non-blank search data.
     */
    @Then("the rebuilt full-text search data should not be blank")
    public void theRebuiltFullTextSearchDataShouldNotBeBlank() {
        assertThat(configurableEntity.getFullTextSearchData())
                .as("Rebuilt full-text search data")
                .isNotBlank();
    }

    /**
     * Verifies that the manually injected sentinel survived a rebuild, proving the ngram pipeline was skipped.
     *
     * @param sentinel value injected earlier via the manual setter
     */
    @Then("the full-text search data should remain {string}")
    public void theFullTextSearchDataShouldRemain(String sentinel) {
        assertThat(configurableEntity.getFullTextSearchData())
                .as("Full-text search data must survive the rebuild untouched because the pipeline was skipped")
                .isEqualTo(sentinel);
    }

    /**
     * Verifies that the search data differs from the one captured before the latest rebuild.
     */
    @Then("the full-text search data should be regenerated on the configurable entity")
    public void theFullTextSearchDataShouldBeRegeneratedOnTheConfigurableEntity() {
        assertThat(configurableEntity.getFullTextSearchData())
                .as("Full-text search data should be different after content change")
                .isNotEqualTo(previousFullTextSearchData);
    }

    /**
     * Verifies that the streaming checksum stored by the rebuild equals
     * {@link ChecksumUtils#computeJsonChecksum(Object)} of the joined supplier text.
     *
     * @param joinedText joined text the old non-streaming implementation would hash, or the {@code empty}/
     *                   {@code large-text} token
     */
    @Then("the search data checksum should equal the checksum of {string}")
    public void theSearchDataChecksumShouldEqualTheChecksumOf(String joinedText) {
        assertThat(configurableEntity.getFullTextSearchDataChecksum())
                .as("Streaming checksum must be byte-identical to computeJsonChecksum of the joined text")
                .isEqualTo(ChecksumUtils.computeJsonChecksum(expandTextToken(joinedText)));
    }

    /**
     * Verifies that the streaming checksum stored by the rebuild differs from
     * {@link ChecksumUtils#computeJsonChecksum(Object)} of the given text.
     *
     * @param joinedText text whose checksum must not match, or the {@code empty}/{@code large-text} token
     */
    @Then("the search data checksum should not equal the checksum of {string}")
    public void theSearchDataChecksumShouldNotEqualTheChecksumOf(String joinedText) {
        assertThat(configurableEntity.getFullTextSearchDataChecksum())
                .as("Streaming checksum must differ from computeJsonChecksum of '%s'", joinedText)
                .isNotEqualTo(ChecksumUtils.computeJsonChecksum(expandTextToken(joinedText)));
    }

    /**
     * Returns the entity the current scenario operates on: the configurable one when present, the plain test entity
     * otherwise (a scenario never uses both).
     *
     * @return entity active in the current scenario
     */
    private FullTextSearchAwareEntity<String> activeEntity() {
        return configurableEntity != null ? configurableEntity : entity;
    }

    /**
     * Maps a supplier token to a supplier.
     *
     * @param token token from the feature file
     * @return supplier for the token, may be {@code null} (a null supplier entry)
     */
    private Supplier<String> tokenToSupplier(String token) {
        return switch (token) {
            case "null" -> null;
            case "null-value" -> () -> null;
            case "blank" -> () -> "";
            case "space" -> () -> " ";
            case "tab" -> () -> "\t";
            case "newline" -> () -> "\n\r";
            case "large-text" -> () -> LARGE_TEXT;
            default -> () -> token;
        };
    }

    /**
     * Expands a joined-text token: {@code empty} yields an empty string and {@code large-text} the ~12 KB mixed-script
     * text; anything else is the literal text.
     *
     * @param token token from the feature file
     * @return expanded text
     */
    private String expandTextToken(String token) {
        return switch (token) {
            case "empty" -> "";
            case "large-text" -> LARGE_TEXT;
            default -> token;
        };
    }

    // invokes the public rebuildFullTextSearchNgrams method directly
    @SneakyThrows
    private void callAssignFullTextSearchData(TestEntity entity) {
        Method method = FullTextSearchAwareEntity.class.getDeclaredMethod("rebuildFullTextSearchData");
        method.setAccessible(true);
        method.invoke(entity);
    }

    /**
     * Test search filter.
     */
    @Builder
    private record TestSearchFilter(

            String searchText) {
    }

}
