package guru.nicks.commons.jpa.mapper;

import guru.nicks.commons.jpa.domain.AuditableEntity;
import guru.nicks.commons.mapper.DefaultMapStructConfig;
import guru.nicks.commons.rest.v1.dto.AuditDetailsDto;

import org.mapstruct.Mapper;

@Mapper(config = DefaultMapStructConfig.class)
public interface AuditDetailsMapper {

    AuditDetailsDto toDto(AuditableEntity<?> source);

}
