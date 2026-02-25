package guru.nicks.commons.jpa.mapper;

import guru.nicks.commons.exception.ExceptionConverter;
import guru.nicks.commons.exception.http.ConflictException;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

public class ObjectOptimisticLockingFailureExceptionConverter
        implements ExceptionConverter<ObjectOptimisticLockingFailureException, ConflictException> {
}
