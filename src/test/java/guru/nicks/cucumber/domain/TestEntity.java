package guru.nicks.cucumber.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.springframework.data.domain.Persistable;

@NoArgsConstructor
@Getter
@Setter
@FieldNameConstants
//
@Jacksonized
@SuperBuilder
// no @EqualsAndHashCode because entities are supposed to be distinguished by their ID
@ToString(callSuper = true)
public class TestEntity implements Persistable<String> {

    @Getter(onMethod_ = @Override)
    private String id;

    private String name;

    private String field1;
    private String field2;
    private String field3;

    @Override
    public boolean isNew() {
        return (id == null);
    }

}
