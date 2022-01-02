#!/bin/bash

# Be verbose
set -x

# Make sure you enter the directory that contains this script.
# The rest of the script requires this as the starting point.
pushd $(dirname $(readlink -f $0))

(cd ../takengine && mkdir thirdparty)

(cd .. && \
	gzip -d ./depends/assimp-4.0.1-mod.tar.gz && \
	cp depends/assimp-4.0.1-mod.tar . && \
	tar xf assimp-4.0.1-mod.tar)
(cd .. && \
        gzip -d ./depends/gdal-2.4.4-mod.tar.gz && \
        cp depends/gdal-2.4.4-mod.tar . && \
        tar xf gdal-2.4.4-mod.tar)
(cd .. && \
	gzip -d ./depends/tinygltf-2.4.1-mod.tar.gz && \
	cp depends/tinygltf-2.4.1-mod.tar takengine/thirdparty &&
	cd takengine/thirdparty && tar xf tinygltf-2.4.1-mod.tar)
(cd .. && \
	gzip -d ./depends/tinygltfloader-0.9.5-mod.tar.gz && \
	cp depends/tinygltfloader-0.9.5-mod.tar takengine/thirdparty &&
	cd takengine/thirdparty && tar xf tinygltfloader-0.9.5-mod.tar)
(cd .. && \
        gzip -d ./depends/libLAS-1.8.2-mod.tar.gz && \
        cp depends/libLAS-1.8.2-mod.tar . && \
        tar xf libLAS-1.8.2-mod.tar)
(cd .. && \
        gzip -d ./depends/LASzip-3.4.3-mod.tar.gz && \
        cp depends/LASzip-3.4.3-mod.tar . && \
        tar xf LASzip-3.4.3-mod.tar)

(cd ../takthirdparty && make TARGET=android-armeabi-v7a GDAL_USE_KDU=no \
	build_spatialite \
	build_commoncommo \
	build_gdal \
	build_assimp) &
(cd ../takthirdparty && make TARGET=android-arm64-v8a GDAL_USE_KDU=no \
	build_spatialite \
	build_commoncommo \
	build_gdal \
	build_assimp) &
(cd ../takthirdparty && make TARGET=android-x86 GDAL_USE_KDU=no \
	build_spatialite \
	build_commoncommo \
	build_gdal \
	build_assimp) &
wait

rm -rf ~/.conan
conan profile new default --detect
# This is unecessary if the default is detected above
#conan profile update settings.compiler.version=8 default

# install TTP conan packages
pushd ../takthirdparty
# add links to builds to the root
unlink android-armeabi-v7a-release
unlink android-arm64-v8a-release
unlink android-x86-release
ln -s builds/android-armeabi-v7a-release android-armeabi-v7a-release
ln -s builds/android-arm64-v8a-release android-arm64-v8a-release
ln -s builds/android-x86-release android-x86-release

cd ci-support
# install the packages locally

# conan
conan export-pkg . -s arch=armv8 -s os=Android -s os.api_level=29 -f
conan export-pkg . -s arch=armv7 -s os=Android -s os.api_level=29 -f
conan export-pkg . -s arch=x86 -s os=Android -s os.api_level=29 -f

# Install TTP maven package
./gradlew assemble
./gradlew publishTtpRuntimeAndroidPublicationToMavenLocal
popd

# install tinygltf conan packages
pushd ../takengine/thirdparty/tinygltf
conan export-pkg . -f
popd

# install tinygltf conan packages
pushd ../takengine/thirdparty/tinygltfloader
conan export-pkg . -f
popd

# build and install LASzip package
pushd ../LASzip
ANDROID_ABIS="arm64-v8a armeabi-v7a x86"
for LASZIP_ANDROID_ABI in ${ANDROID_ABIS} ;
do
    mkdir build-android-${LASZIP_ANDROID_ABI} || exit 1
    pushd build-android-${LASZIP_ANDROID_ABI} || exit 1
    cmake .. -G "Unix Makefiles" -DCMAKE_TOOLCHAIN_FILE=../cmake/android.toolchain.cmake -DCMAKE_BUILD_TYPE=Release -DANDROID_NDK=${ANDROID_NDK_HOME} -DANDROID_ABI=${LASZIP_ANDROID_ABI} -DANDROID_TOOLCHAIN=gcc -DANDROID_STL=gnustl_static -DANDROID_PLATFORM=android-24 -DCMAKE_CXX_FLAGS="-fexceptions -frtti -std=c++11" -DLASZIP_BUILD_STATIC=ON || exit 1
    cmake --build . || exit 1
    cp -r ../include . || exit 1
    cp ../src/*.hpp ./include/laszip || exit 1
    popd
done

cd ci-support
conan export-pkg . -s arch=armv8 -s os=Android -s os.api_level=29 -s compiler.version="8" -f
conan export-pkg . -s arch=armv7 -s os=Android -s os.api_level=29 -s compiler.version="8" -f
conan export-pkg . -s arch=x86 -s os=Android -s os.api_level=29 -s compiler.version="8" -f

popd

# build and install libLAS package
pushd ../libLAS
ANDROID_ABIS="arm64-v8a armeabi-v7a x86"
for LIBLAS_ANDROID_ABI in ${ANDROID_ABIS} ;
do
    mkdir build-android-${LIBLAS_ANDROID_ABI} || exit 1
    pushd build-android-${LIBLAS_ANDROID_ABI} || exit 1
    cmake .. -G "Unix Makefiles" -DCMAKE_TOOLCHAIN_FILE=../cmake/android.toolchain.cmake -DCMAKE_BUILD_TYPE=Release -DANDROID_NDK=${ANDROID_NDK_HOME} -DANDROID_ABI=${LIBLAS_ANDROID_ABI} -DANDROID_TOOLCHAIN=gcc -DANDROID_STL=gnustl_static -DANDROID_PLATFORM=android-24 -DCMAKE_CXX_FLAGS="-fexceptions -frtti -std=c++11" -DLASZIP_BUILD_STATIC=ON || exit 1
    cmake --build . --target las_c || exit 1
    cmake --build . --target las || exit 1
    cp -r ../include . || exit 1
    popd
done

cd ci-support
# publish to conan
conan export-pkg . -s arch=armv8 -s os=Android -s os.api_level=29 -s compiler.version="8" -f
conan export-pkg . -s arch=armv7 -s os=Android -s os.api_level=29 -s compiler.version="8" -f
conan export-pkg . -s arch=x86 -s os=Android -s os.api_level=29 -s compiler.version="8" -f

# publish to maven
./gradlew assemble
./gradlew publishLibLasAndroidPublicationToMavenLocal

popd


# STL-soft
(cd .. && git clone --depth 1 https://github.com/synesissoftware/STLSoft-1.9.git stl-soft)

cp stl-soft-conanfile.py ../stl-soft/conanfile.py
pushd ../stl-soft
conan export-pkg . -f
popd

# Khronos
mkdir -p ../khronos
(cd .. && git clone --depth 1 https://github.com/KhronosGroup/OpenGL-Registry khronos/OpenGL)
(cd .. && git clone --depth 1 https://github.com/KhronosGroup/EGL-Registry khronos/EGL)

cp khronos-conanfile.py ../khronos/conanfile.py
pushd ../khronos
conan export-pkg . -f
popd

popd
