package guru.nicks.jpa.generator;

import guru.nicks.utils.UuidUtils;

import jakarta.annotation.Nullable;
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
import java.util.UUID;

import static guru.nicks.validation.dsl.ValiDsl.check;

/**
 * @see UuidV4CrockfordBase32Generator
 */
public class UuidV4CrockfordBase32GeneratorImpl implements BeforeExecutionGenerator {

    private final Method getter;

    @SuppressWarnings("java:S1172") // unused parameters
    public UuidV4CrockfordBase32GeneratorImpl(UuidV4CrockfordBase32Generator annotation, Member idMember,
            CustomIdGeneratorCreationContext context) {
        check(ReflectHelper.getPropertyType(idMember), idMember.getName()).constraint(
                String.class::isAssignableFrom, "must be String to assign the value generated");
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

        UUID uuid = UuidUtils.generateUuidV4();
        @SuppressWarnings("java:S1488") // redundant local variable, for debugging
        String encoded = UuidUtils.encodeToCrockfordBase32(uuid);
        return encoded;
    }

}
