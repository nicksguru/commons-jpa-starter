package guru.nicks.commons.jpa.it.domain;

import guru.nicks.commons.jpa.domain.AuditableEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Plain test entity NOT extending any Enhanced interface: used to verify that stock repositories keep working in a
 * context where {@code EnhancedJpaRepositoryFactoryBean} is installed, and as the target of a lazy association from
 * {@link TestDocument}.
 */
@Entity
@Table(name = "test_author")
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class TestAuthor extends AuditableEntity<String> {

    @Id
    @Getter(onMethod_ = @Override)
    private String id;

    private String name;

}
