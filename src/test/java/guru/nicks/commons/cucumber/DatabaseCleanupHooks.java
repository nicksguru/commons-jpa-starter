package guru.nicks.commons.cucumber;

import guru.nicks.commons.jpa.it.repo.TestAuthorRepository;
import guru.nicks.commons.jpa.it.repo.TestDocumentRepository;

import io.cucumber.java.Before;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Single shared DB cleanup for all {@code @db}-tagged scenarios: every scenario starts with empty tables. Lives in one
 * place so that a new entity has to be added to the cleanup only once.
 */
@RequiredArgsConstructor
public class DatabaseCleanupHooks {

    // DI
    private final TestDocumentRepository documentRepository;

    // DI
    private final TestAuthorRepository authorRepository;

    // DI
    private final PlatformTransactionManager transactionManager;

    /**
     * Wipes all tables that DB-touching scenarios write to, in a single transaction.
     */
    @Before("@db")
    public void cleanupDatabase() {
        var transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(tx -> {
            documentRepository.deleteAllInBatch();
            authorRepository.deleteAllInBatch();
        });
    }
}
