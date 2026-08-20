package guru.nicks.commons.jpa.repository;

import guru.nicks.commons.jpa.impl.EnhancedJpaRepositoryFragmentImpl;

import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;

/**
 * A combination of common JPA-related repository interfaces augmented with custom functionality contributed by
 * {@link EnhancedJpaRepositoryFragment}. Used implicitly via {@link EnhancedJpaRepositoryFactoryBean}.
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
        extends JpaRepository<T, ID>, QuerydslPredicateExecutor<T>, EnhancedJpaRepositoryFragment<T, ID, E> {

    /**
     * Re-declared to merge the override-equivalent declarations inherited from {@link JpaRepository} (unbounded type
     * parameters) and {@link EnhancedJpaRepositoryFragment} (bounded type parameters) into a single method resolvable
     * through this aggregate; without this bridge, javac considers invocations ambiguous. The implementation is
     * contributed by {@link EnhancedJpaRepositoryFragmentImpl}, see its Javadoc.
     *
     * @param id primary key
     * @return entity with the given ID
     */
    @Override
    T getById(ID id);

}
