package guru.nicks.commons.jpa.impl;

import guru.nicks.commons.jpa.domain.EnhancedSqlDialect;
import guru.nicks.commons.jpa.domain.JpaConstants;
import guru.nicks.commons.jpa.repository.EnhancedJpaRepository;
import guru.nicks.commons.utils.ReflectionUtils;

import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.QuerydslJpaRepository;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Base implementation for {@link EnhancedJpaRepository} that provides dependency injection support. This class extends
 * {@link SimpleJpaRepository} and adds custom functionality while allowing proper Spring bean injection without the
 * need for a static context holder.
 *
 * @param <T>  entity type
 * @param <ID> primary key type
 * @param <E>  exception type to throw when entity is not found
 */
@Transactional(readOnly = true) // borrowed from SimpleJpaRepository
@SuppressWarnings("java:S119")  // allow type names like 'ID'
@Slf4j
public class EnhancedJpaRepositoryImpl<T extends Persistable<ID>, ID extends Serializable, E extends RuntimeException>
        extends QuerydslJpaRepository<T, ID>
        implements EnhancedJpaRepository<T, ID, E> {

    private final EntityManager entityManager;
    private final EnhancedSqlDialect sqlDialect;
    private final ApplicationContext applicationContext;

    private final Class<? extends EnhancedJpaRepository<T, ID, E>> originalRepositoryInterface;
    private final Class<T> entityClass;
    private final Class<E> exceptionClass;

    // Cache the exception instance to avoid expensive reflection on every call
    private final E cachedExceptionInstance;

    /**
     * Creates a new {@link EnhancedJpaRepositoryImpl} for the given {@link JpaEntityInformation} and
     * {@link EntityManager}.
     *
     * @param entityInformation           must not be {@code null}
     * @param entityManager               must not be {@code null}
     * @param originalRepositoryInterface declared in the original repository via {@code extends} - must be
     *                                    {@link EnhancedJpaRepository} or its subclass
     * @param applicationContext          must not be {@code null}
     * @throws IllegalArgumentException if {@code originalRepositoryInterface} is not a subclass of
     *                                  {@link EnhancedJpaRepository}
     */
    @SuppressWarnings("unchecked")
    public EnhancedJpaRepositoryImpl(JpaEntityInformation<T, ID> entityInformation, EntityManager entityManager,
            Class<? extends EnhancedJpaRepository<T, ID, E>> originalRepositoryInterface,
            ApplicationContext applicationContext) {
        super(entityInformation, entityManager);

        if (!EnhancedJpaRepository.class.isAssignableFrom(originalRepositoryInterface)) {
            throw new IllegalArgumentException("Original repository interface must be a subclass of "
                    + EnhancedJpaRepository.class.getName());
        }

        this.entityManager = entityManager;
        this.applicationContext = applicationContext;

        sqlDialect = applicationContext
                .getEnvironment()
                .getProperty(SQL_DIALECT_PROPERTY_NAME, EnhancedSqlDialect.class, DEFAULT_SQL_DIALECT);
        log.info("Using SQL dialect {}", sqlDialect);

        entityClass = (Class<T>) ReflectionUtils
                .findMaterializedGenericType(originalRepositoryInterface,
                        EnhancedJpaRepository.class, Persistable.class)
                .orElseThrow(() -> new IllegalStateException("Failed to infer entity class from "
                        + originalRepositoryInterface));

        exceptionClass = (Class<E>) ReflectionUtils
                .findMaterializedGenericType(originalRepositoryInterface,
                        EnhancedJpaRepository.class, Throwable.class)
                .orElseThrow(() -> new IllegalStateException("Failed to infer exception class from "
                        + originalRepositoryInterface));

        this.originalRepositoryInterface = originalRepositoryInterface;
        // Pre-instantiate exception to avoid expensive reflection on every getByIdOrThrow call
        this.cachedExceptionInstance = ReflectionUtils.instantiateEvenWithoutDefaultConstructor(exceptionClass);

        log.debug("Wrapped {}", originalRepositoryInterface.getName());
    }

    @Override
    public EnhancedSqlDialect getSqlDialect() {
        return sqlDialect;
    }

    @Override
    public Class<T> getEntityClass() {
        return entityClass;
    }

    @Override
    public Class<E> getExceptionClass() {
        return exceptionClass;
    }

    @Override
    public EntityGraph<T> createEntityGraph() {
        return entityManager.createEntityGraph(getEntityClass());
    }

    @Override
    public Optional<T> findByIdWithFetchGraph(ID id, EntityGraph<T> graph) {
        Map<String, Object> hints = Map.of(EntityGraphType.FETCH.getKey(), graph);
        return Optional.ofNullable(entityManager.find(getEntityClass(), id, hints));
    }

    @Override
    public T getByIdOrThrow(ID id) {
        return findById(id).orElseThrow(() -> cachedExceptionInstance);
    }

    @Override
    public List<T> findAllByIdPreserveOrder(Collection<ID> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return new ArrayList<>();
        }

        // build index map with initial capacity to avoid resizing
        Map<ID, T> foundEntities = findAllById(ids).stream()
                .collect(Collectors.toMap(Persistable::getId, entity -> entity,
                        // merge duplicate keys
                        (existing, replacement) -> existing,
                        () -> new HashMap<>(ids.size())
                ));

        // pre-allocate result list with exact size needed
        List<T> result = new ArrayList<>(ids.size());

        for (ID id : ids) {
            T entity = foundEntities.get(id);

            // entity with the given ID was found
            if (entity != null) {
                result.add(entity);
            }
        }

        return result;
    }

    @Transactional
    @Override
    public List<T> saveAllAndFlushInBatches(Collection<T> entities) {
        return saveAllAndFlushInBatches(entities, JpaConstants.INTERNAL_PAGE_SIZE);
    }

    @Transactional
    @Override
    public List<T> saveAllAndFlushInBatches(Collection<T> entities, int batchSize) {
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
                entityManager.clear();
            }
        }

        // Flush any remaining entities that didn't complete a full batch
        if (i % batchSize != 0) {
            flush();
            entityManager.clear();
        }

        return savedEntities;
    }

    /**
     * Returns the original repository interface if it's a subclass of {@link EnhancedJpaRepository}, or {@code null}.
     * The user code is:
     * {@code public interface MyRepository extends EnhancedJpaRepository<MyEntity, String, MyException> {...}}.
     *
     * @return referring to the above example, it's {@code MyRepository.class}
     */
    protected Class<? extends EnhancedJpaRepository<T, ID, E>> getOriginalRepositoryInterface() {
        return originalRepositoryInterface;
    }

    /**
     * Returns the original repository proxy if it's a subclass of {@link EnhancedJpaRepository}. The user code is:
     * {@code public interface MyRepository extends EnhancedJpaRepository<MyEntity, UUID, MyException>}.
     *
     * @return referring to the above example, it's {@code applicationContext.getBean(MyRepository.class)}
     * @throws IllegalStateException if the original repository interface is not set
     */
    protected EnhancedJpaRepository<T, ID, E> getOriginalRepositoryProxy() {
        if (originalRepositoryInterface == null) {
            throw new IllegalStateException("Original repository interface is not set");
        }

        try {
            return applicationContext.getBean(originalRepositoryInterface);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to retrieve repository bean for "
                    + originalRepositoryInterface.getName() + ": " + e.getMessage(), e);
        }
    }

    protected EntityManager getEntityManager() {
        return entityManager;
    }

}
