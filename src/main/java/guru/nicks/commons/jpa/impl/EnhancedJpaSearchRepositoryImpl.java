package guru.nicks.commons.jpa.impl;

import guru.nicks.commons.jpa.domain.FullTextSearchAwareEntity;
import guru.nicks.commons.jpa.repository.EnhancedJpaRepository;
import guru.nicks.commons.jpa.repository.EnhancedJpaSearchRepository;
import guru.nicks.commons.utils.ReflectionUtils;
import guru.nicks.commons.utils.text.NgramUtils;
import guru.nicks.commons.utils.text.NgramUtilsConfig;

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
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Persistable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.Querydsl;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Base implementation for {@link EnhancedJpaSearchRepository}y. This class extends {@link EnhancedJpaRepositoryImpl}
 * and provides search-related functionality described in {@link EnhancedJpaSearchRepository}.
 *
 * @param <T>  entity type
 * @param <ID> primary key type
 * @param <E>  exception type to throw when entity is not found
 * @param <F>  search filter type
 */
@Transactional(readOnly = true) // borrowed from SimpleJpaRepository
@SuppressWarnings("java:S119")  // allow type names like 'ID'
@Slf4j
public class EnhancedJpaSearchRepositoryImpl<T extends Persistable<ID>,
        ID extends Serializable,
        E extends RuntimeException,
        F>
        extends EnhancedJpaRepositoryImpl<T, ID, E>
        implements EnhancedJpaSearchRepository<T, ID, E, F> {

    /**
     * Stores {@link FullTextSearchAwareEntity#getNgramUtilsConfig()} for {@code T}.
     * <p>
     * WARNING: this approach assumes that the config is the same for all instances of the given class.
     */
    private final Cache<Class<?>, NgramUtilsConfig> ngramUtilsConfigCache = Caffeine.newBuilder()
            .maximumSize(1)
            .build();

    private final ObjectMapper objectMapper;

    /**
     * Creates a new {@link EnhancedJpaSearchRepositoryImpl} for the given {@link JpaEntityInformation} and
     * {@link EntityManager}.
     *
     * @param entityInformation           must not be {@code null}
     * @param entityManager               must not be {@code null}
     * @param originalRepositoryInterface declared in the original repository via (after) {@code extends}
     * @param applicationContext          must not be {@code null}
     * @param objectMapper                must not be {@code null}
     * @throws IllegalArgumentException if {@code originalRepositoryInterface} is not a subclass of
     *                                  {@link EnhancedJpaSearchRepository}
     */
    public EnhancedJpaSearchRepositoryImpl(JpaEntityInformation<T, ID> entityInformation, EntityManager entityManager,
            Class<? extends EnhancedJpaRepository<T, ID, E>> originalRepositoryInterface,
            ApplicationContext applicationContext,
            ObjectMapper objectMapper) {
        super(entityInformation, entityManager, originalRepositoryInterface, applicationContext);

        if (!EnhancedJpaSearchRepository.class.isAssignableFrom(originalRepositoryInterface)) {
            throw new IllegalArgumentException("Original repository interface must be a subclass of "
                    + EnhancedJpaSearchRepository.class.getName());
        }

        this.objectMapper = objectMapper;
        log.debug("Wrapped {}", originalRepositoryInterface.getName());
    }

    /**
     * Delegates to the original repository where this method must be implemented. If there's no implementation, Spring
     * Data calls this method again, which results in an eternal loop and a {@link StackOverflowError}.
     */
    @Override
    public BooleanBuilder convertToSearchBuilder(F filter) {
        return getOriginalRepositoryProxy().convertToSearchBuilder(filter);
    }

    /**
     * Delegates to the original repository where this method must be implemented. If there's no implementation, Spring
     * Data calls this method again, which results in an eternal loop and a {@link StackOverflowError}.
     */
    @Override
    public Page<T> findByFilter(F filter, Pageable pageable) {
        return getOriginalRepositoryProxy().findByFilter(filter, pageable);
    }

    @Override
    public Page<T> findByFilter(F filter, Supplier<String> fullTextSearchSupplier, Pageable pageable,
            EntityPathBase<T> queryDslEntity, Supplier<EntityGraph<T>> entityGraphSupplier) {
        log.info("Finding [{}]: filter {} / pagination {}", getEntityClass().getName(), filter, pageable);

        var searchQuery = new JPAQuery<T>(getEntityManager())
                .select(queryDslEntity)
                .from(queryDslEntity);

        BooleanBuilder searchBuilder = convertToSearchBuilder(filter);
        Pageable oldPageable = pageable;
        pageable = setupFullTextSearch(fullTextSearchSupplier, pageable, queryDslEntity, searchBuilder, searchQuery);

        // by method contract, this means no FTS
        if (pageable == oldPageable) {
            searchQuery.where(searchBuilder);
            applyPaginationAndSort(searchQuery, pageable, queryDslEntity);
        }

        // no need to apply the entity graph because, being a set of LEFT JOINs, it doesn't affect the count
        var countQuery = new JPAQuery<>(getEntityManager())
                .select(queryDslEntity.count())
                .from(queryDslEntity)
                .where(searchBuilder);

        Optional.ofNullable(entityGraphSupplier.get())
                .ifPresent(graph -> searchQuery.setHint(EntityGraphType.FETCH.getKey(), graph));
        // this is how Spring Data applies pagination to queries (the query is already limited, see above)
        return PageableExecutionUtils.getPage(searchQuery.fetch(), pageable, countQuery::fetchOne);
    }

    @Override
    public Predicate createJsonContainsPredicate(String propertyName, Object value) {
        // validate property name to prevent SQL injection
        if (!SQL_COLUMN_NAME_PREDICATE.test(propertyName)) {
            throw new IllegalArgumentException("Invalid property name");
        }

        String fieldValueAsJson;
        // convert value to a JSON string, which also makes it SQL-secure
        try {
            fieldValueAsJson = objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON error: " + e.getMessage(), e);
        }

        // WARNING: don't pass '{0}' to booleanTemplate(), rather embed the value, or the query generated will have
        // invalid positional argument indexes (seems their bug)
        String sql = String.format(Locale.US, getSqlDialect().getJsonContainsTemplate(),
                propertyName, fieldValueAsJson);
        return Expressions.booleanTemplate(sql);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Class<? extends EnhancedJpaSearchRepository<T, ID, E, F>> getOriginalRepositoryInterface() {
        return (Class<? extends EnhancedJpaSearchRepository<T, ID, E, F>>) super.getOriginalRepositoryInterface();
    }

    @Override
    protected EnhancedJpaSearchRepository<T, ID, E, F> getOriginalRepositoryProxy() {
        return (EnhancedJpaSearchRepository<T, ID, E, F>) super.getOriginalRepositoryProxy();
    }

    /**
     * Adds pagination and sorting, if any, to the query.
     *
     * @param query          query
     * @param pageable       pagination/sorting request
     * @param queryDslEntity retrieved from QueryDSL as {@code QSomeEntity.someEntity}
     */
    private void applyPaginationAndSort(JPAQuery<?> query, Pageable pageable, EntityPath<T> queryDslEntity) {
        var tmpQueryDsl = new Querydsl(getEntityManager(), (new PathBuilderFactory()).create(queryDslEntity.getType()));
        tmpQueryDsl.applyPagination(pageable, query);
    }

    /**
     * If search text returned by the given supplier is blank, does nothing and returns. Otherwise:
     * <ul>
     *  <li>instantiates {@link #getEntityClass()} in order to retrieve
     *      {@link FullTextSearchAwareEntity#getNgramUtilsConfig()}</li>
     *  <li>adds search text ngrams (to match them against ngrams stored in DB) to {@code searchBuilder} as 'AND'</li>
     *  <li>if {@code pageable} specifies a field to sort by (but not
     *      {@value FullTextSearchAwareEntity#SEARCH_RANK_PSEUDOFIELD}), adds it to {@code query}</li>
     *  <li>otherwise, sets up sort by {@value FullTextSearchAwareEntity#SEARCH_RANK_PSEUDOFIELD} (desc) - adds it to
     *      {@code query} (offset and limit are borrowed from {@code pageable})</li>
     * </ul>
     *
     * @param fullTextSearchSupplier supplier for full-text search text, returns {@code null} if FTS is not needed
     * @param pageable               pagination/sorting request
     * @param queryDslEntity         retrieved from QueryDSL as {@code QSomeEntity.someEntity}
     * @param searchBuilder          already existing (possibly empty) QueryDSL predicate
     * @param query                  can have {@code SELECT} and {@code FROM} clauses only (no pagination or sorting)
     * @return {@code pageable} argument if no full-text search has been stored in {@code searchQuery}, or a new
     *         {@code pageable} otherwise (with sort criteria possibly altered as described above)
     * @throws IllegalArgumentException {@link #getEntityClass()} doesn't extend {@link FullTextSearchAwareEntity}
     */
    private Pageable setupFullTextSearch(Supplier<String> fullTextSearchSupplier, Pageable pageable,
            EntityPathBase<T> queryDslEntity, BooleanBuilder searchBuilder, JPAQuery<T> query) {
        String fts = fullTextSearchSupplier.get();
        // if no FTS was requested, don't check entity class (see below)
        if (StringUtils.isBlank(fts)) {
            return pageable;
        }

        // validate that entity supports full-text search
        if (!FullTextSearchAwareEntity.class.isAssignableFrom(getEntityClass())) {
            throw new IllegalArgumentException("Entity class [" + getEntityClass().getName()
                    + "] must extend [" + FullTextSearchAwareEntity.class.getName()
                    + "] to support full-text search");
        }

        Set<String> ngrams = NgramUtils.createNgrams(fts, NgramUtils.Mode.ALL, getNgramUtilsConfig());
        // special characters are stripped off, so there's no risk of SQL injection, but validate anyway
        if (ngrams.stream().anyMatch(ngram ->
                ngram.contains("'") || ngram.contains("\"") || ngram.contains("--") || ngram.contains(";"))) {
            throw new IllegalArgumentException("Invalid characters (SQL injection?) in search text");
        }

        String q = getSqlDialect().createLenientFullTextSearchCondition(ngrams);

        // WARNING: don't pass '{0}' to booleanTemplate(), rather embed the value, or the query generated will have
        // invalid positional argument indexes (seems their bug)
        String sql = String.format(Locale.US, getSqlDialect().getFullTextSearchTemplate(),
                FullTextSearchAwareEntity.FULL_TEXT_SEARCH_DATA_PROPERTY, q);
        searchBuilder.and(Expressions.booleanTemplate(sql));
        query.where(searchBuilder);

        return fixSortCriteria(pageable, queryDslEntity, query, q);
    }

    /**
     * Adjusts the sorting criteria for FTS. If the original {@code pageable} specifies a sort order other than by
     * {@value FullTextSearchAwareEntity#SEARCH_RANK_PSEUDOFIELD}, that order is applied. Otherwise, the query is sorted
     * by search rank in descending order.
     *
     * @param pageable       original pagination and sorting request
     * @param queryDslEntity QueryDSL entity path for the query.
     * @param query          query object to which the sorting and pagination will be applied
     * @param q              full-text search query string used to calculate the search rank
     * @return A new {@link Pageable} object reflecting the applied sort criteria. This will be a clone of the original
     *         if its sort was used, or a new instance with sorting by search rank.
     */
    private Pageable fixSortCriteria(Pageable pageable, EntityPathBase<T> queryDslEntity, JPAQuery<T> query, String q) {
        // caller intends to sort, but not by search rank - do it
        if (pageable.getSort().isSorted()
                && (pageable.getSort().getOrderFor(FullTextSearchAwareEntity.SEARCH_RANK_PSEUDOFIELD) == null)) {
            applyPaginationAndSort(query, pageable, queryDslEntity);

            // return a clone of the original pageable because FTS is in effect
            return pageable.isUnpaged()
                    ? Pageable.unpaged(pageable.getSort())
                    : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());
        }

        String sql = String.format(Locale.US, getSqlDialect().getFullTextSearchRankTemplate(),
                FullTextSearchAwareEntity.FULL_TEXT_SEARCH_DATA_PROPERTY, q);
        // WARNING: don't pass '{0}' to stringTemplate(), rather embed the value, or the query generated will have
        // invalid positional argument indexes (seems their bug)
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
     * NOTE: this approach assumes that the <b>ngram configuration is the same for all instances of the class</b>.
     *
     * @return the ngram configuration for the entity class
     */
    private NgramUtilsConfig getNgramUtilsConfig() {
        // 'get' method may return null as per Caffeine specs, but never does in this particular case
        return ngramUtilsConfigCache.get(getEntityClass(), clazz -> {
            var tmpEntity = (FullTextSearchAwareEntity<?>)
                    ReflectionUtils.instantiateEvenWithoutDefaultConstructor(clazz);
            return tmpEntity.getNgramUtilsConfig();
        });
    }

}
