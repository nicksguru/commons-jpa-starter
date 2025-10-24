package guru.nicks.jpa.domain;

import jakarta.annotation.Nullable;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * JPA entity that keeps track of who and when created/modified it. In Postgres, timestamps have a
 * <a href="https://www.postgresql.org/docs/current/datatype-datetime.html">microsecond precision</a>, which makes
 * them a good default sorting field for records whose primary keys are not time-sortable (UUID v7 are, but exposing the
 * time of record creation may have a potential security or business risk).
 * <p>
 * This class doesn't keep the full update history (for that, use Hibernate Envers), merely the creation and last update
 * info. Works on the Hibernate level, doesn't need {@link EnableJpaAuditing @EnableJpaAuditing} (the latter works
 * incorrectly with Hibernate 6 - see comments on individual properties for details).
 */
@MappedSuperclass
@NoArgsConstructor
@Getter
@Setter
//
@FieldNameConstants
@SuperBuilder
@ToString
@SuppressWarnings("java:S119") // allow non-single-letter type names in generics
public abstract class AuditableEntity<ID> implements Persistable<ID>, Serializable {

    /**
     * If non-null, overrides default {@link #isNew()} behavior (checking for non-null {@link #getId()}).
     */
    @ToString.Exclude
    @Transient
    private Boolean isNew;

    /**
     * Date of creation. Should not be annotated with <code>@NotNull</code> because it's assigned automatically AFTER
     * entity validation.
     * <p>
     * WARNING: Spring's {@link CreatedDate @CreatedDate} does not work correctly with Hibernate 6 - the property is
     * rewritten every time a persistent object is referred in another (non-persistent) one, for example when a new shop
     * order refers to an existing user (order owner). Hibernate's native {@link CreationTimestamp @CreationTimestamp}
     * isn't leveraged either because it doesn't support {@link Instant} fields.
     */
    private Instant createdDate;

    /**
     * Should not be declared with <code>@NotNull</code> because it's assigned automatically upon entity validation.
     */
    @Embedded
    @AttributeOverride(name = "userId", column = @Column(name = "created_by_user_id"))
    @AttributeOverride(name = "traceId", column = @Column(name = "created_by_trace_id"))
    private AuditDetails createdBy;

    /**
     * Date of last modification. By Spring Data convention, it's set on creation too, but the subsecond part differs.
     * <p>
     * WARNING: Spring's {@link LastModifiedDate @LastModifiedDate} and Hibernate's
     * {@link UpdateTimestamp @UpdateTimestamp} not used for the same reason as described for
     * {@link #getCreatedDate()}.
     */
    private Instant lastModifiedDate;

    /**
     * Nullable because modification is not the same as creation.
     */
    @Embedded
    @AttributeOverride(name = "userId", column = @Column(name = "last_modified_by_user_id"))
    @AttributeOverride(name = "traceId", column = @Column(name = "last_modified_by_trace_id"))
    private AuditDetails lastModifiedBy;

    /**
     * Returns {@link AuditDetails#getUserId()} or {@code null} if unknown.
     */
    @Nullable
    public String getCreatedByUserId() {
        return (createdBy == null)
                ? null
                : createdBy.getUserId();
    }

    /**
     * Returns {@link AuditDetails#getTraceId()} or {@code null} if unknown.
     */
    @Nullable
    public String getCreatedByTraceId() {
        return (createdBy == null)
                ? null
                : createdBy.getTraceId();
    }

    /**
     * Returns {@link AuditDetails#getUserId()} or {@code null} if no modifications occurred. In JPA, creation is
     * considered modification.
     *
     * @see EnableJpaAuditing#modifyOnCreate()
     */
    @Nullable
    public String getLastModifiedByUserId() {
        return (lastModifiedBy == null)
                ? null
                : lastModifiedBy.getUserId();
    }

    /**
     * Returns {@link AuditDetails#getTraceId()} or {@code null} if no modifications occurred. In JPA, creation is
     * considered modification.
     *
     * @see EnableJpaAuditing#modifyOnCreate()
     */
    @Nullable
    public String getLastModifiedByTraceId() {
        return (lastModifiedBy == null)
                ? null
                : lastModifiedBy.getTraceId();
    }

    /**
     * Returns {@code true} if {@link #getId()} returns {@code null}. This behavior can be overridden by
     * {@link #enforceNew(Boolean)}.
     *
     * @return {@code true} if this entity should be treated as new for Hibernate
     */
    @Override
    public boolean isNew() {
        if (isNew != null) {
            return isNew;
        }

        return getId() == null;
    }

    /**
     * Manually sets the entity's new state flag. Use with caution, as this overrides automatic state management.
     *
     * @param isNew {@code true} if entity should be considered new, {@code false} otherwise, or {@code null} to apply
     *              default {@link #isNew()} behavior (checking for non-null {@link #getId()})
     */
    public void enforceNew(Boolean isNew) {
        this.isNew = isNew;
    }

    /**
     * Called by Hibernate when it has decided to insert a new entity in DB. Method assigns both creation-related and
     * modification-related details - for compatibility with Spring's {@link EnableJpaAuditing#modifyOnCreate()}.
     */
    @PrePersist
    private void assignAuditPropertiesBeforeInsert() {
        createdDate = lastModifiedDate = Instant.now();
        consumeAuditDetails(this::setCreatedBy, this::setLastModifiedBy);
    }

    /**
     * Called by Hibernate when it has decided to update an entity in DB (i.e. some persistent properties have changed
     * in memory). Method assigns {@link #getLastModifiedDate()} and {@link #getLastModifiedBy()}.
     */
    @PreUpdate
    private void assignAuditPropertiesBeforeUpdate() {
        lastModifiedDate = Instant.now();
        consumeAuditDetails(this::setLastModifiedBy);
    }

    /**
     * If current user principal is {@link UserDetails}, derives {@link AuditDetails} out of it and calls the given
     * consumers on it.
     *
     * @param consumers consumers to call
     */
    @SafeVarargs
    private void consumeAuditDetails(Consumer<AuditDetails>... consumers) {
        Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                // need UserDetails
                .filter(UserDetails.class::isInstance)
                .map(UserDetails.class::cast)
                .map(AuditDetails::new)
                // call consumers
                .ifPresent(auditDetails -> {
                    for (var consumer : consumers) {
                        consumer.accept(auditDetails);
                    }
                });
    }

}
