# Building ATAK

## Development Environment

ATAK primarily uses a gradle based build system. We recommend Android Studio as the IDE, however, you can be build the entire application from the command-line.

### Android Studio

https://developer.android.com/studio

### Android NDK r21

Unless otherwise noted, all shared objects in ATAK that are compiled from source are compiled using NDK r12b.  

Direct download links:  

* Windows 64-bit https://dl.google.com/android/repository/android-ndk-r21-windows-x86_64.zip
* Mac OS X https://dl.google.com/android/repository/android-ndk-r21-darwin-x86_64.zip
* Linux 64-bit https://dl.google.com/android/repository/android-ndk-r21-linux-x86_64.zip

The NDK should be installed in one of the directories that gradle will detect. This includes  

* `$ANDROID_SDK_HOME/ndk`
* `set ndk.dir in the project's local.properties` (remember on Windows to correctly construct the path C\:\\location\\to\\sdk)  
* `set the $ANDROID_NDK_HOME environment variable` (gradle has deprecated use of this variable)  

### Other Dependencies

#### STL Soft

ATAK requires STL Soft 1.9. The repository should be cloned under `takengine/thirdparty/stlsoft`.  
  
From local repository root, if not using prebuild script:  

``` sh
cd takengine/thirdparty
git clone https://github.com/synesissoftware/STLSoft-1.9.git stlsoft
```  

#### TAK Third Party Build System

**NOTE** The TAK Third Party build system is only confirmed to work on Linux. Experience may vary on other host environments.

The TAK Third Party build system requires a number of dependencies for execution. Please refer to the Readme.md document in the root of the `takthirdparty` directory for the list of those dependencies and execution of that build system.

## Building The Prerequisite Dependencies

**NOTE** we've included a convenience script, `scripts/prebuild.sh` to execute the TAK Third Party build

Prior to building the APK within Android Studio, many of the binary thirdparty dependencies must be built with the TAK Third Party build system (for all architectures) from the command line.

### Step 1: Unpack GDAL

A tarball containing a modified version of GDAL 2.2.3 is included with the library in the `depends` directory. The contents must be extracted to the root directory for use during step 2, e.g.

``` sh
gzip -d ./depends/gdal-2.2.3-mod.tar.gz
cp ./depends/gdal-2.2.3-mod.tar .
tar xf gdal-2.2.3-mod.tar
```

### Step 2: Build shared object dependencies using TAK Third Party

The following issues should be executed from within the `takthirdparty` directory:  

``` sh
make TARGET=android-arm64-v8a build_spatialite build_gdal build_commoncommo build_assimp  
make TARGET=android-armeabi-v7a build_spatialite build_gdal build_commoncommo build_assimp  
make TARGET=android-x86 build_spatialite build_gdal build_commoncommo build_assimp  
```

On successful completion, this will create the following directories, containing header files, static libraries and shared objects:  

``` sh
takthirdparty/builds/android-arm64-v8a-release
takthirdparty/builds/android-armeabi-v7a-release
takthirdparty/builds/android-x86-release
```

These build artifacts will be used by the various `build.gradle` and `Android.mk` scripts used to build the APK.

## Building the ATAK APK

The normal build processes in Android Studio may be used for general building and debugging. Below is some helpful information as it relates to the specifics of ATAK development.  

**NOTE** You must generate your own signing key. Once generated, you will need to specify the following properties in your `local.properties` file.

``` ini
takDebugKeyFile=...
takDebugKeyFilePassword=...
takDebugKeyAlias=...
takDebugKeyPassword=...
takReleaseKeyFile=...
takReleaseKeyFilePassword=...
takReleaseKeyAlias=...
takReleaseKeyPassword=...
```

When specified you must replace the ellipsis (`...`) with the actual values. Note that the paths should be relative.  

The following gradle tasks are commonly used

* `assembleCivDebug` builds the ATAK CIV APK in debug mode
* `assembleCivRelease` builds the ATAK CIV APK in release mode
* `installCivDebug` builds and install the ATAK CIV APK in debug mode to all connected devices (see use of `ANDROID_SERIAL` to target a specific device from the command line)
* `installCivRelease` builds and install the ATAK CIV APK in release mode to all connected devices (see use of `ANDROID_SERIAL` to target a specific device from the command line)
* `createCivDebugJacocoTestReport` executes all tests and generates a code coverage report for ATAK CIV (debug)
* `createCivReleaseJacocoTestReport` executes all tests and generates a code coverage report for ATAK CIV (release)

**NOTE** When executing the `createXXXJacocoTestReport` targets, the gradle command must be passed the option `-Pcoverage`, e.g.  
  
`./gradlew -Pcoverage createCivDebugJacocoTestReport`
