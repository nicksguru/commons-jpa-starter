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
 * @see UuidV7Generator
 */
public class UuidV7GeneratorImpl implements BeforeExecutionGenerator {

    private final Method getter;

    @SuppressWarnings("java:S1172") // unused parameters
    public UuidV7GeneratorImpl(UuidV7Generator annotation, Member idMember, CustomIdGeneratorCreationContext context) {
        check(ReflectHelper.getPropertyType(idMember), idMember.getName()).constraint(
                UUID.class::isAssignableFrom, "must be UUID to assign the value generated");
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
            throw new IllegalStateException("Failed to call entity ID getter method '" + getter.getName() + "'", e);
        }

        if (existingId != null) {
            return existingId;
        }

        @SuppressWarnings("java:S1488") // redundant local variable, for debugging
        UUID uuid = UuidUtils.generateUuidV7();
        return uuid;
    }

}
