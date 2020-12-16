#!/bin/sh

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

(cd ../takthirdparty && make TARGET=android-armeabi-v7a \
	build_spatialite \
	build_commoncommo \
	build_gdal \
	build_assimp) &
(cd ../takthirdparty && make TARGET=android-arm64-v8a \
	build_spatialite \
	build_commoncommo \
	build_gdal \
	build_assimp) &
(cd ../takthirdparty && make TARGET=android-x86 \
	build_spatialite \
	build_commoncommo \
	build_gdal \
	build_assimp) &
wait

(cd ../takengine && git clone https://github.com/synesissoftware/STLSoft-1.9.git thirdparty/stlsoft)
