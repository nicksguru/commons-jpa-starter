package guru.nicks.commons.jpa.mapper;

import guru.nicks.commons.exception.ExceptionConverter;
import guru.nicks.commons.exception.http.ConflictException;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
public class ObjectOptimisticLockingFailureExceptionConverter
        implements ExceptionConverter<ObjectOptimisticLockingFailureException, ConflictException> {
}
