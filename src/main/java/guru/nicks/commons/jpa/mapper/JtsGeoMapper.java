package guru.nicks.commons.jpa.mapper;

import guru.nicks.commons.jpa.GeometryFactoryQualifier;
import guru.nicks.commons.jpa.domain.GeometryFactoryType;
import guru.nicks.commons.mapper.DefaultMapStructConfig;
import guru.nicks.commons.rest.v1.dto.GeoPointDto;

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

        return new GeoPointDto(source.getY(), source.getX());
    }

    public Point fromGeoDto(GeoPointDto dto) {
        if (dto == null) {
            return null;
        }

        return geoFactory.createPoint(new Coordinate(dto.lon(), dto.lat()));
    }

}
