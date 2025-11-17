package guru.nicks.commons.jpa.mapper;

import guru.nicks.commons.exception.ExceptionConverter;
import guru.nicks.commons.exception.http.ConflictException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class DataIntegrityViolationExceptionConverter
        implements ExceptionConverter<DataIntegrityViolationException, ConflictException> {
}
