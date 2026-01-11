package guru.nicks.commons.cucumber;

import guru.nicks.commons.cucumber.domain.TestEntity;
import guru.nicks.commons.jpa.domain.FullTextSearchAwareEntity;
import guru.nicks.commons.utils.text.NgramUtils;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.Value;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class FullTextSearchAwareEntitySteps {

    private TestEntity entity;
    private TestSearchFilter searchFilter;

    private Pageable pageable;
    private Pageable resultPageable;

    private String previousFullTextSearchData;
    private String previousChecksum;
    private long operationStartTime;
    private long operationEndTime;

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

        resultPageable = FullTextSearchAwareEntity.initSortCriteria(searchFilter.getSearchText(), pageable);
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
        assertThat(entity.getFullTextSearchDataChecksum())
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

    // Helper method to call the private assignFullTextSearchData method using reflection
    @SneakyThrows
    private void callAssignFullTextSearchData(TestEntity entity) {
        Method method = FullTextSearchAwareEntity.class.getDeclaredMethod("rebuildFullTextSearchNgrams");
        method.setAccessible(true);
        method.invoke(entity);
    }

    /**
     * Test search filter.
     */
    @Value
    @Builder
    private static class TestSearchFilter {

        String searchText;

    }

}
