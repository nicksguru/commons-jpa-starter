package guru.nicks.commons.jpa.repository;

import guru.nicks.commons.jpa.JpaInference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

    /**
     * Jackson 2 mapper built locally: Spring Boot 4 autoconfigures Jackson 3 only (no Jackson 2 ObjectMapper bean
     * anymore), so repositories can't rely on bean injection. Java 8 dates are processed the same way the former
     * Boot-provided mapper did.
     */
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private final ApplicationContext applicationContext;
    private final JpaInference jpaInference;

    /**
     * Autowired constructor.
     *
     * @param repositoryInterface repository interface,  must not be {@code null}
     * @param applicationContext  application context, must not be {@code null}
     * @param jpaInference        JPA inference helper, must not be {@code null}
     */
    public EnhancedJpaRepositoryFactoryBean(Class<? extends R> repositoryInterface,
            ApplicationContext applicationContext, JpaInference jpaInference) {
        super(repositoryInterface);
        this.applicationContext = applicationContext;
        this.jpaInference = jpaInference;
    }

    @Override
    protected RepositoryFactorySupport createRepositoryFactory(EntityManager entityManager) {
        return new EnhancedJpaRepositoryFactory(entityManager, applicationContext, jpaInference, OBJECT_MAPPER);
    }

}
