package guru.nicks.commons.jpa.it.repo;

import guru.nicks.commons.jpa.it.domain.WeightedTestEntity;
import guru.nicks.commons.jpa.repository.EnhancedJpaSearchRepository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.core.types.dsl.PathBuilderFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import static guru.nicks.commons.jpa.domain.FullTextSearchAwareEntity.initSortCriteria;

/**
 * Test repository over {@link WeightedTestEntity} (the {@code isWeightedTsvector()} entity) for the weighted-tsvector
 * ranking scenario - mirrors {@link TestDocumentRepository} with a null filter and the search text passed directly.
 */
public interface WeightedTestDocumentRepository extends EnhancedJpaSearchRepository<WeightedTestEntity, String,
        RuntimeException, Void> {

    /**
     * QueryDSL entity path for building predicates in the default methods (test sources have no APT-generated
     * Q-classes, so the path is created dynamically).
     */
    PathBuilder<WeightedTestEntity> WEIGHTED_DOCUMENT_PATH = new PathBuilderFactory().create(
            WeightedTestEntity.class);

    /**
     * {@inheritDoc}
     */
    @Override
    default BooleanBuilder convertToSearchBuilder(Void filter) {
        return new BooleanBuilder();
    }

    /**
     * {@inheritDoc} - no filter conditions, the weighted scenario searches by FTS text only.
     */
    @Override
    @Transactional(readOnly = true)
    default Page<WeightedTestEntity> findByFilter(Void filter, Pageable pageable) {
        return Page.empty(pageable);
    }

    /**
     * Searches weighted documents by full-text search text, sorted by search rank (desc).
     *
     * @param searchText full-text search text, may be deliberately misspelled
     * @param pageable   pagination/sorting request
     * @return page of matching documents, best match first
     */
    @Transactional(readOnly = true)
    default Page<WeightedTestEntity> search(String searchText, Pageable pageable) {
        return findByFilter(null,
                () -> searchText,
                initSortCriteria(searchText, pageable),
                WEIGHTED_DOCUMENT_PATH,
                () -> null);
    }

}
