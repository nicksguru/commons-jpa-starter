package guru.nicks.commons.jpa.impl;

import guru.nicks.commons.jpa.JpaInference;
import guru.nicks.commons.jpa.domain.EnhancedSqlDialect;
import guru.nicks.commons.jpa.domain.JpaConstants;
import guru.nicks.commons.jpa.repository.EnhancedJpaRepository;
import guru.nicks.commons.jpa.repository.EnhancedJpaRepositoryFragment;
import guru.nicks.commons.utils.ReflectionUtils;

import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.repository.EntityGraph.EntityGraphType;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static guru.nicks.commons.validation.dsl.ValiDsl.checkNotNull;

/**
 * Fragment implementation for {@link EnhancedJpaRepositoryFragment}. It's a plain class, NOT a Spring bean: it's
 * instantiated per repository by {@code EnhancedJpaRepositoryFactory#getRepositoryFragments(...)} and therefore must
 * never become a singleton. The repository base class stays stock {@code SimpleJpaRepository}; methods declared in
 * {@link EnhancedJpaRepositoryFragment} are routed to this fragment because custom fragments are appended before the
 * base class in the repository composition.
 * <p>
 * Calls that previously relied on inherited {@code SimpleJpaRepository} methods ({@code save}, {@code flush},
 * {@code findById}, {@code findAllById}) route through {@link #getOriginalRepositoryProxy()} - this preserves
 * transactions and user overrides.
 *
 * @param <T>  entity type
 * @param <ID> primary key type
 * @param <E>  exception type to throw when entity is not found
 */
// fragment methods participate in the repository proxy's transaction interceptor, so mirror SimpleJpaRepository
// transactional semantics
@Transactional(readOnly = true)
@SuppressWarnings("java:S119")  // allow type names like 'ID'
@Slf4j
public class EnhancedJpaRepositoryFragmentImpl<T extends Persistable<ID>, ID extends Serializable,
        E extends RuntimeException>
        implements EnhancedJpaRepositoryFragment<T, ID, E> {

    private final EntityManager entityManager;
    private final ApplicationContext applicationContext;
    private final JpaInference jpaInference;

    private final Class<? extends EnhancedJpaRepository<T, ID, E>> originalRepositoryInterface;
    private final Class<T> entityClass;
    private final Class<E> exceptionClass;

    private final Supplier<E> exceptionSupplier;

    /**
     * Creates a new {@link EnhancedJpaRepositoryFragmentImpl}.
     *
     * @param entityManager               must not be {@code null}
     * @param originalRepositoryInterface declared in the original repository via (after) {@code extends}
     * @param jpaInference                must not be {@code null}
     * @param applicationContext          must not be {@code null}
     * @throws IllegalArgumentException if {@code originalRepositoryInterface} is not a subclass of
     *                                  {@link EnhancedJpaRepository}
     */
    @SuppressWarnings("unchecked")
    public EnhancedJpaRepositoryFragmentImpl(EntityManager entityManager,
            Class<? extends EnhancedJpaRepository<T, ID, E>> originalRepositoryInterface,
            JpaInference jpaInference, ApplicationContext applicationContext) {
        this.originalRepositoryInterface = checkNotNull(originalRepositoryInterface, "originalRepositoryInterface");

        if (!EnhancedJpaRepository.class.isAssignableFrom(originalRepositoryInterface)) {
            throw new IllegalArgumentException("Original repository interface must be a subclass of "
                    + EnhancedJpaRepository.class.getName());
        }

        this.entityManager = checkNotNull(entityManager, "entityManager");
        this.jpaInference = checkNotNull(jpaInference, "jpaInference");
        this.applicationContext = checkNotNull(applicationContext, "applicationContext");

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

        Constructor<E> exceptionConstructor;
        try {
            exceptionConstructor = getExceptionClass().getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Can't find argumentless constructor for exception class ["
                    + getExceptionClass().getName()
                    + "]: " + e.getMessage(), e);
        }

        // wraps reflection exceptions
        exceptionSupplier = () -> {
            try {
                return exceptionConstructor.newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Can't instantiate exception of class ["
                        + getExceptionClass().getName()
                        + "]: " + e.getMessage(), e);
            }
        };

        log.debug("Wrapped {}", originalRepositoryInterface.getName());
    }

    @Override
    public EnhancedSqlDialect getSqlDialect() {
        return jpaInference.getSqlDialect();
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
    public Supplier<E> getExceptionSupplier() {
        return exceptionSupplier;
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
    public T getById(ID id) {
        return getOriginalRepositoryProxy().findById(id).orElseThrow(getExceptionSupplier());
    }

    @Override
    public List<T> findAllByIdPreserveOrder(Collection<ID> ids) {
        // need indexed access to IDs which only List has
        List<ID> list = (ids instanceof List)
                ? (List<ID>) ids
                : IterableUtils.toList(ids);

        // ID -> index of its first occurrence in the request; makes the sort below O(n log n) instead of O(n^2)
        // (indexOf() would scan the whole list for every element)
        Map<ID, Integer> id2index = HashMap.newHashMap(list.size());
        for (int i = 0; i < list.size(); i++) {
            id2index.putIfAbsent(list.get(i), i);
        }

        return getOriginalRepositoryProxy().findAllById(list).stream()
                .sorted(Comparator.comparing(document -> id2index.get(document.getId())))
                .collect(Collectors.toCollection(ArrayList::new));
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

        // route save/flush through the repository proxy to preserve transactions and user overrides
        EnhancedJpaRepository<T, ID, E> repository = getOriginalRepositoryProxy();
        List<T> savedEntities = new ArrayList<>(entities.size());
        int i = 0;

        for (T entity : entities) {
            savedEntities.add(repository.save(entity));
            i++;

            if (i % batchSize == 0) {
                repository.flush();
                entityManager.clear();
            }
        }

        // Flush any remaining entities that didn't complete a full batch
        if (i % batchSize != 0) {
            repository.flush();
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
