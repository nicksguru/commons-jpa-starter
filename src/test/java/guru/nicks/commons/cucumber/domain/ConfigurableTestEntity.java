package guru.nicks.commons.cucumber.domain;

import guru.nicks.commons.jpa.JpaInference;
import guru.nicks.commons.jpa.domain.FullTextSearchAwareEntity;
import guru.nicks.commons.utils.text.NgramUtilsConfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nonnull;
import jakarta.persistence.Transient;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Entity with a mutable supplier list, allowing {@code null} suppliers and arbitrary values to be interleaved - the
 * fixed immutable supplier list of {@link TestEntity} cannot express such configurations.
 */
public class ConfigurableTestEntity extends FullTextSearchAwareEntity<String> {

    private final List<Supplier<String>> suppliers = new ArrayList<>();

    @ToString.Exclude
    private String fullTextSearchData;

    private String id;

    @Override
    public String getId() {
        return id;
    }

    /**
     * Adds a supplier to the mutable supplier list.
     *
     * @param supplier supplier to add, may be {@code null}
     */
    public void addSupplier(Supplier<String> supplier) {
        suppliers.add(supplier);
    }

    @Override
    public String getFullTextSearchData() {
        return fullTextSearchData;
    }

    @Override
    public void setFullTextSearchData(String value) {
        fullTextSearchData = value;
    }

    @Override
    public int getMaxFullTextSearchDataLength() {
        return JpaInference.DEFAULT_SQL_DIALECT.getMaxFullTextSearchDataLength();
    }

    @Nonnull
    @Override
    public NgramUtilsConfig getNgramUtilsConfig() {
        return NgramUtilsConfig.DEFAULT;
    }

    @Nonnull
    @Override
    @JsonIgnore
    @Transient
    protected Collection<Supplier<String>> getFullTextSearchDataSuppliers() {
        return suppliers;
    }
}
