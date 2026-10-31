package guru.nicks.commons.jpa.interceptor;

import guru.nicks.commons.jpa.domain.AuditableEntity;

import org.hibernate.Interceptor;
import org.hibernate.type.Type;

import java.time.Instant;

/**
 * Restores the Hibernate 6 behavior lost in Hibernate 7.2: entities with manually assigned IDs (natural keys, like
 * {@code ProductGroupEntity#code}) are saved via {@code merge()}, and Hibernate 7.2 no longer runs the 'save' lifecycle
 * for transient instances on this path - {@code @PrePersist} never fires, so {@code createdDate} stays {@code null} and
 * the {@code INSERT}/{@code UPDATE} violates the {@code NOT NULL} constraint.
 * <p>
 * This interceptor fills in the missing audit dates right before {@code INSERT} and {@code UPDATE} (including the
 * entity field itself, to keep the in-session state consistent). It's a no-op when dates are already set (by
 * {@code @PrePersist}/{@code @PreUpdate} callbacks or explicitly).
 * <p>
 * WARNING: the instance is shared by all Hibernate sessions, therefore it must be stateless and thread-safe (it is).
 */
public class AuditDatesInterceptor implements Interceptor {

    @Override
    public boolean onFlushDirty(Object entity, Object id, Object[] currentState, Object[] previousState,
            String[] propertyNames, Type[] types) {
        return fillAuditDates(entity, currentState, propertyNames);
    }

    @Override
    public boolean onSave(Object entity, Object id, Object[] state, String[] propertyNames, Type[] types) {
        return fillAuditDates(entity, state, propertyNames);
    }

    /**
     * Fills in {@code createdDate} / {@code lastModifiedDate} when they're {@code null}.
     *
     * @param entity        entity being saved or flushed
     * @param state         current property values (mutated in place)
     * @param propertyNames property names, parallel to {@code state}
     * @return whether the state was changed
     */
    private boolean fillAuditDates(Object entity, Object[] state, String[] propertyNames) {
        if (!(entity instanceof AuditableEntity<?> auditable)) {
            return false;
        }

        boolean changed = false;
        var now = Instant.now();

        for (var i = 0; i < propertyNames.length; i++) {
            if ("createdDate".equals(propertyNames[i]) && (state[i] == null)) {
                state[i] = now;
                auditable.setCreatedDate(now);
                changed = true;
            } else if ("lastModifiedDate".equals(propertyNames[i]) && (state[i] == null)) {
                state[i] = now;
                auditable.setLastModifiedDate(now);
                changed = true;
            }
        }

        return changed;
    }

}
