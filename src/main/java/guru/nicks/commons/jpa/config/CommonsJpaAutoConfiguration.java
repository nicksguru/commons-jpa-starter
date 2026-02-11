package guru.nicks.commons.jpa.config;

import guru.nicks.commons.jpa.GeometryFactoryQualifier;
import guru.nicks.commons.jpa.domain.GeometryFactoryType;
import guru.nicks.commons.jpa.domain.MyJpaProperties;
import guru.nicks.commons.jpa.repository.EnhancedJpaRepository;
import guru.nicks.commons.jpa.repository.EnhancedJpaSearchRepository;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.geolatte.geom.codec.Wkb;
import org.geolatte.geom.crs.CoordinateReferenceSystems;
import org.hibernate.annotations.JdbcType;
import org.hibernate.spatial.dialect.postgis.PGGeographyJdbcType;
import org.hibernate.spatial.dialect.postgis.PGGeometryJdbcType;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * To inherit your JPA repositories from {@link EnhancedJpaRepository} or {@link EnhancedJpaSearchRepository}, add the
 * following to your application configuration:
 * <pre>
 * &#64;EnableJpaRepositories(
 *     basePackages = "com.yourcompany",
 *     repositoryFactoryBeanClass = EnhancedJpaRepositoryFactoryBean.class
 * )
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
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
    @ConditionalOnMissingBean(Wkb.Dialect.class)
    @Bean
    public Wkb.Dialect wkbDialect() {
        log.debug("Building {} bean", Wkb.Dialect.class.getSimpleName());
        return Wkb.Dialect.POSTGIS_EWKB_2;
    }

    /**
     * Other DB engines may have own transaction templates. This bean is declared as {@link Primary @Primary} so that
     * it's used by default. Other beans can be reached as {@code @Qualifier("someOtherTransactionTemplateBean")}.
     *
     * @param transactionManager JPA transaction manager
     * @return JPA transaction template
     */
    @ConditionalOnMissingBean(TransactionTemplate.class)
    @Bean
    @Primary
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        log.debug("Building {} bean", TransactionTemplate.class.getSimpleName());
        return new TransactionTemplate(transactionManager);
    }

    /**
     * Other DB engines may have own transaction managers. This bean is declared as {@link Primary @Primary} so that it
     * is used by default. Other beans can be reached as {@code @Transactional("someOtherTransactionManagerBean")}.
     *
     * @param entityManagerFactory JPA entity manager factory
     * @return JPA transaction manager
     */
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        log.debug("Building {} bean", PlatformTransactionManager.class.getSimpleName());
        var transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        return transactionManager;
    }

}
