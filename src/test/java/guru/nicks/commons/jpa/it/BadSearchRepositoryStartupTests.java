package guru.nicks.commons.jpa.it;

import guru.nicks.commons.jpa.repository.EnhancedJpaRepositoryFactoryBean;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Regression test for the fail-fast validation performed by {@code EnhancedJpaSearchRepositoryFragmentImpl}: a search
 * repository declared WITHOUT the mandatory 'default' methods must fail context startup with the
 * METHODS_TO_IMPLEMENT error instead of failing later at runtime with a StackOverflowError. Runs in an isolated
 * application context (separate H2 database) so that the main test context stays unaffected.
 */
class BadSearchRepositoryStartupTests {

    /**
     * Booting a context that registers {@code BadSearchRepository} fails during refresh with an
     * IllegalArgumentException listing both missing methods.
     */
    @Test
    void searchRepositoryWithoutDefaultMethodsFailsContextStartup() {
        var thrown = catchThrowable(() -> new SpringApplicationBuilder(BadApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=jdbc:h2:mem:jpa-it-bad;DB_CLOSE_DELAY=-1",
                        // required by MyJpaProperties validation (the URL above is explicit, so the values are unused)
                        "spring.datasource.my.host=localhost",
                        "spring.datasource.my.port=5432",
                        "spring.datasource.my.database=jpa-it-bad",
                        "spring.jpa.hibernate.ddl-auto=create-drop",
                        "spring.jpa.hibernate.naming.physical-strategy="
                                + "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
                        "app.database.dialect=POSTGRES")
                .run());

        assertThat(thrown).isNotNull();
        assertThat(ExceptionUtils.getRootCause(thrown))
                .isInstanceOf(IllegalArgumentException.class);

        // collect messages of the whole cause chain - the fragment error is wrapped by bean creation exceptions
        var messages = new StringBuilder();
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            messages.append(current.getMessage()).append(' ');
        }

        assertThat(messages.toString())
                .contains("is missing the following 'default' methods")
                .contains("convertToSearchBuilder")
                .contains("findByFilter");
    }

    /**
     * Isolated application that registers ONLY the broken repository.
     */
    @SpringBootApplication
    @EnableJpaRepositories(basePackages = "guru.nicks.commons.jpa.it.bad",
            repositoryFactoryBeanClass = EnhancedJpaRepositoryFactoryBean.class)
    static class BadApp {
    }

}
