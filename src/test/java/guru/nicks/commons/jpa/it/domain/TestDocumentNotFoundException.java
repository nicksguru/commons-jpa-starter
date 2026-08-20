package guru.nicks.commons.jpa.it.domain;

import guru.nicks.commons.exception.BusinessException;
import lombok.experimental.StandardException;

/**
 * Exception thrown when a {@link TestDocument} is not found; must have an argumentless constructor because
 * {@code EnhancedJpaRepositoryFragmentImpl} instantiates it reflectively via {@code getExceptionSupplier()}.
 */
@StandardException
public class TestDocumentNotFoundException extends BusinessException {
}
