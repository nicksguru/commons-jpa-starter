package guru.nicks.jpa.mapper;

import guru.nicks.exception.ExceptionConverter;
import guru.nicks.exception.http.ConflictException;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
public class ObjectOptimisticLockingFailureExceptionConverter
        implements ExceptionConverter<ObjectOptimisticLockingFailureException, ConflictException> {
}
