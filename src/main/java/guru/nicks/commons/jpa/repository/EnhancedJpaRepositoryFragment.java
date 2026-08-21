package guru.nicks.commons.jpa.repository;

import guru.nicks.commons.jpa.JpaInference;
import guru.nicks.commons.jpa.domain.EnhancedSqlDialect;
import guru.nicks.commons.jpa.domain.JpaConstants;
import guru.nicks.commons.jpa.impl.EnhancedJpaRepositoryFragmentImpl;
import guru.nicks.commons.utils.ExceptionUtils;

import jakarta.persistence.EntityGraph;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.core.RepositoryMetadata;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Custom functionality contributed to {@link EnhancedJpaRepository} as a repository fragment. Implemented in
 * {@link EnhancedJpaRepositoryFragmentImpl} which is attached to repository proxies via
 * {@link EnhancedJpaRepositoryFactory#getRepositoryFragments(RepositoryMetadata)}.
 * <p>
 * NOTE: deliberately extends nothing - only then does Spring Data treat this interface as a fragment and not as a
 * repository. Fragment discovery through {@link EnhancedJpaRepository} is transitive, so user repositories extending
 * the aggregate get this fragment automatically.
 * <p>
 * NOTE: Spring Data wraps all non-static repository methods in a decorator that rejects null arguments.
 *
 * @param <T>  entity type
 * @param <ID> primary key type
 * @param <E>  exception type to throw when entity is not found
 */
@NoRepositoryBean
@SuppressWarnings("java:S119")  // allow type names like 'ID'
public interface EnhancedJpaRepositoryFragment<T extends Persistable<ID>,
        ID extends Serializable,
        E extends RuntimeException> {

    /**
     * A predicate that validates if a given string is a valid SQL column name. It allows names starting with a letter
     * or underscore, followed by letters, digits, or underscores. This is useful for preventing SQL injection in
     * dynamically constructed query parts.
     */
    Predicate<String> SQL_COLUMN_NAME_PREDICATE = Pattern
            .compile("^[a-zA-Z_][a-zA-Z0-9_]*$")
            .asMatchPredicate();

    /**
     * This method is implemented in {@link EnhancedJpaRepositoryFragmentImpl} which delegates to
     * {@link JpaInference#getSqlDialect()}.
     *
     * @return SQL dialect
     */
    EnhancedSqlDialect getSqlDialect();

    /**
     * Implemented in {@link EnhancedJpaRepositoryFragmentImpl}.
     *
     * @return class of type {@code T}
     * @throws IllegalStateException if the entity class is not found in the generic type parameters
     */
    Class<T> getEntityClass();

    /**
     * Implemented in {@link EnhancedJpaRepositoryFragmentImpl}. For instantiating the exception class, refer to
     * {@link ExceptionUtils#getExceptionFactory(Class)}.
     *
     * @return class of type {@code E}
     * @throws IllegalStateException if the exception class is not found in the generic type parameters
     */
    Class<E> getExceptionClass();

    /**
     * Creates an entity graph for {@link #getEntityClass()}. Implemented in {@link EnhancedJpaRepositoryFragmentImpl}.
     *
     * @return entity graph
     * @see #findByIdWithFetchGraph(Serializable, EntityGraph)
     */
    EntityGraph<T> createEntityGraph();

    /**
     * Finds an entity by its ID using the specified entity graph for fetch optimization. Implemented in
     * {@link EnhancedJpaRepositoryFragmentImpl}.
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
     * Does the same as {@link JpaRepository#findById(Object)} but throws an exception if entity is not found.
     * Implemented in {@link EnhancedJpaRepositoryFragmentImpl}.
     * <p>
     * NOTE: declared here (and not inherited from {@code CrudRepository}) deliberately, so that the fragment
     * implementation wins method routing over the deprecated
     * {@code SimpleJpaRepository#getById(Serializable)}.
     *
     * @param id entity ID
     * @return entity
     * @throws E                          entity not found
     * @throws BeanInstantiationException {@code E}'s argument-less constructor failed
     */
    T getById(ID id);

    /**
     * Unlike {@link JpaRepository#findAllById(Iterable)}, returns elements in the same order as their IDs are returned
     * by the input collection (which may or may not be ordered). Implemented in
     * {@link EnhancedJpaRepositoryFragmentImpl}.
     *
     * @param ids IDs
     * @return elements in the same order as in {@code ids}, mutable list - crucial for Hibernate if this list is
     *         assigned to another entity; if it's immutable, Hibernate can't save it because it tries to clear it
     */
    List<T> findAllByIdPreserveOrder(Collection<ID> ids);

    /**
     * Saves a collection of entities in batches of {@link JpaConstants#INTERNAL_PAGE_SIZE}, flushing and clearing the
     * persistence context after each batch. This is more memory-efficient for bulk operations than
     * {@link JpaRepository#saveAll(Iterable)}. Implemented in {@link EnhancedJpaRepositoryFragmentImpl}.
     *
     * @param entities entities to save, must not be {@code null}
     * @return saved entities
     */
    List<T> saveAllAndFlushInBatches(Collection<T> entities);

    /**
     * Saves a collection of entities in batches, flushing and clearing the persistence context after each batch. This
     * is more memory-efficient for bulk operations than {@link JpaRepository#saveAll(Iterable)}. Implemented in
     * {@link EnhancedJpaRepositoryFragmentImpl}.
     *
     * @param entities  entities to save, must not be {@code null}
     * @param batchSize size of each batch
     * @return saved entities
     */
    List<T> saveAllAndFlushInBatches(Collection<T> entities, int batchSize);

}
