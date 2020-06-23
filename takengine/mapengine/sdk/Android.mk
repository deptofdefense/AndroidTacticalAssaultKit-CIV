##
##  MODULE:  MapEngine		(libMapEngine.so)
##
##	N.B.: To be successfully imported by another module, the module name
##	must match the directory's.
##
##	Build using: ndk-build NDK_PROJECT_PATH=. \
##			       NDK_MODULE_PATH=.. \
##			       NDK_APPLICATION_MK=Application.mk \
##			       APP_BUILD_SCRIPT=Android.mk \
##			       PGSC_UTILS_PATH=<path to PGSC_Utils> \
##		     	       SPATIALITE_PATH=<path to SpatiaLite> \
##			       GDAL_PATH=<path to agdaljni> \
##			       STL_SOFT_PATH=<path to stlsoft>
##
##	NDK_MODULE_PATH is a colon-separated list of paths that are searched
##	for imported modules.  (Spaces are not permitted.)
##
##	To see build commands, add: V=1
##	To see NDK output, add: NDK_LOG=1
##	To debug imports, add: NDK_DEBUG_IMPORTS=1
##

# Need to save the local path because it will be reset by the included mk files.
SAVED_PATH :=			$(call my-dir)

include $(PGSC_UTILS_PATH)/Android_prebuilt.mk
include $(SPATIALITE_PATH)/Android_prebuilt.mk
include $(GDAL_PATH)/Android_prebuilt.mk

LOCAL_PATH :=			$(SAVED_PATH)
MY_SUBDIRS :=			core \
				db \
				feature \
				raster \
				raster/apass \
				raster/gdal \
				raster/mosaic \
				raster/osm \
				raster/pfi \
				raster/pfps \
				raster/tilereader \
				spi \
				util \
				math

include $(CLEAR_VARS)

rwildcard=$(foreach d,$(wildcard $1*),$(call rwildcard,$d/,$2) $(filter $(subst *,%,$2),$d))
SRC_PATH_RENDER_CLASSES := $(call rwildcard, $(LOCAL_PATH)/src/renderer/, *.cpp)

LOCAL_MODULE :=			MapEngine

LOCAL_SRC_FILES :=		$(foreach d,$(MY_SUBDIRS), \
				    $(wildcard $(LOCAL_PATH)/src/$(d)/*.cpp))
LOCAL_SRC_FILES +=              $(SRC_PATH_RENDER_CLASSES)

LOCAL_ARM_MODE :=		arm
LOCAL_ARM_NEON :=		true

LOCAL_CFLAGS +=			-D_POSIX_C_SOURCE=200809L \
				-D__STDC_CONSTANT_MACROS \
				-D__STDC_LIMIT_MACROS				    

LOCAL_C_INCLUDES :=		$(LOCAL_PATH)/src \
				$(STL_SOFT_PATH)/include \
				$(MY_SUBDIRS:%=$(LOCAL_PATH)/include/%)

LOCAL_CPP_FEATURES :=		exceptions rtti

LOCAL_SHARED_LIBRARIES := 	PGSC_Utils \
				gdal \
				spatialite sqlite3 xml2 proj iconv lzma geos_c geos

LOCAL_LDLIBS :=                 -lGLESv2

include $(BUILD_SHARED_LIBRARY)

#$(call modules-dump-database)