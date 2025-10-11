package guru.nicks.jpa.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.jackson.Jacksonized;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Custom properties - Spring isn't aware of them. The idea is construct a connection URL out of them via placeholders.
 */
@ConfigurationProperties(prefix = "spring.datasource.my", ignoreUnknownFields = false)
@Validated
// immutability
@Value
@NonFinal // needed for CGLIB to bind property values (nested classes don't need this)
@Jacksonized
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
