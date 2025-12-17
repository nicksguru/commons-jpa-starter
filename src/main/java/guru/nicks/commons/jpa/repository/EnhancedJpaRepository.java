package guru.nicks.commons.jpa.repository;

import guru.nicks.commons.jpa.domain.EnhancedSqlDialect;
import guru.nicks.commons.jpa.domain.JpaConstants;
import guru.nicks.commons.jpa.impl.EnhancedJpaRepositoryImpl;

import jakarta.persistence.EntityGraph;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * A combination of common JPA-related repository interfaces augmented with some custom functionality. Used implicitly
 * via {@link EnhancedJpaRepositoryFactoryBean}.
 * <p>
 * NOTE: Spring Data wraps all non-static repository methods in a decorator that rejects null arguments.
 *
 * @param <T>  entity type
 * @param <ID> primary key type
 * @param <E>  exception type to throw when entity is not found
 */
@NoRepositoryBean
@SuppressWarnings("java:S119")  // allow type names like 'ID'
public interface EnhancedJpaRepository<T extends Persistable<ID>,
        ID extends Serializable,
        E extends RuntimeException>
        extends JpaRepository<T, ID>, QuerydslPredicateExecutor<T> {

    /**
     * @see #getSqlDialect()
     */
    String SQL_DIALECT_PROPERTY_NAME = "app.database.dialect";

    /**
     * @see #getSqlDialect()
     */
    EnhancedSqlDialect DEFAULT_SQL_DIALECT = EnhancedSqlDialect.POSTGRES;

    /**
     * A predicate that validates if a given string is a valid SQL column name. It allows names starting with a letter
     * or underscore, followed by letters, digits, or underscores. This is useful for preventing SQL injection in
     * dynamically constructed query parts.
     */
    Predicate<String> SQL_COLUMN_NAME_PREDICATE = Pattern
            .compile("^[a-zA-Z_][a-zA-Z0-9_]*$")
            .asMatchPredicate();

    /**
     * Returns the SQL dialect configured for this repository. This method is implemented in
     * {@link EnhancedJpaRepositoryImpl} which has access to the injected {@link Environment} and falls back on
     * {@link #DEFAULT_SQL_DIALECT}.
     *
     * @return SQL dialect
     */
    EnhancedSqlDialect getSqlDialect();

    /**
     * Implemented in {@link EnhancedJpaRepositoryImpl}.
     *
     * @return class of type {@code T}
     * @throws IllegalStateException if the entity class is not found in the generic type parameters
     */
    Class<T> getEntityClass();

    /**
     * Implemented in {@link EnhancedJpaRepositoryImpl}.
     *
     * @return class of type {@code E}
     * @throws IllegalStateException if the exception class is not found in the generic type parameters
     */
    Class<E> getExceptionClass();

    /**
     * Creates an entity graph for {@link #getEntityClass()}. Implemented in {@link EnhancedJpaRepositoryImpl}.
     *
     * @return entity graph
     * @see #findByIdWithFetchGraph(Serializable, EntityGraph) (Object, EntityGraph)
     */
    EntityGraph<T> createEntityGraph();

    /**
     * Finds an entity by its ID using the specified entity graph for fetch optimization. Implemented in
     * {@link EnhancedJpaRepositoryImpl}.
     * <p>
     * This method allows for fine-grained control over which entity attributes and associations are eagerly loaded by
     * providing a custom {@link EntityGraph}. The entity graph is used as a FETCH hint to optimize the query
     * performance and reduce, thanks to LEFT JOINs, the number of database round-trips.
     *
     * @param id    primary key
     * @param graph entity graph defining which attributes and associations to fetch eagerly; must not be {@code null}
     * @return optional entity
     */
    Optional<T> findByIdWithFetchGraph(ID id, EntityGraph<T> graph);

    /**
     * Does the same as {@link #findById(Object)} but throws an exception if entity is not found. Implemented in
     * {@link EnhancedJpaRepositoryImpl}.
     *
     * @param id entity ID
     * @return entity
     * @throws E                          entity not found
     * @throws BeanInstantiationException {@code E}'s argument-less constructor failed
     */
    T getByIdOrThrow(ID id);

    /**
     * Unlike {@link #findAllById(Iterable)}, returns elements in the same order as their IDs are returned by the input
     * collection (which may or may not be ordered). Implemented in {@link EnhancedJpaRepositoryImpl}.
     *
     * @param ids IDs
     * @return elements in the same order as in {@code ids}, mutable list - crucial for Hibernate if this list is
     *         assigned to another entity; if it's immutable, Hibernate can't save it because it tries to clear it
     */
    List<T> findAllByIdPreserveOrder(Collection<ID> ids);

    /**
     * Saves a collection of entities in batches of {@link JpaConstants#INTERNAL_PAGE_SIZE}, flushing and clearing the
     * persistence context after each batch. This is more memory-efficient for bulk operations than
     * {@link #saveAll(Iterable)}. Implemented in {@link EnhancedJpaRepositoryImpl}.
     *
     * @param entities entities to save, must not be {@code null}
     * @return saved entities
     */
    List<T> saveAllAndFlushInBatches(Collection<T> entities);

    /**
     * Saves a collection of entities in batches, flushing and clearing the persistence context after each batch. This
     * is more memory-efficient for bulk operations than {@link #saveAll(Iterable)}. Implemented in
     * {@link EnhancedJpaRepositoryImpl}.
     *
     * @param entities  entities to save, must not be {@code null}
     * @param batchSize size of each batch
     * @return saved entities
     */
    List<T> saveAllAndFlushInBatches(Collection<T> entities, int batchSize);

}
