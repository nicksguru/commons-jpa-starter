package guru.nicks.jpa.mapper;

import guru.nicks.exception.ExceptionConverter;
import guru.nicks.exception.http.ConflictException;

import jakarta.persistence.OptimisticLockException;
import org.springframework.stereotype.Component;

@Component
public class OptimisticLockExceptionConverter
        implements ExceptionConverter<OptimisticLockException, ConflictException> {
}
