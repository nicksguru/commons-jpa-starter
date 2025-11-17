package guru.nicks.commons.jpa.mapper;

import guru.nicks.commons.exception.ExceptionConverter;
import guru.nicks.commons.exception.http.ConflictException;

import jakarta.persistence.OptimisticLockException;
import org.springframework.stereotype.Component;

@Component
public class OptimisticLockExceptionConverter
        implements ExceptionConverter<OptimisticLockException, ConflictException> {
}
