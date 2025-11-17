package guru.nicks.commons.jpa.domain;

import guru.nicks.commons.log.domain.LogContext;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

/**
 * Audit details for JPA entities.
 */
@Embeddable
@NoArgsConstructor
@Getter
@Setter
@FieldNameConstants
@ToString
@Slf4j
public class AuditDetails implements Serializable {

    /**
     * Doesn't always correspond to an existing ID because the user may have been deleted. For this reason, it's not
     * recommended to create a foreign key constraint in DB.
     */
    private String userId;

    /**
     * {@link LogContext#TRACE_ID}
     */
    private String traceId;

    /**
     * Assigns:
     * <ul>
     *     <li>{@link #getTraceId() traceId} from {@link LogContext#TRACE_ID}</li>
     *     <li>{@link #getUserId() userId} from the argument using reflection ({@code id} property)</li>
     * </ul>
     * <p>
     * {@link UserDetails} has no {@code id} property, but its subclasses may have. Exceptions (no such property /
     * error reading property / etc.) are logged and ignored.
     *
     * @param userDetails user details
     */
    public AuditDetails(UserDetails userDetails) {
        traceId = LogContext.TRACE_ID.find().orElse(null);

        try {
            userId = Objects.toString(PropertyUtils.getProperty(userDetails, "id"), null);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            log.error("Failed to retrieve user ID (as 'id' property) from [{}], ignoring: {}",
                    userDetails.getClass().getName(), e.getMessage(), e);
        }
    }

}
