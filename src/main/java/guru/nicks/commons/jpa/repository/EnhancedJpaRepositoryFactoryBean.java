package guru.nicks.commons.jpa.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

import java.io.Serializable;

/**
 * For documentation, see {@link EnhancedJpaRepositoryFactory}.  To use this class, configure it in your Spring Data JPA
 * configuration:
 * <pre>
 * &#64;EnableJpaRepositories(
 *     repositoryFactoryBeanClass = EnhancedJpaRepositoryFactoryBean.class
 * );
 * </pre>
 *
 * @param <R>  repository type
 * @param <T>  entity type
 * @param <ID> entity ID type
 */
@SuppressWarnings("java:S119")  // allow type names like 'ID'
public class EnhancedJpaRepositoryFactoryBean<
        R extends JpaRepository<T, ID>,
        T extends Persistable<ID>,
        ID extends Serializable>
        extends JpaRepositoryFactoryBean<R, T, ID> {

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;

    /**
     * Autowired constructor.
     *
     * @param repositoryInterface repository interface,  must not be {@code null}
     * @param applicationContext  application context, must not be {@code null}
     */
    public EnhancedJpaRepositoryFactoryBean(Class<? extends R> repositoryInterface, ObjectMapper objectMapper,
            ApplicationContext applicationContext) {
        super(repositoryInterface);
        this.objectMapper = objectMapper;
        this.applicationContext = applicationContext;
    }

    @Override
    protected RepositoryFactorySupport createRepositoryFactory(EntityManager entityManager) {
        return new EnhancedJpaRepositoryFactory(entityManager, applicationContext, objectMapper);
    }

}
