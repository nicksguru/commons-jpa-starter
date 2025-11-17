package guru.nicks.commons.jpa;

import guru.nicks.commons.jpa.domain.GeometryFactoryType;

import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For injecting {@link GeometryFactory} beans by {@link GeometryFactoryType} and not by bean names (which are not well
 * readable and may vary). Bean injection using custom qualifiers requires {@link Autowired @Autowired} annotation
 * because Lombok doesn't copy custom annotations to auto-generated constructors.
 */
@Qualifier
@Target({
        ElementType.FIELD, ElementType.PARAMETER,
        // for @Component
        ElementType.TYPE,
        // for @Bean
        ElementType.METHOD
})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GeometryFactoryQualifier {

    GeometryFactoryType value();

}
