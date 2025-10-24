package guru.nicks.jpa.mapper;

import guru.nicks.jpa.domain.AuditableEntity;
import guru.nicks.mapper.DefaultMapStructConfig;
import guru.nicks.rest.v1.dto.AuditDetailsDto;

import org.mapstruct.Mapper;

@Mapper(config = DefaultMapStructConfig.class)
public interface AuditDetailsMapper {

    AuditDetailsDto toDto(AuditableEntity<?> source);

}
