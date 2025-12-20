package guru.nicks.commons.jpa.repository;

import guru.nicks.commons.jpa.domain.FullTextSearchAwareEntity;
import guru.nicks.commons.jpa.impl.EnhancedJpaSearchRepositoryImpl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.EntityPathBase;
import jakarta.persistence.EntityGraph;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Persistable;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Search-related enhancements for JPA repositories. Used implicitly via {@link EnhancedJpaRepositoryFactoryBean}.
 * Repositories must implement the following methods (failure to do so will result in an exception during
 * initialization):
 * <ul>
 *     <li>{@link #convertToSearchBuilder(Object)}</li>
 *     <li>{@link #findByFilter(Object, Pageable)}</li>
 * </ul>
 *
 * @param <T>  entity type (if full-text search is required, must inherit from {@link FullTextSearchAwareEntity})
 * @param <ID> primary key type
 * @param <E>  exception type to throw when entity is not found
 * @param <F>  search filter type (pass {@code Void} for no filter)
 */
@NoRepositoryBean
@SuppressWarnings("java:S119")  // allow type names like 'ID'
public interface EnhancedJpaSearchRepository<T extends Persistable<ID>,
        ID extends Serializable,
        E extends RuntimeException,
        F>
        extends EnhancedJpaRepository<T, ID, E> {

    Set<String> METHODS_TO_IMPLEMENT = Set.of("convertToSearchBuilder", "findByFilter");

    /**
     * Converts search filter request to QueryDSL predicate builder. <b>Repositories must implement this method.</b>
     * <p>
     * WARNING: don't add full-text search query to the predicate created -  it's processed in a special way by
     * {@link #findByFilter(Object, Supplier, Pageable, EntityPathBase, Supplier)}.
     *
     * @param filter search filter
     * @return predicate ({@link Optional#empty()} if there's no search condition or the filter is {@code null})
     */
    BooleanBuilder convertToSearchBuilder(F filter);

    /**
     * Finds entities by filter. <b>Repositories must implement this method</b> with
     * {@code @Transactional(readOnly = true)} and call
     * {@link #findByFilter(Object, Supplier, Pageable, EntityPathBase, Supplier)} with appropriate arguments.
     *
     * @param filter   filter
     * @param pageable pagination/sorting request
     * @return page of entities found
     */
    Page<T> findByFilter(F filter, Pageable pageable);

    /**
     * Finds entities by filter (<b>method implemented in {@link EnhancedJpaSearchRepositoryImpl}</b>). Looks not for
     * the original text but for its ngrams - this is what fuzzy search is. If the original words are shorter than the
     * minimum ngram length, they will NOT be found.
     * <p>
     * If {@code fulltextSearchText} contains a non-blank string, applies Hibernate-safe syntax to perform full-text
     * search on the {@value FullTextSearchAwareEntity#FULL_TEXT_SEARCH_DATA_PROPERTY} property.
     * {@code FULL_TEXT_SEARCH (fts_column, fts_query)} function returning 0/1 must be created in each DB, for
     * unification.
     * <p>
     * WARNING: don't use entity graphs to fetch collections with pagination - LEFT JOINs on collections break native
     * pagination. Hibernate issues a warning about having to paginate in memory, but it'd be better to fail. Instead,
     * consider fetching associated collections with a single SQL query after a page of parent entities has been found -
     * just pass all the entity IDs to that query.
     *
     * @param filter                 filter
     * @param fullTextSearchSupplier supplier for full-text search text, returns {@code null} or a blank/empty string if
     *                               FTS is not needed
     * @param pageable               pagination/sorting request, at least {@link Pageable#unpaged()}
     * @param entityGraphSupplier    entity graph to fetch associated entities, can return {@code null}
     * @param queryDslEntity         retrieved from QueryDSL as {@code QSomeEntity.someEntity}
     * @return page of entities found
     * @see #createEntityGraph()
     */
    Page<T> findByFilter(F filter, Supplier<String> fullTextSearchSupplier, Pageable pageable,
            EntityPathBase<T> queryDslEntity, Supplier<EntityGraph<T>> entityGraphSupplier);

    /**
     * Applies Hibernate-safe syntax to search inside a JSON column (<b>method implemented in
     * {@link EnhancedJpaSearchRepositoryImpl}</b>). {@code JSON_CONTAINS (json_column, json_value)} function returning
     * 0/1 must be created in each DB, for unification.
     *
     * @param propertyName property name (of type {@code jsonb} in Postgres); will be converted to column name
     * @param value        value to search for - scalar or object; will be JSON-quoted to prevent SQL injection attacks
     * @return boolean expression for QueryDSL
     * @throws IllegalArgumentException error encoding value as JSON
     */
    Predicate createJsonContainsPredicate(String propertyName, Object value);

    /**
     * Adds a search condition to query if the search value is not {@code null}.
     *
     * @param valueSupplier can't pass the value itself because Spring Data wraps this method in a special invocation
     *                      handler which rejects nulls
     * @param target        QueryDSL builder to add the condition to
     * @param condition     search condition to add
     * @param <V>           value type
     */
    default <V> void andIfNotNull(Supplier<V> valueSupplier, BooleanBuilder target, Function<V, Predicate> condition) {
        Optional.ofNullable(valueSupplier.get())
                .map(condition)
                .ifPresent(target::and);
    }

    /**
     * Adds a search condition to query if the search value is not blank.
     *
     * @param valueSupplier can't pass the value itself because Spring Data wraps this method in a special invocation
     *                      handler which rejects nulls
     * @param target        QueryDSL builder to add the condition to
     * @param condition     search condition to add
     */
    default void andIfNotBlank(Supplier<String> valueSupplier, BooleanBuilder target,
            Function<String, Predicate> condition) {
        Optional.ofNullable(valueSupplier.get())
                .filter(StringUtils::isNotBlank)
                .map(condition)
                .ifPresent(target::and);
    }

}
