package guru.nicks.commons.jpa.repository;

import guru.nicks.commons.ApplicationContextHolder;
import guru.nicks.commons.utils.ReflectionUtils;

import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Transactional;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A combination of common JPA-related repository interfaces augmented with some custom functionality.
 * <p>
 * {@link SimpleJpaRepository} (Spring Data implementation of {@link JpaRepository}) has
 * {@code @Transactional(readOnly=true)} at the class level and {@code Transactional} on methods that write something -
 * this interface does the same. This means transactions are created on the fly if caller has not created them.
 * <p>
 * WARNING: Spring Data wraps all non-static repository methods in a decorator that rejects null arguments.
 *
 * @param <T>  entity type
 * @param <ID> primary key type
 * @param <E>  exception type to throw when entity is not found
 */
@NoRepositoryBean
@SuppressWarnings("java:S119")  // allow type names like 'ID'
public interface EnhancedJpaRepository<T extends Persistable<ID>, ID, E extends RuntimeException>
        extends JpaRepository<T, ID>, QuerydslPredicateExecutor<T> {

    /**
     * Recommended value for <b>internal</b> paginated operations.
     */
    int RECOMMENDED_BATCH_SIZE = 500;

    /**
     * @see #getDialect()
     */
    String DIALECT_PROPERTY_NAME = "app.database.dialect";

    Class<?> STATIC_THIS = MethodHandles.lookup().lookupClass();

    /**
     * @return SQL dialect read from the {@value #DIALECT_PROPERTY_NAME} config property, or
     *         {@link EnhancedJpaDialect#POSTGRES} if there's no such property / no app context
     */
    static EnhancedJpaDialect getDialect() {
        return ApplicationContextHolder.findApplicationContext()
                .map(ApplicationContext::getEnvironment)
                .map(env -> env.getProperty(DIALECT_PROPERTY_NAME, EnhancedJpaDialect.class))
                .orElse(EnhancedJpaDialect.POSTGRES);
    }

    /**
     * @return class of type {@code T}
     * @throws IllegalStateException if the entity class is not found in the generic type parameters
     */
    @SuppressWarnings("unchecked")
    default Class<T> getEntityClass() {
        return (Class<T>) ReflectionUtils
                .findMaterializedGenericType(getClass(), STATIC_THIS, Persistable.class)
                .orElseThrow(() -> new IllegalStateException("Failed to find entity class"));
    }

    /**
     * @return class of type {@code E}
     * @throws IllegalStateException if the exception class is not found in the generic type parameters
     */
    @SuppressWarnings("unchecked")
    default Class<E> getExceptionClass() {
        return (Class<E>) ReflectionUtils
                .findMaterializedGenericType(getClass(), STATIC_THIS, Throwable.class)
                .orElseThrow(() -> new IllegalStateException("Failed to find generic exception type"));
    }

    /**
     * Retrieves bean out of {@link ApplicationContextHolder}.
     *
     * @return bean
     * @throws NoSuchBeanDefinitionException {@link EntityManager} bean not found
     */
    default EntityManager getEntityManagerBean() {
        return ApplicationContextHolder
                .getApplicationContext()
                .getBean(EntityManager.class);
    }

    /**
     * Creates an entity graph for {@link #getEntityClass()}.
     *
     * @return entity graph
     * @see #findByIdWithFetchGraph(Object, EntityGraph)
     */
    default EntityGraph<T> createEntityGraph() {
        return getEntityManagerBean().createEntityGraph(getEntityClass());
    }

    /**
     * Finds an entity by its ID using the specified entity graph for fetch optimization.
     * <p>
     * This method allows for fine-grained control over which entity attributes and associations are eagerly loaded by
     * providing a custom {@link EntityGraph}. The entity graph is used as a FETCH hint to optimize the query
     * performance and reduce, thanks to LEFT JOINs, the number of database round-trips.
     *
     * @param id    primary key
     * @param graph entity graph defining which attributes and associations to fetch eagerly; must not be {@code null}
     * @return optional entity
     */
    @Transactional(readOnly = true)
    default Optional<T> findByIdWithFetchGraph(ID id, EntityGraph<T> graph) {
        Map<String, Object> hints = Map.of(EntityGraphType.FETCH.getKey(), graph);

        return Optional.ofNullable(getEntityManagerBean()
                .find(getEntityClass(), id, hints));
    }

    /**
     * Does the same as {@link #findById(Object)} but throws an exception if entity is not found.
     *
     * @param id entity ID
     * @return entity
     * @throws E                          entity not found
     * @throws BeanInstantiationException {@code E}'s argument-less constructor failed
     */
    @Transactional(readOnly = true)
    default T getByIdOrThrow(ID id) {
        return findById(id).orElseThrow(() ->
                ReflectionUtils.instantiateEvenWithoutDefaultConstructor(getExceptionClass()));
    }

    /**
     * Unlike {@link #findAllById(Iterable)}, returns elements in the same order as their IDs are returned by the input
     * collection (which may or may not be ordered).
     *
     * @param ids IDs
     * @return elements in the same order as in {@code ids}, mutable list - crucial for Hibernate if this list is
     *         assigned to another entity; if it's immutable, Hibernate can't save it because it tries to clear it
     */
    @Transactional(readOnly = true)
    default List<T> findAllByIdPreserveOrder(Collection<ID> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return new ArrayList<>();
        }

        Map<ID, T> foundEntities = findAllById(ids).stream()
                .collect(Collectors.toMap(Persistable::getId, entity -> entity));

        return ids.stream()
                .map(foundEntities::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * A more readable shortcut to {@link #findAllBy()}.
     * <p>
     * WARNING: {@code try-with-resources} is required.
     *
     * @return stream of entities
     */
    @Transactional(readOnly = true)
    default Stream<T> findAllAsStream() {
        return findAllBy();
    }

    /**
     * Fetch all documents using DB cursor. Faster than {@link Pageable}, requires less memory than {@link #findAll()}.
     * Requires {@code try-with-resources}:
     * <pre>
     *  try (var stream = repository.findAllAsStream()) {
     *      [...]
     *  }
     * </pre>
     * WARNING: call {@link EntityManager#detach(Object)} after processing each record (or a batch of records) to avoid
     * OOM while retrieving big data (Hibernate keeps references to all the entities fetched).
     *
     * @return stream of entities
     */
    @Transactional(readOnly = true)
    Stream<T> findAllBy();

    /**
     * Saves a collection of entities in batches of {@value #RECOMMENDED_BATCH_SIZE}, flushing and clearing the
     * persistence context after each batch. This is more memory-efficient for bulk operations than
     * {@link #saveAll(Iterable)}.
     *
     * @param entities entities to save, must not be {@code null}
     * @return saved entities
     */
    @Transactional
    default List<T> saveAllAndFlushInBatches(Collection<T> entities) {
        return saveAllAndFlushInBatches(entities, RECOMMENDED_BATCH_SIZE);
    }

    /**
     * Saves a collection of entities in batches, flushing and clearing the persistence context after each batch. This
     * is more memory-efficient for bulk operations than {@link #saveAll(Iterable)}.
     *
     * @param entities  entities to save, must not be {@code null}
     * @param batchSize size of each batch
     * @return saved entities
     */
    @Transactional
    default List<T> saveAllAndFlushInBatches(Collection<T> entities, int batchSize) {
        if (CollectionUtils.isEmpty(entities)) {
            return new ArrayList<>();
        }

        List<T> savedEntities = new ArrayList<>(entities.size());
        int i = 0;

        for (T entity : entities) {
            savedEntities.add(save(entity));
            i++;

            if (i % batchSize == 0) {
                flush();
                getEntityManagerBean().clear();
            }
        }

        return savedEntities;
    }

}
