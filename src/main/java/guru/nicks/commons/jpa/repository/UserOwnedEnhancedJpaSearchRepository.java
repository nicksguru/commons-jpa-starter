package guru.nicks.commons.jpa.repository;

import org.springframework.data.domain.Persistable;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.Optional;

/**
 * Base repository interface for entities owned by users. Provides common methods for accessing entities scoped to a
 * specific user.
 *
 * @param <T>  entity type
 * @param <ID> entity ID type
 * @param <E>  exception type thrown when entity is not found
 * @param <F>  filter type for searching entities
 */
@NoRepositoryBean
@SuppressWarnings("java:S119")  // allow type names like 'ID'
public interface UserOwnedEnhancedJpaSearchRepository<
        T extends Persistable<ID>,
        ID extends Serializable,
        E extends RuntimeException,
        F>
        extends EnhancedJpaSearchRepository<T, ID, E, F> {

    /**
     * Finds {@code T} owned by the given user.
     *
     * @param id     entity ID
     * @param userId user ID who owns the entity
     * @return optional entity
     */
    Optional<T> findByIdAndUserId(ID id, String userId);

    /**
     * Finds {@code T} owned by the given user, throwing {@code E} if not found.
     *
     * @param id     entity ID
     * @param userId user ID who owns the entity
     * @return entity owned by the user
     */
    @Transactional(readOnly = true)
    default T getByIdAndUserId(ID id, String userId) {
        return findByIdAndUserId(id, userId).orElseThrow(getExceptionSupplier());
    }

    /**
     * Deletes {@code T} owned by the given user.
     * <p>
     * This method is idempotent: it succeeds even if the entity doesn't exist. If it exists, but belongs to a different
     * user, this method simply does nothing. This is needed from the security perspective - to prevent tracking which
     * entity belongs to which user.
     *
     * @param id     entity ID
     * @param userId user ID who owns the entity
     */
    void deleteByIdAndUserId(ID id, String userId);

}
