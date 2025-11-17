package guru.nicks.commons.jpa.generator;

import guru.nicks.commons.sortableid.TimeSortableId;

import jakarta.annotation.Nullable;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;
import org.hibernate.id.factory.spi.CustomIdGeneratorCreationContext;
import org.hibernate.internal.util.ReflectHelper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.EnumSet;

import static guru.nicks.commons.validation.dsl.ValiDsl.check;
import static guru.nicks.commons.validation.dsl.ValiDsl.checkNotBlank;

/**
 * @see SequenceBasedTimeSortableIdGenerator
 */
public class SequenceBasedTimeSortableIdGeneratorImpl implements BeforeExecutionGenerator {

    /**
     * SQL statement that fetches the sequence's next value.
     */
    private final String sequenceGetNextValueSql;
    private final Method getter;

    public SequenceBasedTimeSortableIdGeneratorImpl(SequenceBasedTimeSortableIdGenerator annotation, Member idMember,
            CustomIdGeneratorCreationContext context) {
        check(ReflectHelper.getPropertyType(idMember), idMember.getName()).constraint(
                String.class::isAssignableFrom, "must be String to assign the value generated");

        String sequenceName = annotation.sequenceName();
        checkNotBlank(sequenceName, "sequence name");

        // generate and cache SQL for querying the given sequence
        JdbcEnvironment jdbcEnvironment = context.getServiceRegistry().getService(JdbcEnvironment.class);
        Dialect dialect = jdbcEnvironment.getDialect();
        sequenceGetNextValueSql = dialect.getSequenceSupport().getSequenceNextValString(sequenceName);

        getter = ReflectHelper.getGetterOrNull(idMember.getDeclaringClass(), idMember.getName());
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EventTypeSets.INSERT_ONLY;
    }

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, @Nullable Object currentValue,
            EventType eventType) {
        Object existingId;
        // WARNING: currentValue argument is null even if the ID has already been set for the entity, therefore
        // calling the getter is the only way to go
        try {
            existingId = getter.invoke(owner);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to call entity ID getter " + getter.getName());
        }

        if (existingId != null) {
            return existingId;
        }

        long nextValue = session.createQuery(sequenceGetNextValueSql, Long.class).uniqueResult();
        return new TimeSortableId(nextValue).getId();
    }

}
