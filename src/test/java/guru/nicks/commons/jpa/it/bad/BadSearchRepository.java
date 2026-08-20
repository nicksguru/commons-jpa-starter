package guru.nicks.commons.jpa.it.bad;

import guru.nicks.commons.jpa.it.domain.TestDocument;
import guru.nicks.commons.jpa.it.domain.TestDocumentNotFoundException;
import guru.nicks.commons.jpa.repository.EnhancedJpaSearchRepository;

/**
 * Deliberately broken search repository: extends {@link EnhancedJpaSearchRepository} WITHOUT declaring the mandatory
 * 'default' methods ({@code convertToSearchBuilder}/{@code findByFilter}). Used by the fail-fast startup test; must
 * live outside the {@code repo} package so that the main test application context never picks it up.
 */
public interface BadSearchRepository extends EnhancedJpaSearchRepository<TestDocument, String,
        TestDocumentNotFoundException, Void> {
}
