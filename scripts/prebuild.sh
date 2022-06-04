#!/bin/bash

# Be verbose
set -x

# Make sure you enter the directory that contains this script.
# The rest of the script requires this as the starting point.
pushd $(dirname $(readlink -f $0))

mkdir -p ../takengine/thirdparty

# Extract everything in parallel
tar xf ../depends/assimp-4.0.1-mod.tar.gz         -C ../ &
tar xf ../depends/gdal-2.4.4-mod.tar.gz           -C ../ &
tar xf ../depends/tinygltf-2.4.1-mod.tar.gz       -C ../takengine/thirdparty &
tar xf ../depends/tinygltfloader-0.9.5-mod.tar.gz -C ../takengine/thirdparty &
tar xf ../depends/libLAS-1.8.2-mod.tar.gz         -C ../ &
tar xf ../depends/LASzip-3.4.3-mod.tar.gz         -C ../ &
wait

# Make the third party parts in parallel
make -C ../takthirdparty \
	TARGET=android-armeabi-v7a GDAL_USE_KDU=no \
	build_spatialite \
	build_commoncommo \
	build_gdal \
	build_assimp &
make -C ../takthirdparty \
	TARGET=android-arm64-v8a GDAL_USE_KDU=no \
	build_spatialite \
	build_commoncommo \
	build_gdal \
	build_assimp &
make -C ../takthirdparty \
	TARGET=android-x86 GDAL_USE_KDU=no \
	build_spatialite \
	build_commoncommo \
	build_gdal \
	build_assimp &
wait

rm -rf ~/.conan
conan profile new default --detect
# This step is required to ensure conan package IDs are consistent between prebuild and build steps
conan profile update settings.compiler.version=8 default

# install TTP conan packages
pushd ../takthirdparty
# add links to builds to the root
ln -sf builds/android-armeabi-v7a-release android-armeabi-v7a-release
ln -sf builds/android-arm64-v8a-release android-arm64-v8a-release
ln -sf builds/android-x86-release android-x86-release

cd ci-support
# install the packages locally

# conan
conan export-pkg . -s arch=armv8 -s os=Android -s os.api_level=29 -f
conan export-pkg . -s arch=armv7 -s os=Android -s os.api_level=29 -f
conan export-pkg . -s arch=x86   -s os=Android -s os.api_level=29 -f

# Install TTP maven package
./gradlew assemble
./gradlew publishTtpRuntimeAndroidPublicationToMavenLocal
popd

pushd ../takengine/thirdparty/tinygltf
# install tinygltf conan packages
conan export-pkg . -f
# install tinygltf conan packages
cd ../tinygltfloader
conan export-pkg . -f
popd

# build and install LASzip package
pushd ../LASzip
ANDROID_ABIS="arm64-v8a armeabi-v7a x86"
for LASZIP_ANDROID_ABI in ${ANDROID_ABIS} ;
do
    mkdir -p build-android-${LASZIP_ANDROID_ABI}
    pushd build-android-${LASZIP_ANDROID_ABI}
    cmake .. -G Ninja -DCMAKE_TOOLCHAIN_FILE=../cmake/android.toolchain.cmake -DCMAKE_BUILD_TYPE=Release -DANDROID_NDK=${ANDROID_NDK_HOME} -DANDROID_ABI=${LASZIP_ANDROID_ABI} -DANDROID_TOOLCHAIN=gcc -DANDROID_STL=gnustl_static -DANDROID_PLATFORM=android-24 -DCMAKE_CXX_FLAGS="-fexceptions -frtti -std=c++11" -DLASZIP_BUILD_STATIC=ON
    cmake --build .
    cp -r ../include .
    cp ../src/*.hpp ./include/laszip
    popd
done

cd ci-support
conan export-pkg . -s arch=armv8 -s os=Android -s os.api_level=29 -s compiler.version="8" -f
conan export-pkg . -s arch=armv7 -s os=Android -s os.api_level=29 -s compiler.version="8" -f
conan export-pkg . -s arch=x86   -s os=Android -s os.api_level=29 -s compiler.version="8" -f

popd

# build and install libLAS package
pushd ../libLAS
ANDROID_ABIS="arm64-v8a armeabi-v7a x86"
for LIBLAS_ANDROID_ABI in ${ANDROID_ABIS} ;
do
    mkdir -p build-android-${LIBLAS_ANDROID_ABI}
    pushd build-android-${LIBLAS_ANDROID_ABI}
    cmake .. -G Ninja -DCMAKE_TOOLCHAIN_FILE=../cmake/android.toolchain.cmake -DCMAKE_BUILD_TYPE=Release -DANDROID_NDK=${ANDROID_NDK_HOME} -DANDROID_ABI=${LIBLAS_ANDROID_ABI} -DANDROID_TOOLCHAIN=gcc -DANDROID_STL=gnustl_static -DANDROID_PLATFORM=android-24 -DCMAKE_CXX_FLAGS="-fexceptions -frtti -std=c++11" -DLASZIP_BUILD_STATIC=ON
    cmake --build . --target las_c
    cmake --build . --target las
    cp -r ../include .
    popd
done

cd ci-support
# publish to conan
conan export-pkg . -s arch=armv8 -s os=Android -s os.api_level=29 -s compiler.version="8" -f
conan export-pkg . -s arch=armv7 -s os=Android -s os.api_level=29 -s compiler.version="8" -f
conan export-pkg . -s arch=x86   -s os=Android -s os.api_level=29 -s compiler.version="8" -f

# publish to maven
./gradlew assemble
./gradlew publishLibLasAndroidPublicationToMavenLocal

popd

cp stl-soft-conanfile.py ../stl-soft/conanfile.py
pushd ../stl-soft
conan export-pkg . -f
popd

# Khronos
cp khronos-conanfile.py ../khronos/conanfile.py
pushd ../khronos
conan export-pkg . -f
popd

popd
