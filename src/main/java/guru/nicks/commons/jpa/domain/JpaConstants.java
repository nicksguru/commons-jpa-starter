package guru.nicks.commons.jpa.domain;

import jakarta.persistence.OrderColumn;
import lombok.experimental.UtilityClass;
import org.hibernate.annotations.BatchSize;

/**
 * Handy constants for JPA entity declarations.
 */
@UtilityClass
public class JpaConstants {

    /**
     * For use in {@link OrderColumn#name()}.
     */
    public static final String PERSISTENT_LIST_ORDER_COLUMN = "offsetInList";

    /**
     * Recommented value for {@link BatchSize#size()} to mitigate the N+1 problem when dealing with lazy collections.
     */
    public static final int ONE_TO_MANY_BATCH_SIZE = 10;

    /**
     * Recommended value for <b>internal</b> paginated queries.
     */
    public static final int INTERNAL_PAGE_SIZE = 500;

}
