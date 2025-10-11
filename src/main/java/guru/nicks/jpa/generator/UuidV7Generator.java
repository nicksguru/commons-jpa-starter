package guru.nicks.jpa.generator;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

/**
 * Generates UUIDv7 whose big advantage is that not only it's time-sortable (by the milliseconds prefix) but also
 * sortable within the same milli thanks to an incrementing counter (whose initial value is random for each milli).
 * <p>
 * WARNING: exposing the time of record creation may be a potential security or business risk.
 */
@IdGeneratorType(UuidV7GeneratorImpl.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({FIELD, METHOD})
@Documented
public @interface UuidV7Generator {
}
