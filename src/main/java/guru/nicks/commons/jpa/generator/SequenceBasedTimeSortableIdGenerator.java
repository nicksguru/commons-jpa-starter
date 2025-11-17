package guru.nicks.commons.jpa.generator;

import guru.nicks.commons.sortableid.TimeSortableId;

import org.hibernate.annotations.IdGeneratorType;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

/**
 * Uses sequence name passed in {@link SequenceStyleGenerator#SEQUENCE_PARAM} to obtain a {@link Long} number and
 * convert it with {@link TimeSortableId}.
 * <p>
 * WARNING: exposing the time of record creation may be a potential security or business risk. Shifting the timestamp by
 * a certain delta is easy guessable by comparing the timestamp to the real one.
 * <p>
 * When called for an object already having a non-null ID, does nothing.
 */
@IdGeneratorType(SequenceBasedTimeSortableIdGeneratorImpl.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({FIELD, METHOD})
@Documented
public @interface SequenceBasedTimeSortableIdGenerator {

    String sequenceName();

}
