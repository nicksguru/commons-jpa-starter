package guru.nicks.commons.jpa.it.repo;

import guru.nicks.commons.jpa.it.domain.TestDocument;
import guru.nicks.commons.jpa.it.domain.TestDocumentFilter;
import guru.nicks.commons.jpa.it.domain.TestDocumentNotFoundException;
import guru.nicks.commons.jpa.repository.EnhancedJpaSearchRepository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.core.types.dsl.PathBuilderFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import static guru.nicks.commons.jpa.domain.FullTextSearchAwareEntity.initSortCriteria;

/**
 * Test repository mimicking how real user repositories extend
 * {@link guru.nicks.commons.jpa.repository.EnhancedJpaSearchRepository}: the two mandatory methods are declared as
 * 'default' ones directly in the interface (required by the fail-fast check in the fragment implementation).
 */
public interface TestDocumentRepository extends EnhancedJpaSearchRepository<TestDocument, String,
        TestDocumentNotFoundException, TestDocumentFilter> {

    /**
     * QueryDSL entity path for building predicates in the default methods (test sources have no APT-generated
     * Q-classes, so the path is created dynamically).
     */
    PathBuilder<TestDocument> DOCUMENT_PATH = new PathBuilderFactory().create(TestDocument.class);

    /**
     * {@inheritDoc}
     */
    @Override
    default BooleanBuilder convertToSearchBuilder(TestDocumentFilter filter) {
        var builder = new BooleanBuilder();
        if (filter == null) {
            return builder;
        }

        andIfNotBlank(filter::name, builder,
                name -> DOCUMENT_PATH.getString(TestDocument.Fields.name).contains(name));
        andIfNotNull(filter::userId, builder,
                userId -> DOCUMENT_PATH.getString(TestDocument.Fields.userId).eq(userId));
        andIfNotBlank(filter::color, builder,
                color -> createJsonContainsPredicate("metadata", color));
        return builder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    default Page<TestDocument> findByFilter(TestDocumentFilter filter, Pageable pageable) {
        return findByFilter(filter,
                () -> filter == null ? null : filter.searchText(),
                initSortCriteria(filter == null ? null : filter.searchText(), pageable),
                DOCUMENT_PATH,
                () -> null);
    }

}
