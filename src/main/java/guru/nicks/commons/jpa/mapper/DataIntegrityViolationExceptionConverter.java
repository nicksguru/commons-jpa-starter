package guru.nicks.commons.jpa.mapper;

import guru.nicks.commons.exception.AlreadyExistsException;
import guru.nicks.commons.exception.ExceptionConverter;
import guru.nicks.commons.exception.http.ConflictException;

import org.springframework.dao.DataIntegrityViolationException;

public class DataIntegrityViolationExceptionConverter
        implements ExceptionConverter<DataIntegrityViolationException, ConflictException> {

    /**
     * If the exception message contains 'duplicate key value', it's mapped to a more specific
     * {@link AlreadyExistsException}.
     */
    @Override
    public ConflictException convert(DataIntegrityViolationException cause) {
        return (cause != null)
                && (cause.getMessage() != null)
                && cause.getMessage().contains("duplicate key value")
                ? new AlreadyExistsException(cause)
                : ExceptionConverter.super.convert(cause);
    }

}
