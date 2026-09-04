package guru.nicks.commons.jpa.utils;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.Strings;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

/**
 * Utilities for inspecting {@link DataIntegrityViolationException}s raised by the persistence layer.
 */
@UtilityClass
public class DataIntegrityViolationUtils {

    /**
     * Checks whether the given exception was caused by a violation of a DB constraint whose name contains the given
     * fragment (case-insensitively). The driver-reported violation is wrapped by the persistence layer into multiple
     * exception layers, and the constraint name may surface in the message of any of them (commonly in a
     * {@link SQLException} or a Hibernate {@link ConstraintViolationException} cause). So the entire cause chain,
     * including the exception itself, is inspected.
     * <p>
     * The fragment must be rename-stable when code templates are involved: e.g. {@code "_email"} matches any
     * {@code idx_<table>_email} unique index regardless of the table name.
     *
     * @param exception              exception raised by a failed INSERT/UPDATE
     * @param constraintNameFragment constraint name fragment to look for
     * @return whether any exception in the cause chain mentions the given constraint name fragment
     */
    public static boolean isViolationOf(DataIntegrityViolationException exception, String constraintNameFragment) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (Strings.CI.contains(cause.getMessage(), constraintNameFragment)) {
                return true;
            }
        }

        return false;
    }

}
