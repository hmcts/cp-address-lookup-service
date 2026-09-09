# CP Address Lookup Service

Address and postcode lookup service for the HMCTS Common Platform Programme (CPP).

It exposes a RESTful API for resolving UK postcodes and addresses, backed by the Ordnance Survey (OS) Places API.

This repository is built on the [HMCTS Crime Service Spring Boot Template](https://github.com/hmcts/service-hmcts-crime-springboot-template), inheriting its baseline Spring Boot scaffold, observability, logging, and CI/CD conventions.

## Documentation

Further documentation can be found in the [docs](docs) directory.

### Key Documentation
- [Logging Documentation](docs/Logging.md) - Logging configuration and best practices
- [Pipeline Documentation](docs/PIPELINE.md) - CI/CD pipeline configuration and deployment processes

### Prerequisites

- ☕️ **Java 25 or later**: Ensure Java is installed and available on your `PATH`.
- ⚙️ **Gradle**: [Install Gradle](https://gradle.org/install/). The project itself defines which Gradle version to use (gradle/wraper/gradle-wrapper.properties).

You can verify installation with:
```bash
java -version
gradle -v
```

## Installation

### Build
```bash
gradle build
```

`build` will run all tests.

### Tests
- `gradle test` for running unit and integration tests

## Static code analysis

Install PMD

```bash
brew install pmd
```
```bash
pmd check \
    --dir src/main/java \
    --rulesets \
    .github/pmd-ruleset.xml \
    --format html \
    -r build/reports/pmd/pmd-report.html
```

Run PMD from Gradle

```
gradle pmdTest
```

### Contribute to This Repository

Contributions are welcome! Please see the [CONTRIBUTING.md](.github/CONTRIBUTING.md) file for guidelines.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details
