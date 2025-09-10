# Nokee Build Plugins

All plugins used for building Nokee projects.

## Usage

.settings.gradle
```groovy
pluginManagement {
	apply from: 'https://raw.githubusercontent.com/nokeedev/nokeebuild-plugins/refs/heads/main/init.gradle'
}
```

## Plugins

### Project Plugins

- `nokeebuild.use-junit-platform-in-test-suites`: All `JvmTestSuite` uses JUnit 5 testing framework at version `junit-jupiter` from Version Catalog or `latest.release`.
Set default `@TempDir` cleanup monde to `ON_SUCCESS`.
- `nokeebuild-use-latest-java-lts-in-test-suites`: All `JvmTestSuite` and `testFixtures` uses latest JAVA LTS.

### Settings Plugins

- `nokeebuild.repositories`: Prefer settings repositories and add `mavenCentral()`.
