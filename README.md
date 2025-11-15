# [Nicks.Guru](https://nicks.guru) Commons JPA Starter

JPA starter.

## Versioning

The version format is inspired by [Scalver](https://scalver.org) and looks like `M.yymm.N` (in UTC timezone), where:

* `M`  - major version component, same as in [SemVer](https://semver.org) (starts from 0 and increments in case of backwards incompatibility)
* `yy` - year minus 2000 (25 in 2025, 100 in 2100)
* `mm` - month (zero-padded: 01 = Jan, 02 = Feb)
* `N`  - incremental build number, starts from 0 every month

## Usage

See full version history on
[Maven Central](https://central.sonatype.com/namespace/guru.nicks.commons), use as follows:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>guru.nicks.commons</groupId>
            <artifactId>bom</artifactId>
            <version>1.2511.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>guru.nicks.commons</groupId>
        <artifactId>jpa-starter</artifactId>
    </dependency>
</dependencies>
```

## Documentation

To browse the API documentation, click [here](https://nicks.guru/commons/commons-jpa-starter/apidocs).

This software adheres to the BDD (Behavior-Driven Development) approach. See module usage examples in Cucumber
test [scenarios](src/test/resources/cucumber/) and [steps](src/test/java/guru/nicks/cucumber/).

## Disclaimer

THIS CODE IS PROVIDED "AS IS" WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED
TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. USE AT YOUR OWN RISK.

Copyright © 2025 [nicks.guru](https://nicks.guru). All rights reserved.
