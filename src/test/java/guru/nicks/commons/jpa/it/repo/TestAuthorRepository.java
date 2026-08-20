package guru.nicks.commons.jpa.it.repo;

import guru.nicks.commons.jpa.it.domain.TestAuthor;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Plain stock repository NOT extending any Enhanced interface: pins that the hybrid fragment factory leaves the
 * default Spring Data repository path fully functional in the same application context.
 */
public interface TestAuthorRepository extends JpaRepository<TestAuthor, String> {
}
