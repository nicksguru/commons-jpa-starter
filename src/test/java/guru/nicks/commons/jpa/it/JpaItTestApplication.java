package guru.nicks.commons.jpa.it;

import guru.nicks.commons.jpa.repository.EnhancedJpaRepositoryFactoryBean;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Spring Boot application for the repository regression tests: boots an H2 in-memory database and installs
 * {@link EnhancedJpaRepositoryFactoryBean} for the test repositories, exactly as real applications are instructed to
 * do by {@code CommonsJpaAutoConfiguration}.
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "guru.nicks.commons.jpa.it.repo",
        repositoryFactoryBeanClass = EnhancedJpaRepositoryFactoryBean.class)
public class JpaItTestApplication {
}
