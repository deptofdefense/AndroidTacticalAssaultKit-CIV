# Building ATAK

## Development Environment

ATAK primarily uses a gradle based build system. We recommend Android Studio as the IDE, however, you can be build the entire application from the command-line.

### Android Studio

https://developer.android.com/studio

### Android NDK r12b

Unless otherwise noted, all shared objects in ATAK that are compiled from source are compiled using NDK r12b.  

Direct download links:  

* Windows 32-bit https://dl.google.com/android/repository/android-ndk-r12b-windows-x86.zip
* Windows 64-bit https://dl.google.com/android/repository/android-ndk-r12b-windows-x86_64.zip
* Mac OS X https://dl.google.com/android/repository/android-ndk-r12b-darwin-x86_64.zip
* Linux 64-bit https://dl.google.com/android/repository/android-ndk-r12b-linux-x86_64.zip

The NDK should be installed in one of the directories that gradle will detect. This includes  

* `$ANDROID_SDK_HOME/ndk`
* `set ndk.dir in the project's local.properties` (remember on Windows to correctly construct the path C\:\\location\\to\\sdk)  
* `set the $ANDROID_NDK_HOME environment variable` (gradle has deprecated use of this variable)  

### CMake

CMake 3.14.7 or later is required for the build of `libtakengine`. The 3.14.7 version is known to work for the Android build; other versions are not guaranteed to be supported. It is _strongly recommended_ to define `cmake.dir` in the `local.properties` file to point at the root of the compatible CMake installation. It has been observed that the Android gradle plugin may not correctly select a compatible CMake install, even if it is available on the `PATH`.

Direct download links:  

* Windows 32-bit https://cmake.org/files/v3.14/cmake-3.14.7-win32-x86.zip
* Windows 64-bit https://cmake.org/files/v3.14/cmake-3.14.7-win64-x64.zip
* Mac OS X https://cmake.org/files/v3.14/cmake-3.14.7-Darwin-x86_64.tar.gz
* Linux 64-bit https://cmake.org/files/v3.14/cmake-3.14.7-Linux-x86_64.tar.gz

### Conan

Conan is now used for management of C/C++ packages. Conan must be installed and available on the `PATH` in the local development environment.

https://conan.io/downloads.html

*NOTE:* Conan will publish the packages using the _compiler version_ of the _host machine_. A compiler version of 8 is required for the CMake build to properly resolve the packages. If Conan is not being used on other projects, the compiler version for the default profile can be set via

``` sh
# creates the default profile if none exists
#conan profile new default --detect

# set the compiler version on the default profile
conan profile update settings.compiler.version=8 default
```

If Conan is being used for other projects within the development environment, it is recommended to create a separate profile specific to Android or ATAK, and use that profile for the local publications.

### Other Dependencies

#### STL Soft

ATAK requires STL Soft 1.9. The repository should be cloned under `takengine/thirdparty/stlsoft`.  
  
From local repository root:  

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

A tarball containing a modified version of GDAL 2.4.4 is included with the library in the `depends` directory. The contents must be extracted to the root directory for use during step 2, e.g.

``` sh
gzip -d ./depends/gdal-2.4.4-mod.tar.gz
cp ./depends/gdal-2.4.4-mod.tar .
tar xf gdal-2.4.4-mod.tar
```

### Step 3: Unpack ASSIMP

A tarball containing a modified version of ASSIMP 4.0.1 is included with the library in the `depends` directory. The contents must be extracted to the root directory for use during step 3, e.g.

``` sh
gzip -d ./depends/assimp-4.0.1-mod.tar.gz
cp depends/assimp-4.0.1-mod.tar .
tar xf assimp-4.0.1-mod.tar
```

### Step 3: Build shared object dependencies using TAK Third Party

The following commands should be executed from within the `takthirdparty` directory:  

``` sh
make TARGET=android-arm64-v8a GDAL_USE_KDU=no build_spatialite build_gdal build_commoncommo build_assimp  
make TARGET=android-armeabi-v7a GDAL_USE_KDU=no build_spatialite build_gdal build_commoncommo build_assimp  
make TARGET=android-x86 GDAL_USE_KDU=no build_spatialite build_gdal build_commoncommo build_assimp  
```

On successful completion, this will create the following directories, containing header files, static libraries and shared objects:  

``` sh
takthirdparty/builds/android-arm64-v8a-release
takthirdparty/builds/android-armeabi-v7a-release
takthirdparty/builds/android-x86-release
```

Following the build, the artifacts must be published to the local conan package repository. The following commands should be executed from within the `takthirdparty` directory:

``` sh
# install TTP conan packages

# add links to builds to the `takthirdparty` root
ln -s builds/android-armeabi-v7a-release android-armeabi-v7a-release
ln -s builds/android-arm64-v8a-release android-arm64-v8a-release
ln -s builds/android-x86-release android-x86-release

cd ci-support
# install the packages locally
conan export-pkg . -s arch=armv8 -s os=Android -s os.api_level=29 -f
conan export-pkg . -s arch=armv7 -s os=Android -s os.api_level=29 -f
conan export-pkg . -s arch=x86 -s os=Android -s os.api_level=29 -f
```

These build artifacts will be used by the various `build.gradle` and `Android.mk` scripts used to build the APK.

### Step 3: Unpack tinygltf/tinygltfloader

A tarball containing modified versions of tinygltf (2.4.1) and tinygltfloader (0.9.5) are included with the library in the `depends` directory. The contents must be extracted and then published to the local conan package repository.

``` sh
 # unpack tinygltf
gzip -d ./depends/tinygltf-2.4.1-mod.tar.gz
cp depends/tinygltf-2.4.1-mod.tar takengine/thirdparty
(cd takengine/thirdparty && tar xf tinygltf-2.4.1-mod.tar)

gzip -d ./depends/tinygltfloader-0.9.5-mod.tar.gz
cp depends/tinygltfloader-0.9.5-mod.tar takengine/thirdparty
(cd takengine/thirdparty && tar xf tinygltfloader-0.9.5-mod.tar)

# install tinygltf conan packages
pushd takengine/thirdparty/tinygltf
conan export-pkg . -f
popd

# install tinygltfloader conan packages
pushd takengine/thirdparty/tinygltfloader
conan export-pkg . -f
popd
```

### Step 4: Clone STL Soft

STL Soft must be cloned into the root

``` sh
git clone https://github.com/synesissoftware/STLSoft-1.9.git stl-soft
```


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
