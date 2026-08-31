package guru.nicks.commons.cucumber;

import guru.nicks.commons.jpa.repository.EnhancedJpaRepositoryFactoryBean;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Step definitions for the fail-fast validation performed by {@code EnhancedJpaSearchRepositoryFragmentImpl}: a search
 * repository declared WITHOUT the mandatory 'default' methods must fail context startup with the METHODS_TO_IMPLEMENT
 * error instead of failing later at runtime with a StackOverflowError. The broken repository boots in an isolated
 * application context (separate H2 database) so that the shared scenario context stays unaffected.
 */
public class BadSearchRepositoryStartupSteps {

    private Throwable startupFailure;

    /**
     * Boots the isolated {@link BadApp} context; the failure is captured because startup must fail during refresh.
     */
    @When("an isolated application context boots with a search repository missing the default methods")
    public void anIsolatedApplicationContextBootsWithABadSearchRepository() {
        startupFailure = catchThrowable(() -> new SpringApplicationBuilder(BadApp.class)
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
    }

    /**
     * Verifies that context startup failed with IllegalArgumentException as the root cause.
     */
    @Then("the startup should fail with IllegalArgumentException as the root cause")
    public void theStartupShouldFailWithIllegalArgumentExceptionAsTheRootCause() {
        assertThat(startupFailure).isNotNull();
        assertThat(ExceptionUtils.getRootCause(startupFailure))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies that the cause chain mentions both missing 'default' methods.
     *
     * @param firstMethod  first missing method name
     * @param secondMethod second missing method name
     */
    @Then("the cause chain should mention the missing methods {string} and {string}")
    public void theCauseChainShouldMentionTheMissingMethods(String firstMethod, String secondMethod) {
        // collect messages of the whole cause chain - the fragment error is wrapped by bean creation exceptions
        var messages = new StringBuilder();
        for (Throwable current = startupFailure; current != null; current = current.getCause()) {
            messages.append(current.getMessage()).append(' ');
        }

        assertThat(messages.toString())
                .contains("is missing the following 'default' methods")
                .contains(firstMethod)
                .contains(secondMethod);
    }

    /**
     * Isolated application that registers ONLY the broken repository. Lives outside the packages scanned by the shared
     * test context so that {@code BadSearchRepository} never leaks into it; entity scanning is pointed at the shared
     * test domain so that the isolated context resolves the same entities as the original JUnit fixture did.
     */
    @SpringBootApplication
    @EntityScan(basePackages = "guru.nicks.commons.jpa.it.domain")
    @EnableJpaRepositories(basePackages = "guru.nicks.commons.jpa.it.bad",
            repositoryFactoryBeanClass = EnhancedJpaRepositoryFactoryBean.class)
    static class BadApp {
    }
}
