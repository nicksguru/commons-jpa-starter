package guru.nicks.commons.jpa.mapper;

import guru.nicks.commons.exception.ExceptionConverter;
import guru.nicks.commons.exception.http.ConflictException;

import jakarta.persistence.OptimisticLockException;

public class OptimisticLockExceptionConverter
        implements ExceptionConverter<OptimisticLockException, ConflictException> {
}
