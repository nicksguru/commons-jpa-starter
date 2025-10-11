package guru.nicks.jpa.mapper;

import guru.nicks.jpa.GeometryFactoryQualifier;
import guru.nicks.jpa.domain.GeometryFactoryType;
import guru.nicks.mapper.DefaultMapStructConfig;
import guru.nicks.rest.v1.dto.GeoPointDto;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(config = DefaultMapStructConfig.class)
public class JtsGeoMapper {

    @GeometryFactoryQualifier(GeometryFactoryType.GEO)
    @Autowired
    private GeometryFactory geoFactory;

    public GeoPointDto toGeoDto(Point source) {
        if (source == null) {
            return null;
        }

        return GeoPointDto.builder()
                .lat(source.getY())
                .lon(source.getX())
                .build();
    }

    public Point fromGeoDto(GeoPointDto dto) {
        if (dto == null) {
            return null;
        }

        return geoFactory.createPoint(new Coordinate(dto.getLon(), dto.getLat()));
    }

}
