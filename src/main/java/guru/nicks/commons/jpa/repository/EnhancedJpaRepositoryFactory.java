package guru.nicks.commons.jpa.repository;

import guru.nicks.commons.jpa.impl.EnhancedJpaRepositoryImpl;
import guru.nicks.commons.jpa.impl.EnhancedJpaSearchRepositoryImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;

import java.io.Serializable;

/**
 * Custom {@link JpaRepositoryFactory} that creates:
 * <ul>
 *     <li>{@link EnhancedJpaRepositoryImpl} for {@link EnhancedJpaRepository} interfaces</li>
 *     <li>{@link EnhancedJpaSearchRepositoryImpl} for {@link EnhancedJpaSearchRepository} interfaces</li>
 *     <li>{@link SimpleJpaRepository} for other repository interfaces</li>
 * </ul>
 */
public class EnhancedJpaRepositoryFactory extends JpaRepositoryFactory {

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    /**
     * Constructor.
     *
     * @param entityManager      must not be {@code null}
     * @param applicationContext must not be {@code null}
     * @param objectMapper       can be {@code null}
     */
    public EnhancedJpaRepositoryFactory(EntityManager entityManager, ApplicationContext applicationContext,
            ObjectMapper objectMapper) {
        super(entityManager);
        this.applicationContext = applicationContext;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected JpaRepositoryImplementation<?, ?> getTargetRepository(RepositoryInformation information,
            EntityManager entityManager) {
        JpaEntityInformation<?, Serializable> entityInformation = getEntityInformation(information.getDomainType());

        // subclass of EnhancedJpaSearchRepository, therefore goes FIRST
        if (EnhancedJpaSearchRepository.class.isAssignableFrom(information.getRepositoryInterface())) {
            return new EnhancedJpaSearchRepositoryImpl(entityInformation, entityManager,
                    information.getRepositoryInterface(), applicationContext, objectMapper);
        }

        if (EnhancedJpaRepository.class.isAssignableFrom(information.getRepositoryInterface())) {
            return new EnhancedJpaRepositoryImpl(entityInformation, entityManager,
                    information.getRepositoryInterface(), applicationContext);
        }

        // fallback
        return new SimpleJpaRepository(entityInformation, entityManager);

    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        // subclass of EnhancedJpaSearchRepository, therefore goes FIRST
        if (EnhancedJpaSearchRepository.class.isAssignableFrom(metadata.getRepositoryInterface())) {
            return EnhancedJpaSearchRepositoryImpl.class;
        }

        if (EnhancedJpaRepository.class.isAssignableFrom(metadata.getRepositoryInterface())) {
            return EnhancedJpaRepositoryImpl.class;
        }

        // fallback
        return SimpleJpaRepository.class;
    }

}
