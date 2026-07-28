package guru.nicks.commons.jpa;

import guru.nicks.commons.exception.user.EmailAlreadyExistsException;
import guru.nicks.commons.jpa.domain.EnhancedSqlDialect;
import guru.nicks.commons.utils.TransformUtils;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.boot.model.naming.Identifier;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Utility class for inferring JPA table and column names. There's also a {@link #withoutAutoFlushMode(Supplier)}
 * utility method.
 */
@RequiredArgsConstructor
@Slf4j
public class JpaInference {

    /**
     * @see #getSqlDialect()
     */
    public static final String SQL_DIALECT_PROPERTY_NAME = "app.database.dialect";

    /**
     * @see #getSqlDialect()
     */
    public static final EnhancedSqlDialect DEFAULT_SQL_DIALECT = EnhancedSqlDialect.POSTGRES;

    /**
     * Simple camelCase to snake_case conversion (matches Hibernate 6 default behavior).
     * <p>
     * Need both {@code $1} (the lowercase letter) and {@code $2} (the uppercase letter) in the replacement string.
     */
    private static final Pattern CAMEL_CASE_TO_SNAKE_CASE = Pattern.compile("([a-z])([A-Z])");

    // DI
    private final EntityManager entityManager;
    private final Environment environment;

    @Getter
    private EnhancedSqlDialect sqlDialect;

    @PostConstruct
    private void init() {
        sqlDialect = environment.getProperty(SQL_DIALECT_PROPERTY_NAME, EnhancedSqlDialect.class, DEFAULT_SQL_DIALECT);
        log.info("Using SQL dialect {}", sqlDialect);
    }

    /**
     * Gets the table name from JPA annotations or falls back to {@link #toEscapedSnakeCaseColumnName(String)}.
     *
     * @param entityClass the entity class
     * @return the table name
     */
    public String getTableName(Class<?> entityClass) {
        String tableName;
        Table tableAnnotation = entityClass.getAnnotation(Table.class);

        // explicit table name set
        if ((tableAnnotation != null) && !tableAnnotation.name().isBlank()) {
            tableName = tableAnnotation.name();
            // honor SQL quoting
            Identifier identifier = Identifier.toIdentifier(tableName);
            tableName = identifier.render();
        }
        // infer table name from class name using JPA metamodel
        else {
            Metamodel metamodel = entityManager.getMetamodel();
            EntityType<?> entityType = metamodel.entity(entityClass);
            tableName = toEscapedSnakeCaseColumnName(entityType.getName());
        }

        return tableName;
    }

    /**
     * Gets the column name from JPA annotations or falls back to {@link #toEscapedSnakeCaseColumnName(String)}.
     * Searches for {@link Column @Column} in the given class and its superclasses (excluding interfaces - they can't
     * have fields).
     *
     * @param entityClass entity class
     * @param fieldName   field name
     * @return the column name
     */
    public String getColumnName(Class<?> entityClass, String fieldName) {
        for (Class<?> clazz = entityClass; clazz != null; clazz = clazz.getSuperclass()) {
            Field field;

            try {
                field = clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                // field not in this class, try superclass
                continue;
            }

            Column columnAnnotation = field.getAnnotation(Column.class);

            if ((columnAnnotation != null) && !columnAnnotation.name().isBlank()) {
                Identifier identifier = Identifier.toIdentifier(columnAnnotation.name());
                return identifier.render();
            }
        }

        // fallback
        return toEscapedSnakeCaseColumnName(fieldName);
    }

    /**
     * Converts a camelCase string to snake_case and escapes it with backticks if necessary.
     *
     * @param camelCase camelCase string
     * @return snake_case string
     */
    public String toEscapedSnakeCaseColumnName(String camelCase) {
        String snakeCase = CAMEL_CASE_TO_SNAKE_CASE.matcher(camelCase).replaceAll("$1_$2").toLowerCase();
        Identifier identifier = Identifier.toIdentifier(snakeCase);
        return identifier.render();
    }

    /**
     * Executes a given code with the Hibernate session's flush mode temporarily set to {@link FlushModeType#COMMIT}
     * (vs. the default {@link FlushMode#AUTO}). This is necessary for preventing unintended flushes before a specific
     * operation.
     * <p>
     * For example, let a new entity have an email address set, and there's a unique DB constraint for it. If the entity
     * is saved, a general-purpose {@link DataIntegrityViolationException} will be raised. But we should display a
     * meaningful 'Email already exists' message, which requires a {@link EmailAlreadyExistsException} to be raised. The
     * solution is to find an entity having the same email address <b>without saving the new entity</b>.
     *
     * @param supplier code to be executed
     * @param <T>      supplier result type
     * @return what the supplier returns
     * @see TransformUtils#toSupplier(Runnable)
     */
    public <T> T withoutAutoFlushMode(Supplier<T> supplier) {
        Session session = entityManager.unwrap(Session.class);
        FlushModeType oldFlushMode = session.getFlushMode();

        try {
            session.setFlushMode(FlushModeType.COMMIT);
            return supplier.get();
        } finally {
            session.setFlushMode(oldFlushMode);
        }
    }

}
