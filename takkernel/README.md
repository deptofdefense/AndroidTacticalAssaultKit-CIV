# takkernel

Java and C++ code shared across TAK applications

# Project Organization

## Layout

| path | contents |
| ----- | ----- |
| aarbundle | Aggregator project used to construct Android AAR from other sub-projects |
| annotation | Project containing Java annotations used in `takkernel` |
| controlled | Project containing code that is not publicly releasable. This project is completely separable and not a dependency of the core `takkernel`. |
| controlledaarbundle | Aggregator project used to construct Android AAR from other sub-projects that contain code that is not publicly releasable |
| shared | Project containing the shared data models and business logic for TAK Java based applications |
| engine | Project containing the TAK map engine API and implementation |
| gradle/ | Contains Gradle distribution and custom scripting |
| port | Project containing Portability API to reduce platform specific dependencies |

## Dependency Graph

```
annotation
    |
  port
    |
 engine
    |
 shared
    |  \
    |    ---
aarbundle    \
          controlled
              |
       controlledaarbundle
```

# Build System

## Build instructions

### Setup

1. Ensure [Java 8 or later](https://adoptium.net/?variant=openjdk8) is present on system and the `JAVA_HOME` environment 
   variable is set to reference the correct location (e.g. "C:\Program Files\Eclipse Adoptium\jdk-8.0.322.6-hotspot").

2. Download and install [CMake 3.14.7 or later](https://cmake.org/download/). Make sure it is added to `PATH`.

3. Download and install [Python 3+](https://www.python.org/downloads/). Make sure it is added to `PATH`.

4. Install Conan using Python by executing the following command: `pip3 install --upgrade conan`

5. Update Conan's trust store to add the LetsEncrypt CA cert:
   1. Download the [LetsEncrypt cert](https://letsencrypt.org/certs/isrgrootx1.pem).
   2. Execute the following command to cause Conan to initialize its data directory (the command itself doesn't matter; we
      just need to force Conan to run): `conan profile list`
   3. Browse to your Conan data directory.
      1. Linux: `$HOME/.conan`
      2. Windows: `%USERPROFILE%/.conan`
   4. Open the _cacert.pem_ in a text editor and paste the contents of the downloaded _isrgrootx1.pem_ at the end of the
      file.
   5. Save and close the _cacert.pem_ file.

6. Add a `local.properties` file to the root of the takkernel repo. At a minimum, it needs the following values. For 
   additional values that may be added, see the [Properties and Extensions](#properties-and-extensions) table below.

   ```
   takRepoMavenUrl=<URL for accessing TAK Maven artifacts>
   takRepoConanUrl=<URL for accessing TAK Conan artifacts>
   takRepoUsername=<username>
   takRepoPassword=<password>
   ```
   
### Build

1. Execute the following gradle command to build and publish to maven local. Note: instead of providing
   `kernelBuildTarget` as a command line parameter, you can add it to the `local.properties` if desired. The value for
   the setting should be either `java` or `android`, depending on your target platform.

   ```
   gradlew publishToMavenLocal -PkernelBuildTarget=<your-build-target>
   ```
   
   For example, to build TAK Kernel for TAKX, execute the command as:
   ```
   gradlew publishToMavenLocal -PkernelBuildTarget=java
   ```

## IDE

Android Studio and IntelliJ are recommended for Android and Java Desktop development, respectively. Either IDE tool may be used, however, some features may be limited or non-functional depending on kernel build target configuration. Specifically, the _build_ button in the respective IDE will only be available when the kernel build target corresponds with the IDE software.

## Gradle

`takkernel` uses Gradle as the build system for Java source code. Each project makes use of a single Gradle script that is capable of building the library for either Android or Desktop Java build targets. The availability of specific tasks is dependent on the build target configuration, however the following major tasks are available for both:
* `assemble`
* `clean`
* `publishAllPublicationsToMavenRepository`
* `publishToMavenLocal`

Dual target assembly is facilitated by use of several custom scripts that reduce much of the boilerplate in a sub-project's `build.gradle` file.

| script | purpose |
| ----- | ----- |
| gradle/buildtype.gradle | Determines kernel build target and applies _utils_ script. |
| gradle/buildconfig.gradle | Applies Android or Java Gradle plugin, depending on kernel build target. Configures default source sets. Defines the `takkernel` version. | 
| gradle/publishing.gradle | Configures publications. |
| gradle/repositories.gradle | Configures standard repositories. |
| gradle/utils.gradle | Defines various utility functions as extensions. |
| gradle/versions.gradle | Defines dependencies versions as extensions. |

### Properties and Extensions

| name | where | required | default | purpose |
| ----- | ----- | ----- | ----- | ----- |
| **build target configuration** |
| takRepoMavenUrl | `local.properties` | yes | `invalid` | Defines the URL for accessing TAK Maven artifacts |
| takRepoConanUrl | `local.properties` | yes | `invalid` | Defines the URL for accessing TAK Conan artifacts |
| takRepoUsername | `local.properties` | yes | `invalid` | Provides a username for accessing TAK artifacts |
| takRepoPassword | `local.properties` | yes | `invalid` | Provides a password for accessing TAK artifacts |
| kernelBuildTarget | `local.properties`, command line | yes | `java` | Defines the build target for the `takkernel` library. Must be either `android` or `java`. |
| **publications** |
| maven.publish.url | `local.properties`, command line | maybe | `https://localhost` | Specifies the URL for maven publication. Only required when publishing to remote |
| maven.publish.user | `local.properties`, command line | maybe | `invalid` | Specifies the user for maven publication. Only required when publishing to remote |
| maven.publish.password | `local.properties`, command line | maybe | `invalid` | Specifies the password for maven publication. Only required when publishing to remote |
| skipPublications | sub-project `build.gradle` before `buildconfig.gradle` is applied | no | `['android']` | Used by `publishing.gradle` to omit sub-project artifacts from publication. |

# Build artifacts

## Java

A JAR file is produced for each of the sub-projects.

## Android

A single AAR is produced that contains libraries for each of the sub-projects.

# Versioning

`takkernel` uses Semantic Versioning (https://semver.org/). The version is defined in `gradle/buildconfig.gradle`. `versionCode` is a monotonically increasing value (to be bumped whenever the semantic version changes) to satisfy Android builds.
