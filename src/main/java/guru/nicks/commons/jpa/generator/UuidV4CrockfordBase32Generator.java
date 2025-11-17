package guru.nicks.commons.jpa.generator;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

/**
 * Generates UUIDv4 which is simply a long random number. Its quality depends on the server's random generator. It's not
 * time-sortable which is an advantage for exposing such UUIDs to the public safely. For purely internal data, consider
 * using {@link UuidV7CrockfordBase32Generator} because it has a better DB index locality.
 * <p>
 * The UUID is encoded with Crockford Base32, which yields 26 characters (decimal digits and lowercase letters). Just to
 * compare, Base64 encoding yields 22 characters (not a big difference), but they are case-sensitive and may cause
 * issues related to DB column case-sensitiveness. The default hex encoding is 36 characters long.
 * <p>
 * When called for an object already having a non-null ID, does nothing.
 */
@IdGeneratorType(UuidV4CrockfordBase32GeneratorImpl.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({FIELD, METHOD})
@Documented
public @interface UuidV4CrockfordBase32Generator {
}
