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
 * Entity with a mutable supplier list, ngram configuration and maximum search data length - the fixed settings of
 * {@link ConfigurableTestEntity} cannot express the tiny caps and toggles the chunk characterization scenarios need.
 */
public class ChunkingTestEntity extends FullTextSearchAwareEntity<String> {

    private final List<Supplier<String>> suppliers = new ArrayList<>();

    @ToString.Exclude
    private String fullTextSearchData;

    private String id;

    private NgramUtilsConfig ngramUtilsConfig = NgramUtilsConfig.DEFAULT;

    private int maxFullTextSearchDataLength = JpaInference.DEFAULT_SQL_DIALECT.getMaxFullTextSearchDataLength();

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
        return maxFullTextSearchDataLength;
    }

    /**
     * @param maxFullTextSearchDataLength maximum search data length to simulate a dialect limit
     */
    public void setMaxFullTextSearchDataLength(int maxFullTextSearchDataLength) {
        this.maxFullTextSearchDataLength = maxFullTextSearchDataLength;
    }

    @Nonnull
    @Override
    public NgramUtilsConfig getNgramUtilsConfig() {
        return ngramUtilsConfig;
    }

    /**
     * @param ngramUtilsConfig ngram configuration the entity rebuild must use
     */
    public void setNgramUtilsConfig(NgramUtilsConfig ngramUtilsConfig) {
        this.ngramUtilsConfig = ngramUtilsConfig;
    }

    @Nonnull
    @Override
    @JsonIgnore
    @Transient
    protected Collection<Supplier<String>> getFullTextSearchDataSuppliers() {
        return suppliers;
    }
}
