package guru.nicks.commons.jpa.generator;

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
 * <p>
 * The UUID is encoded with Crockford Base32, which yields 26 characters (decimal digits and lowercase letters). Just to
 * compare, Base64 encoding yields 22 characters (not a big difference), but they are case-sensitive and may cause
 * issues related to DB column case-sensitiveness. The default hex encoding is 36 characters long.
 * <p>
 * When called for an object already having a non-null ID, does nothing.
 */
@IdGeneratorType(UuidV7CrockfordBase32GeneratorImpl.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({FIELD, METHOD})
@Documented
public @interface UuidV7CrockfordBase32Generator {
}
