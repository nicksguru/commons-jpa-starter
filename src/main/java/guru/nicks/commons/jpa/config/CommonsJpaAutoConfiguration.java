package guru.nicks.commons.jpa.config;

import guru.nicks.commons.jpa.GeometryFactoryQualifier;
import guru.nicks.commons.jpa.domain.GeometryFactoryType;
import guru.nicks.commons.jpa.domain.MyJpaProperties;
import guru.nicks.commons.jpa.mapper.AuditDetailsMapper;
import guru.nicks.commons.jpa.mapper.DataIntegrityViolationExceptionConverter;
import guru.nicks.commons.jpa.mapper.ObjectOptimisticLockingFailureExceptionConverter;
import guru.nicks.commons.jpa.mapper.OptimisticLockExceptionConverter;
import guru.nicks.commons.jpa.repository.EnhancedJpaRepository;
import guru.nicks.commons.jpa.repository.EnhancedJpaSearchRepository;

import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.geolatte.geom.codec.Wkb;
import org.geolatte.geom.crs.CoordinateReferenceSystems;
import org.hibernate.annotations.JdbcType;
import org.hibernate.spatial.dialect.postgis.PGGeographyJdbcType;
import org.hibernate.spatial.dialect.postgis.PGGeometryJdbcType;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * /** Transaction managers and transaction templates aren't created automatically. This is intentional: some projects
 * may combine JPA transactions with Mongo ones (which requires one of the beans to be primary), some may not.
 * <p>
 * To inherit your JPA repositories from {@link EnhancedJpaRepository} or {@link EnhancedJpaSearchRepository}, add the
 * following to your application configuration:
 * <pre>
 * &#64;EnableJpaRepositories(
 *     basePackages = "com.yourcompany",
 *     repositoryFactoryBeanClass =EnhancedJpaRepositoryFactoryBean.class
 * )
 * </pre>
 */
@AutoConfiguration
@EnableConfigurationProperties(MyJpaProperties.class)
@EnableTransactionManagement
@EnableJpaAuditing
@Slf4j
public class CommonsJpaAutoConfiguration {

    /**
     * Creates a factory for geo (spherical) geometry. It specifies the default Coordinate Reference System (CRS) to be
     * used, namely WGS84 (longitude is 'x', latitude is 'y'). This is the only CRS allowed for lat/lon in Postgres;
     * attempts to use others result in <i>ERROR: Only lon/lat coordinate systems are supported in geography.</i>
     * <p>
     * To inject, use {@link GeometryFactoryQualifier @GeometryFactoryQualifier(GeometryFactoryType.GEO)} +
     * {@link Autowired @Autowired} (Lombok doesn't copy custom annotations to auto-generated constructors).
     *
     * @return bean
     */
    @GeometryFactoryQualifier(GeometryFactoryType.GEO)
    @Bean
    public GeometryFactory geoGeometryFactory() {
        log.debug("Building {} ({}) bean", GeometryFactory.class.getSimpleName(), GeometryFactoryType.GEO.name());
        return new GeometryFactory(new PrecisionModel(), CoordinateReferenceSystems.WGS84.getCrsId().getCode());
    }

    /**
     * Creates a factory for planar geometry.
     * <p>
     * To inject, use {@link GeometryFactoryQualifier @GeometryFactoryQualifier(GeometryFactoryType.PLANAR)} +
     * {@link Autowired @Autowired} (Lombok doesn't copy custom annotations to auto-generated constructors).
     *
     * @return bean
     */
    @GeometryFactoryQualifier(GeometryFactoryType.PLANAR)
    @Bean
    public GeometryFactory planarGeometryFactory() {
        log.debug("Building {} ({}) bean", GeometryFactory.class.getSimpleName(), GeometryFactoryType.PLANAR.name());
        return new GeometryFactory();
    }

    /**
     * Needed for {@link PGGeographyJdbcType}/{@link PGGeometryJdbcType} i.e. for {@link JdbcType @JdbcType} for
     * geography (spherical) / geometry (planar) {@link Point} columns.
     *
     * @return bean
     */
    @ConditionalOnMissingBean
    @Bean
    public Wkb.Dialect wkbDialect() {
        log.debug("Building {} bean", Wkb.Dialect.class.getSimpleName());
        return Wkb.Dialect.POSTGIS_EWKB_2;
    }

    /**
     * Exception converter for {@link OptimisticLockException}.
     *
     * @return bean
     */
    @ConditionalOnMissingBean
    @Bean
    public OptimisticLockExceptionConverter optimisticLockExceptionConverter() {
        log.debug("Building {} bean", OptimisticLockExceptionConverter.class.getSimpleName());
        return new OptimisticLockExceptionConverter();
    }

    /**
     * Exception converter for {@link DataIntegrityViolationException}.
     *
     * @return bean
     */
    @ConditionalOnMissingBean
    @Bean
    public DataIntegrityViolationExceptionConverter dataIntegrityViolationExceptionConverter() {
        log.debug("Building {} bean", DataIntegrityViolationExceptionConverter.class.getSimpleName());
        return new DataIntegrityViolationExceptionConverter();
    }

    /**
     * Exception converter for {@link ObjectOptimisticLockingFailureException}.
     *
     * @return bean
     */
    @ConditionalOnMissingBean
    @Bean
    public ObjectOptimisticLockingFailureExceptionConverter objectOptimisticLockingFailureExceptionConverter() {
        log.debug("Building {} bean", ObjectOptimisticLockingFailureExceptionConverter.class.getSimpleName());
        return new ObjectOptimisticLockingFailureExceptionConverter();
    }

    /**
     * Mapper configuration for external MapStruct mappers. Such mappers reside in external libraries/packages which are
     * not noticed by Spring component scanning.
     */
    @ConditionalOnMissingBean
    @Bean
    public AuditDetailsMapper auditDetailsMapper() {
        return Mappers.getMapper(AuditDetailsMapper.class);
    }

}
