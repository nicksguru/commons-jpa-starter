package guru.nicks.jpa.mapper;

import guru.nicks.exception.ExceptionConverter;
import guru.nicks.exception.http.ConflictException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class DataIntegrityViolationExceptionConverter
        implements ExceptionConverter<DataIntegrityViolationException, ConflictException> {
}
