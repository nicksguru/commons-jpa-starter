@db @geo #@disabled
Feature: JTS Geo Mapper
  Maps between JTS Point objects and GeoPointDto objects

  Scenario Outline: Converting JTS Point to GeoPointDto
    Given a JTS Point with coordinates x: <x> and y: <y>
    When the Point is converted to GeoPointDto
    Then the GeoPointDto should have longitude <lon> and latitude <lat>
    Examples:
      | x       | y     | lon     | lat   |
      | 10.5    | 20.75 | 10.5    | 20.75 |
      | -122.42 | 37.77 | -122.42 | 37.77 |
      | 0.0     | 0.0   | 0.0     | 0.0   |
      | 180.0   | 90.0  | 180.0   | 90.0  |

  Scenario: Converting null Point to GeoPointDto
    Given a null JTS Point
    When the Point is converted to GeoPointDto
    Then the geo conversion result should be null

  Scenario Outline: Converting GeoPointDto to JTS Point
    Given a GeoPointDto with longitude <lon> and latitude <lat>
    When the GeoPointDto is converted to Point
    Then the Point should have x coordinate <x> and y coordinate <y>
    Examples:
      | lon     | lat   | x       | y     |
      | 10.5    | 20.75 | 10.5    | 20.75 |
      | -122.42 | 37.77 | -122.42 | 37.77 |
      | 0.0     | 0.0   | 0.0     | 0.0   |
      | 180.0   | 90.0  | 180.0   | 90.0  |

  Scenario: Converting null GeoPointDto to Point
    Given a null GeoPointDto
    When the GeoPointDto is converted to Point
    Then the geo conversion result should be null
