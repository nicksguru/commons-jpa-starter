package guru.nicks.commons.jpa.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Custom properties - Spring isn't aware of them. The idea is construct a connection URL out of them via placeholders.
 */
@ConfigurationProperties(prefix = "spring.datasource.my")
@Validated
// immutability
@Value
@NonFinal // CGLIB creates a subclass to bind property values (nested classes don't need this)
@Builder(toBuilder = true)
public class MyJpaProperties {

    @NotBlank
    String host;

    @NotNull
    @Min(1)
    Integer port;

    @NotBlank
    String database;

    String options;

}
