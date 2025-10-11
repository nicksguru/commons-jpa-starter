package guru.nicks.jpa.domain;

import jakarta.persistence.OrderColumn;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JpaConstants {

    /**
     * For use in {@link OrderColumn#name()} to avoid typing the same value manually over and over.
     */
    public static final String PERSISTENT_LIST_ORDER_COLUMN = "offsetInList";

}
