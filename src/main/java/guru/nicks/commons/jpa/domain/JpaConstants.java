package guru.nicks.commons.jpa.domain;

import jakarta.persistence.OrderColumn;
import lombok.experimental.UtilityClass;
import org.hibernate.annotations.BatchSize;

/**
 * Handy constants for JPA entity declarations.
 * <p>
 * For making all lazy associations use batch fetching by default
 * ({@code SELECT * FROM child_table WHERE parent_id IN (1, 2, 3)} without using {@link BatchSize} explicitly, set
 * {@code spring.jpa.properties.hibernate .default_batch_fetch_size} in application properties to, for example, 50.
 */
@UtilityClass
public class JpaConstants {

    /**
     * For use in {@link OrderColumn#name()} to make {@code List<SomeEntity>} maintain order.
     */
    public static final String PERSISTENT_LIST_ORDER_COLUMN = "offsetInList";

    /**
     * Recommended value for <b>internal</b> (not UI-facing) paginated queries.
     */
    public static final int INTERNAL_PAGE_SIZE = 500;

}
