package guru.nicks.jpa.repository;

import guru.nicks.ApplicationContextHolder;
import guru.nicks.cache.domain.CacheConstants;
import guru.nicks.jpa.domain.FullTextSearchAwareEntity;
import guru.nicks.utils.NgramUtils;
import guru.nicks.utils.NgramUtilsConfig;
import guru.nicks.utils.ReflectionUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.PathBuilderFactory;
import com.querydsl.core.types.dsl.StringTemplate;
import com.querydsl.jpa.impl.JPAQuery;
import jakarta.persistence.EntityGraph;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Persistable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.support.Querydsl;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.invoke.MethodHandles;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Search-related enhancements for JPA repositories. Subclasses must implement:
 * <ul>
 *     <li>{@link #convertToSearchPredicate(Object)}</li>
 *     <li>{@link #findByFilter(Object, Pageable)}</li>
 * </ul>
 *
 * @param <T>  entity type (if full-text search is required, must inherit from {@link FullTextSearchAwareEntity})
 * @param <ID> primary key type
 * @param <E>  exception type to throw when entity is not found - must have a constructor accepting a message
 * @param <F>  filter type (pass {@code Void} for no filter)
 */
@NoRepositoryBean
@SuppressWarnings("java:S119")  // allow type names like 'ID'
public interface EnhancedJpaSearchRepository<T extends Persistable<ID>, ID, E extends RuntimeException, F>
        extends EnhancedJpaRepository<T, ID, E> {

    /**
     * @see #getLog()
     */
    Class<?> STATIC_THIS = MethodHandles.lookup().lookupClass();

    java.util.function.Predicate<String> SQL_COLUMN_NAME_PREDICATE = Pattern
            .compile("^[a-zA-Z_][a-zA-Z0-9_]*$")
            .asMatchPredicate();

    /**
     * Stores {@link FullTextSearchAwareEntity#getNgramUtilsConfig()} for each subclass to avoid entity instantiation
     * each time the config is needed.
     * <p>
     * WARNING: this approach assumes that the config is the same for all instances of the given class.
     */
    Cache<Class<?>, NgramUtilsConfig> NGRAM_CONFIG_CACHE = Caffeine.newBuilder()
            .maximumSize(CacheConstants.DEFAULT_CAFFEINE_CACHE_CAPACITY)
            .build();

    /**
     * Converts search filter request to QueryDSL predicate. If there's no implementation, Spring Data tries to generate
     * some code for this method and fails with
     * {@code No property 'convertToSearchPredicate' found for type 'SomeEntity'}. Therefore, even if the filter type is
     * {@link Void}, an empty implementation is needed in subclasses.
     * <p>
     * NOTE: don't add full-text search query to the predicate created -  it's processed in a special way by
     * {@link #findByFilter(Object, Supplier, Pageable, EntityPathBase, Supplier)}.
     *
     * @param filter search filter
     * @return predicate ({@link Optional#empty()} if there's no search condition, including {@code null} filter)
     */
    BooleanBuilder convertToSearchPredicate(F filter);

    /**
     * Finds entities by filter. Subclasses must implement this with {@code @Transactional(readOnly = true)} and call
     * {@link #findByFilter(Object, Supplier, Pageable, EntityPathBase, Supplier)}. If there's no default
     * implementation, Spring Data tries to generate a query out of this method name and fails.
     *
     * @param filter   filter
     * @param pageable pagination/sorting request
     * @return page of entities found
     */
    @Transactional(readOnly = true)
    Page<T> findByFilter(F filter, Pageable pageable);

    /**
     * Finds entities by filter. Looks not for the original text but for its ngrams - this is what fuzzy search is. If
     * the original words are shorter than the minimum ngram length, they will NOT be found.
     * <p>
     * If {@code fulltextSearchText} contains a non-blank string, applies Hibernate-safe syntax to perform full-text
     * search on the {@value FullTextSearchAwareEntity#FULL_TEXT_SEARCH_DATA_PROPERTY} property.
     * {@code FULL_TEXT_SEARCH (fts_column, fts_query)} function returning 0/1 must be created in each DB, for
     * unification.
     * <p>
     * WARNING: don't use entity graphs to fetch collections with pagination - LEFT JOIN on collections breaks native
     * pagination. Hibernate issues a warning about having to paginate in memory, but it'd be better to fail.
     *
     * @param filter                 filter
     * @param fullTextSearchSupplier supplier for full-text search text, returns {@code null} if FTS is not needed
     * @param pageable               pagination/sorting request
     * @param entityGraphSupplier    entity graph to fetch associated entities, can return {@code null}
     * @param queryDslEntity         retrieved from QueryDSL as {@code QSomeEntity.someEntity}
     * @return page of entities found
     * @see #createEntityGraph()
     */
    @Transactional(readOnly = true)
    default Page<T> findByFilter(F filter, Supplier<String> fullTextSearchSupplier, Pageable pageable,
            EntityPathBase<T> queryDslEntity, Supplier<EntityGraph<T>> entityGraphSupplier) {
        getLog().info("Finding [{}]: filter {} / pagination {}", getEntityClass().getName(), filter, pageable);

        var searchQuery = new JPAQuery<T>(getEntityManagerBean())
                .select(queryDslEntity)
                .from(queryDslEntity);

        BooleanBuilder predicate = convertToSearchPredicate(filter);
        Pageable oldPageable = pageable;
        pageable = setupFullTextSearch(fullTextSearchSupplier, pageable, queryDslEntity, predicate, searchQuery);

        // no FTS
        if (pageable == oldPageable) {
            searchQuery.where(predicate);
            applyPaginationAndSort(searchQuery, pageable, queryDslEntity);
        }

        Optional.ofNullable(entityGraphSupplier.get())
                .ifPresent(graph -> searchQuery.setHint(EntityGraphType.FETCH.getKey(), graph));

        // no need to apply entity graph because it's always LEFT JOIN which doesn't affect the count
        var countQuery = new JPAQuery<>(getEntityManagerBean())
                .select(queryDslEntity.count())
                .from(queryDslEntity)
                .where(predicate);

        // this is how Spring Data applies pagination to queries (the query is already limited, see above)
        return PageableExecutionUtils.getPage(searchQuery.fetch(), pageable, countQuery::fetchOne);
    }

    /**
     * Adds search condition to query if search value is not {@code null}.
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
     * Adds search condition to query if search value is a not-blank string.
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

    /**
     * Adds pagination and sorting, if any, to the query.
     *
     * @param query          query
     * @param pageable       pagination/sorting request
     * @param queryDslEntity retrieved from QueryDSL as {@code QSomeEntity.someEntity}
     */
    default void applyPaginationAndSort(JPAQuery<?> query, Pageable pageable, EntityPath<T> queryDslEntity) {
        var tmpQueryDsl = new Querydsl(getEntityManagerBean(),
                (new PathBuilderFactory()).create(queryDslEntity.getType()));
        tmpQueryDsl.applyPagination(pageable, query);
    }

    /**
     * Returns logger bound to this class. There's no polymorphism anyway - repository classes are created on the fly as
     * proxies to {@link SimpleJpaRepository}.
     *
     * @return logger
     */
    default Logger getLog() {
        return LoggerFactory.getLogger(STATIC_THIS);
    }

    /**
     * Retrieves bean out of {@link ApplicationContextHolder}.
     *
     * @return bean
     * @throws NoSuchBeanDefinitionException {@link ObjectMapper} bean not found
     */
    default ObjectMapper getObjectMapperBean() {
        return ApplicationContextHolder
                .getApplicationContext()
                .getBean(ObjectMapper.class);
    }

    /**
     * Applies Hibernate-safe syntax to search inside a JSON column. {@code JSON_CONTAINS (json_column, json_value)}
     * function returning 0/1 must be created in each DB, for unification.
     *
     * @param propertyName property name (of type {@code jsonb} in Postgres); will be converted to column name
     * @param value        value to search for - scalar or object; will be JSON-quoted to prevent SQL injection attacks
     * @return boolean expression for QueryDSL
     * @throws NoSuchBeanDefinitionException {@link ObjectMapper} bean not found
     * @throws IllegalArgumentException      error encoding value as JSON
     */
    default Predicate createJsonContainsPredicate(String propertyName, Object value) {
        // validate property name to prevent SQL injection
        if (!SQL_COLUMN_NAME_PREDICATE.test(propertyName)) {
            throw new IllegalArgumentException("Invalid property name");
        }

        String fieldValueAsJson;
        // convert value to a JSON string, which also makes it SQL-secure
        try {
            fieldValueAsJson = getObjectMapperBean().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON error: " + e.getMessage(), e);
        }

        // WARNING: don't pass '{0}' to booleanTemplate(), rather embed the value, or the query generated will have
        // invalid positional argument indexes (seems their bug)
        String sql = String.format(Locale.US, EnhancedJpaRepository.getDialect().getJsonContainsTemplate(),
                propertyName, fieldValueAsJson);
        return Expressions.booleanTemplate(sql);
    }

    /**
     * If search text returned by the given supplier is blank, does nothing and returns. Otherwise:
     * <ul>
     *  <li>instantiates {@link #getEntityClass()} in order to retrieve
     *      {@link FullTextSearchAwareEntity#getNgramUtilsConfig()}</li>
     *  <li>adds search text ngrams (to match them against ngrams stored in DB) to {@code predicate} </li>
     *  <li>applies {@code predicate} to {@code searchQuery}</li>
     *  <li>if {@code pageable} specifies a field to sort by (but not
     *      {@value FullTextSearchAwareEntity#SEARCH_RANK_PSEUDOFIELD}), adds {@code pageable} to {@code query}</li>
     *  <li>otherwise, sets up sort by {@value FullTextSearchAwareEntity#SEARCH_RANK_PSEUDOFIELD} (desc) - adds it to
     *      {@code query} (offset and limit are borrowed from {@code pageable})</li>
     * </ul>
     *
     * @param fullTextSearchSupplier supplier for full-text search text, returns {@code null} if FTS is not needed
     * @param pageable               pagination/sorting request
     * @param queryDslEntity         retrieved from QueryDSL as {@code QSomeEntity.someEntity}
     * @param predicate              already existing (possibly empty) QueryDSL predicate
     * @param query                  can have {@code SELECT} and {@code FROM} clauses only (no pagination or sorting)
     * @return {@code pageable} argument if no full-text search has been stored in {@code searchQuery}, or a new
     *         {@code pageable} object otherwise (with sort criteria possibly altered as described above)
     * @throws IllegalArgumentException {@link #getEntityClass()} doesn't extend {@link FullTextSearchAwareEntity}
     */
    private Pageable setupFullTextSearch(Supplier<String> fullTextSearchSupplier, Pageable pageable,
            EntityPathBase<T> queryDslEntity, BooleanBuilder predicate, JPAQuery<T> query) {
        String text = Optional.ofNullable(fullTextSearchSupplier.get())
                .filter(StringUtils::isNotBlank)
                .orElse(null);
        if (text == null) {
            return pageable;
        }

        // validate that entity supports full-text search
        if (!FullTextSearchAwareEntity.class.isAssignableFrom(getEntityClass())) {
            throw new IllegalArgumentException("Entity class [" + getEntityClass().getName()
                    + "] must extend [" + FullTextSearchAwareEntity.class.getName()
                    + "] to support full-text search");
        }

        Set<String> ngrams = NgramUtils.createNgrams(text, NgramUtils.Mode.ALL, getNgramUtilsConfig());
        // special characters are stripped off, so there's no risk of SQL injection, but validate anyway
        if (ngrams.stream().anyMatch(ngram ->
                ngram.contains("'") || ngram.contains("\"") || ngram.contains("--") || ngram.contains(";"))) {
            throw new IllegalArgumentException("Invalid characters (SQL injection?) in search text");
        }

        String q = EnhancedJpaRepository.getDialect().createLenientFullTextSearchCondition(ngrams);

        // WARNING: don't pass '{0}' to booleanTemplate(), rather embed the value, or the query generated will have
        // invalid positional argument indexes (seems their bug)
        String sql = String.format(Locale.US, EnhancedJpaRepository.getDialect().getFullTextSearchTemplate(),
                FullTextSearchAwareEntity.FULL_TEXT_SEARCH_DATA_PROPERTY, q);
        predicate.and(Expressions.booleanTemplate(sql));
        query.where(predicate);

        return fixSortCriteria(pageable, queryDslEntity, query, q);
    }

    private Pageable fixSortCriteria(Pageable pageable, EntityPathBase<T> queryDslEntity, JPAQuery<T> query, String q) {
        // caller intends to sort, but not by search rank
        if (pageable.getSort().isSorted()
                && (pageable.getSort().getOrderFor(FullTextSearchAwareEntity.SEARCH_RANK_PSEUDOFIELD) == null)) {
            applyPaginationAndSort(query, pageable, queryDslEntity);

            // return a clone of the original pageable because FTS is in effect
            return pageable.isUnpaged()
                    ? Pageable.unpaged(pageable.getSort())
                    : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());
        }

        // WARNING: don't pass '{0}' to stringTemplate(), rather embed the value, or the query generated will have
        // invalid positional argument indexes (seems their bug)
        String sql = String.format(Locale.US, EnhancedJpaRepository.getDialect().getFullTextSearchRankTemplate(),
                FullTextSearchAwareEntity.FULL_TEXT_SEARCH_DATA_PROPERTY, q);
        StringTemplate sortBySearchRank = Expressions.stringTemplate(sql);

        // sort by search rank (always desc) which is an SQL function
        query.offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(new OrderSpecifier<>(Order.DESC, sortBySearchRank));

        // for caller, search rank looks like a special property
        Sort newSort = Sort.by(
                Sort.Order.desc(FullTextSearchAwareEntity.SEARCH_RANK_PSEUDOFIELD));
        return pageable.isUnpaged()
                ? Pageable.unpaged(newSort)
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), newSort);
    }

    /**
     * Retrieves ngram configuration for {@link #getEntityClass()}. The configuration is cached to avoid repeated entity
     * instantiation. If not found in cache, instantiates the entity class (even without a default constructor) and
     * retrieves its {@link FullTextSearchAwareEntity#getNgramUtilsConfig()}.
     * <p>
     * WARNING: this approach assumes that the ngram configuration is the same for all instances of the given class.
     *
     * @return the ngram configuration for the entity class
     */
    private NgramUtilsConfig getNgramUtilsConfig() {
        return NGRAM_CONFIG_CACHE.get(getEntityClass(), clazz -> {
            var tmpEntity = (FullTextSearchAwareEntity) ReflectionUtils.instantiateEvenWithoutDefaultConstructor(clazz);
            return tmpEntity.getNgramUtilsConfig();
        });
    }

}
