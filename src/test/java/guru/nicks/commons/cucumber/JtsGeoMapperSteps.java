package guru.nicks.commons.cucumber;

import guru.nicks.commons.jpa.mapper.JtsGeoMapper;
import guru.nicks.commons.rest.v1.dto.GeoPointDto;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JtsGeoMapperSteps {

    @Mock
    private GeometryFactory geoFactory;
    @InjectMocks
    private JtsGeoMapper jtsGeoMapper;
    private AutoCloseable closeableMocks;

    private GeometryFactory geometryFactory;
    private Point point;
    private GeoPointDto geoPointDto;
    private GeoPointDto resultDto;
    private Point resultPoint;

    @Before
    public void beforeEachScenario() {
        closeableMocks = MockitoAnnotations.openMocks(this);
        geometryFactory = new GeometryFactory();
    }

    @After
    public void afterEachScenario() throws Exception {
        closeableMocks.close();
    }

    @Given("a JTS Point with coordinates x: {double} and y: {double}")
    public void aJtsPointWithCoordinatesXAndY(double x, double y) {
        point = createPoint(x, y);
    }

    @Given("a null JTS Point")
    public void aNullJtsPoint() {
        point = null;
    }

    @When("the Point is converted to GeoPointDto")
    public void thePointIsConvertedToGeoPointDto() {
        resultDto = jtsGeoMapper.toGeoDto(point);
    }

    @Then("the GeoPointDto should have longitude {double} and latitude {double}")
    public void theGeoPointDtoShouldHaveLongitudeAndLatitude(double lon, double lat) {
        assertThat(resultDto)
                .as("result dto")
                .isNotNull();
        assertThat(resultDto.lon())
                .as("longitude")
                .isEqualTo(lon);
        assertThat(resultDto.lat())
                .as("latitude")
                .isEqualTo(lat);
    }

    @Given("a GeoPointDto with longitude {double} and latitude {double}")
    public void aGeoPointDtoWithLongitudeAndLatitude(double lon, double lat) {
        geoPointDto = new GeoPointDto(lat, lon);
    }

    @Given("a null GeoPointDto")
    public void aNullGeoPointDto() {
        geoPointDto = null;
    }

    @When("the GeoPointDto is converted to Point")
    public void theGeoPointDtoIsConvertedToPoint() {
        // Mock the GeometryFactory to return a Point when createPoint is called
        if (geoPointDto != null) {
            var expectedPoint = createPoint(geoPointDto.lon(), geoPointDto.lat());

            when(geoFactory.createPoint(any(Coordinate.class)))
                    .thenReturn(expectedPoint);
        }

        resultPoint = jtsGeoMapper.fromGeoDto(geoPointDto);
    }

    @Then("the Point should have x coordinate {double} and y coordinate {double}")
    public void thePointShouldHaveXCoordinateAndYCoordinate(double x, double y) {
        assertThat(resultPoint)
                .as("result point")
                .isNotNull();
        assertThat(resultPoint.getX())
                .as("x coordinate")
                .isEqualTo(x);
        assertThat(resultPoint.getY())
                .as("y coordinate")
                .isEqualTo(y);

        // Verify that createPoint was called with the correct coordinates
        verify(geoFactory).createPoint(new Coordinate(x, y));
    }

    @Then("the geo conversion result should be null")
    public void theGeoConversionResultShouldBeNull() {
        if (resultDto != null) {
            assertThat(resultDto)
                    .as("result dto")
                    .isNull();
        } else if (resultPoint != null) {
            assertThat(resultPoint)
                    .as("result point")
                    .isNull();
        }
    }

    /**
     * Helper method to create a {@link Point} with the given coordinates.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @return a new point
     */
    private Point createPoint(double x, double y) {
        return geometryFactory.createPoint(new Coordinate(x, y));
    }

}
