package guru.nicks.commons.cucumber.world;

import guru.nicks.commons.cucumber.domain.TestEntity;

import io.cucumber.spring.ScenarioScope;
import lombok.Data;
import org.hibernate.generator.EventType;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
@ScenarioScope
@Data
public class JpaWorld {

    private Collection<EventType> generatorEventTypes;

    private TestEntity entity;
    private String generatedId;

    private String searchText;

}
