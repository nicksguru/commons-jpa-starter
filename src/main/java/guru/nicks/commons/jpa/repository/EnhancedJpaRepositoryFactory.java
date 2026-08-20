package guru.nicks.commons.jpa.repository;

import guru.nicks.commons.jpa.JpaInference;
import guru.nicks.commons.jpa.impl.EnhancedJpaRepositoryFragmentImpl;
import guru.nicks.commons.jpa.impl.EnhancedJpaSearchRepositoryFragmentImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryComposition.RepositoryFragments;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.core.support.RepositoryFragment;

/**
 * Custom {@link JpaRepositoryFactory} that keeps the stock {@link SimpleJpaRepository} repository base class and
 * contributes custom functionality as repository fragments - the same extension hook
 * ({@link RepositoryFactorySupport#getRepositoryFragments(RepositoryMetadata)}) Spring Data JPA itself uses for
 * Querydsl since 3.2. It creates:
 * <ul>
 *     <li>{@link EnhancedJpaSearchRepositoryFragmentImpl} fragment for {@link EnhancedJpaSearchRepository} interfaces
 *         (and their subclasses, such as {@code UserOwnedEnhancedJpaSearchRepository} which needs no own fragment
 *         because its methods are either derived queries or default ones)</li>
 *     <li>{@link EnhancedJpaRepositoryFragmentImpl} fragment for other {@link EnhancedJpaRepository} interfaces</li>
 *     <li>no custom fragments for other repository interfaces</li>
 * </ul>
 */
public class EnhancedJpaRepositoryFactory extends JpaRepositoryFactory {

    private final EntityManager entityManager;
    private final ApplicationContext applicationContext;
    private final JpaInference jpaInference;
    private final ObjectMapper objectMapper;

    /**
     * Constructor.
     *
     * @param entityManager      must not be {@code null}
     * @param applicationContext must not be {@code null}
     * @param objectMapper       can be {@code null}
     */
    public EnhancedJpaRepositoryFactory(EntityManager entityManager, ApplicationContext applicationContext,
            JpaInference jpaInference, ObjectMapper objectMapper) {
        super(entityManager);
        // superclass keeps the EntityManager private, so hold own reference for fragment construction
        this.entityManager = entityManager;
        this.applicationContext = applicationContext;
        this.jpaInference = jpaInference;
        this.objectMapper = objectMapper;
    }

    /**
     * Contributes custom fragments implementing {@link EnhancedJpaRepositoryFragment} and
     * {@link EnhancedJpaSearchRepositoryFragment}. Querydsl support comes from the stock fragment contributed by the
     * superclass.
     * <p>
     * Fragment ordering (verified against Spring Data 3.5 sources): custom fragments are appended BEFORE the
     * superclass' ones, and {@link RepositoryFactorySupport} appends the repository base class target always last.
     * <p>
     * Method routing within the composition is first-match-wins, so custom fragments win over the stock QueryDSL
     * fragment and the base class for shared signatures (notably {@code getById}, deliberately declared in
     * {@link EnhancedJpaRepositoryFragment} to override the deprecated
     * {@code SimpleJpaRepository#getById(Serializable)}).
     *
     * @param metadata repository metadata
     * @return custom fragments appended before the stock ones
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    protected RepositoryFragments getRepositoryFragments(RepositoryMetadata metadata) {
        RepositoryFragments repositoryFragments = RepositoryFragments.empty();
        Class<?> repoInterface = metadata.getRepositoryInterface();

        // subclass of EnhancedJpaSearchRepository, therefore goes FIRST
        if (EnhancedJpaSearchRepository.class.isAssignableFrom(repoInterface)) {
            repositoryFragments = repositoryFragments.append(
                    RepositoryFragment.implemented(
                            // raw type is fine - generics are inferred reflectively inside the fragment implementation
                            new EnhancedJpaSearchRepositoryFragmentImpl(
                                    entityManager, repoInterface, jpaInference, applicationContext, objectMapper)));
        } else if (EnhancedJpaRepository.class.isAssignableFrom(repoInterface)) {
            repositoryFragments = repositoryFragments.append(
                    RepositoryFragment.implemented(
                            new EnhancedJpaRepositoryFragmentImpl(
                                    entityManager, repoInterface, jpaInference, applicationContext)));
        }

        return repositoryFragments.append(super.getRepositoryFragments(metadata));
    }

}
