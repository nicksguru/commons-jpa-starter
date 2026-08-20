package guru.nicks.commons.jpa.it.domain;

/**
 * Search filter for {@link TestDocument}: exercises plain predicates, the JSON-contains predicate and the full-text
 * search path of {@code EnhancedJpaSearchRepositoryFragment}.
 *
 * @param name substring to match the document name against, can be {@code null}/blank
 * @param userId exact user ID to match, can be {@code null}
 * @param color value to search for inside the JSON metadata column, can be {@code null}/blank
 * @param searchText full-text (ngram fuzzy) search text, can be {@code null}/blank
 */
public record TestDocumentFilter(
        String name,
        String userId,
        String color,
        String searchText) {
}
