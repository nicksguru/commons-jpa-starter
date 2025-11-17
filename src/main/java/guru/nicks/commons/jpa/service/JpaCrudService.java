package guru.nicks.commons.jpa.service;

import guru.nicks.commons.jpa.repository.EnhancedJpaRepository;
import guru.nicks.commons.service.CrudService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * {@link SimpleJpaRepository} (Spring Data's implementation of {@link JpaRepository}) has
 * {@code @Transactional(readOnly=true)} on the class level and {@code @Transactional} on methods that change something,
 * which means everything is transactional.
 */
@SuppressWarnings("java:S119")  // allow type names like 'ID'
public interface JpaCrudService<T extends Persistable<ID>, ID> extends CrudService<T, ID> {

    @Override
    default T save(T entity) {
        return getRepository().save(entity);
    }

    @Override
    default Iterable<T> saveAll(Iterable<T> entities) {
        return getRepository().saveAll(entities);
    }

    @Override
    default void delete(T entity) {
        getRepository().delete(entity);
    }

    @Override
    default void deleteById(ID id) {
        getRepository().deleteById(id);
    }

    @Override
    default void deleteAllById(Iterable<ID> ids) {
        getRepository().deleteAllById(ids);
    }

    @Override
    default boolean existsById(ID id) {
        return getRepository().existsById(id);
    }

    @Override
    default Optional<T> findById(ID id) {
        return getRepository().findById(id);
    }

    @Override
    default T getByIdOrThrow(ID id) {
        return getRepository().getByIdOrThrow(id);
    }

    @Override
    default List<T> findAllByIdPreserveOrder(Collection<ID> ids) {
        return getRepository().findAllByIdPreserveOrder(ids);
    }

    // no transaction because DB cursor is returned
    @Override
    default Stream<T> findAllAsStream() {
        return getRepository().findAllAsStream();
    }

    @Override
    default Page<T> findAll(Pageable pageable) {
        return getRepository().findAll(pageable);
    }

    /**
     * Returns repository which deals with {@code T} entities. Can't be just
     * {@link org.springframework.data.jpa.repository.JpaRepository} because
     * {@link EnhancedJpaRepository#findAllAsStream()} is needed.
     *
     * @return repository
     */
    EnhancedJpaRepository<T, ID, ?> getRepository();

}
