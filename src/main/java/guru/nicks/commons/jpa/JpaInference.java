package guru.nicks.commons.jpa;

import guru.nicks.commons.jpa.domain.EnhancedSqlDialect;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.boot.model.naming.Identifier;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.regex.Pattern;

/**
 * Utility class for inferring JPA table and column names. Requires {@link EntityManager} for JPA Metamodel.
 */
@Component
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

    private EnhancedSqlDialect sqlDialect;

    @PostConstruct
    private void init() {
        sqlDialect = environment.getProperty(SQL_DIALECT_PROPERTY_NAME, EnhancedSqlDialect.class, DEFAULT_SQL_DIALECT);
        log.info("Using SQL dialect {}", sqlDialect);
    }

    /**
     * Returns the SQL dialect configured. Falls back on {@link #DEFAULT_SQL_DIALECT} if
     * {@link #SQL_DIALECT_PROPERTY_NAME} property is missing from {@link Environment}.
     *
     * @return SQL dialect
     */
    public EnhancedSqlDialect getSqlDialect() {
        return sqlDialect;
    }

    /**
     * Gets the table name from JPA annotations or falls back to {@link #toEscapedSnakeCaseColumnName(String)}.
     *
     * @param entityClass the entity class
     * @return the table name
     */
    public String getTableName(Class<?> entityClass) {
        String tableName;

        jakarta.persistence.Table tableAnnotation = entityClass.getAnnotation(jakarta.persistence.Table.class);
        if ((tableAnnotation != null) && !tableAnnotation.name().isBlank()) {
            tableName = tableAnnotation.name();
            // honor SQL quoting
            Identifier identifier = Identifier.toIdentifier(tableName);
            tableName = identifier.render();
        }
        // fallback to JPA Metamodel
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

}
