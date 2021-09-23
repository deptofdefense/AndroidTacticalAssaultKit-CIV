#!/bin/bash

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
conan profile update settings.compiler.version=8 default

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
conan export-pkg . -s arch=armv8 -s os=Android -s os.api_level=29 -f
conan export-pkg . -s arch=armv7 -s os=Android -s os.api_level=29 -f
conan export-pkg . -s arch=x86 -s os=Android -s os.api_level=29 -f
popd

# install tinygltf conan packages
pushd ../takengine/thirdparty/tinygltf
conan export-pkg . -f
popd

# install tinygltf conan packages
pushd ../takengine/thirdparty/tinygltfloader
conan export-pkg . -f
popd

(cd .. && git clone https://github.com/synesissoftware/STLSoft-1.9.git stl-soft)
