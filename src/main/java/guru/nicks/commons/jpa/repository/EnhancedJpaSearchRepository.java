package guru.nicks.commons.jpa.repository;

import guru.nicks.commons.jpa.domain.FullTextSearchAwareEntity;

import org.springframework.data.domain.Persistable;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;

/**
 * Search-related enhancements for JPA repositories: a combination of {@link EnhancedJpaRepository} and custom search
 * functionality contributed by {@link EnhancedJpaSearchRepositoryFragment}. Used implicitly via
 * {@link EnhancedJpaRepositoryFactoryBean}.
 * <p>
 * Repositories must implement the following methods (failure to do so will result in an exception during
 * initialization):
 * <ul>
 *     <li>{@link EnhancedJpaSearchRepositoryFragment#convertToSearchBuilder(Object)}</li>
 *     <li>{@link EnhancedJpaSearchRepositoryFragment#findByFilter(Object, org.springframework.data.domain.Pageable)}
 *     </li>
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
        extends EnhancedJpaRepository<T, ID, E>, EnhancedJpaSearchRepositoryFragment<T, ID, F> {

}
