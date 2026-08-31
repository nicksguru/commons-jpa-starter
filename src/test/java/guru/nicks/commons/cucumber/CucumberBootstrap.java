package guru.nicks.commons.cucumber;

import guru.nicks.commons.cucumber.world.JpaWorld;
import guru.nicks.commons.cucumber.world.TextWorld;
import guru.nicks.commons.jpa.it.JpaItTestApplication;

import io.cucumber.spring.CucumberContextConfiguration;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Initializes Spring context for the whole test suite. Therefore, initializes beans shared by all scenarios. Mocking
 * should be done inside step definition classes to let them program a different behavior.
 * <p>
 * Please keep in mind that mocked Spring beans ({@link MockitoBean @MockitoBean}) declared in step definition classes
 * conflict with each other because all the steps are part of the same test suite i.e. Spring context. POJO mocks
 * ({@link Mock @Mock}) do not conflict with each other.
 * <p>
 * Boots {@link JpaItTestApplication} with the {@code jpa-it} profile so that scenarios can exercise the enhanced
 * repository fragments against a real H2 database (see {@code application-jpa-it.properties}); the scenario-scoped
 * worlds are registered alongside it.
 */
@CucumberContextConfiguration
@SpringBootTest(classes = {
        // application under test: H2 + enhanced repository factory bean
        JpaItTestApplication.class,
        // scenario-scoped states
        TextWorld.class, JpaWorld.class
}, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("jpa-it")
public class CucumberBootstrap {
}
