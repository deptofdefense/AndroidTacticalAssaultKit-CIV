# See if ANDROID_NDK env var is set
ifeq ($(strip $(ANDROID_NDK)),)
    $(error ANDROID_NDK is not set in environment. Set ANDROID_NDK properly.)
endif

$(TARGET)_prep=$(OUTDIR)/toolchain

ANDROID_API_LEVEL=21

$(OUTDIR)/toolchain:
	$(ANDROID_NDK)/build/tools/make-standalone-toolchain.sh     \
		--platform=android-$(ANDROID_API_LEVEL)             \
		--arch=arm                                          \
		--install-dir=$@                                    


# Put Android NDK on path
export PATH := $(PATH):$(OUTDIR)/toolchain/bin

# Android ABI
ANDROID_ABI=armeabi-v7a

# Tools
CC=arm-linux-androideabi-gcc
CXX=arm-linux-androideabi-g++
CPP=arm-linux-androideabi-cpp
LD=arm-linux-androideabi-ld
RANLIB=arm-linux-androideabi-ranlib
AR=arm-linux-androideabi-ar
STRIP=arm-linux-androideabi-strip -S

# "host" argument to autoconf-based configure scripts
# Leave blank for autodetect/non-cross compile
CONFIGURE_TARGET=--host arm-linux-androideabi
CONFIGURE_debug=--enable-debug

# Library naming
LIB_PREFIX=lib
LIB_SHAREDSUFFIX=so
LIB_STATICSUFFIX=a

# Object file naming
OBJ_SUFFIX=o

# Flags - common to all packages
CFLAGS_generic:=-fstack-protector-all -mfloat-abi=softfp -mfpu=neon -march=armv7-a -mtune=cortex-a8 -ftree-vectorize
CFLAGS_release:=-O3
CFLAGS_debug:=-g -O0
CXXFLAGS_generic:=$(CFLAGS_generic)
CXXFLAGS_release:=$(CFLAGS_release)
CXXFLAGS_debug:=$(CFLAGS_debug)
LDFLAGS_generic:=

# Per-package flags
kdu_PLATFORM=arm-eabi-gcc
pri_PLATFORM=$(ANDROID_ABI)

openssl_CFLAGS_generic=
openssl_CFLAGS_release=
openssl_CFLAGS_debug=
openssl_CXXFLAGS_generic=
openssl_CXXFLAGS_release=
openssl_CXXFLAGS_debug=
openssl_CONFIG=./Configure linux-generic32 no-shared --cross-compile-prefix=arm-linux-androideabi- --prefix=$(OUTDIR_CYGSAFE) $(openssl_CFLAGS)
openssl_LDFLAGS=


# Target-specific patches for libkml, space separated
libkml_EXTRAPATCHES=
# Target-specific patches to be applied before libkml's autoconf is run
libkml_EXTRAPATCHES_PREAC=

# Mr. SID binary bundle file path
# binbundle has mrsid/ top level directory with include/ under that
mrsid_BINBUNDLE=$(DISTFILESDIR)/mrsid/android.tar.gz
# Path within expansion of above where the Mr.Sid library can be found
# omit mrsid/ initial directory
mrsid_BINLIBPATH=lib/armeabi-v7a

gdal_CFLAGS_generic=-DKDU_MAJOR_VERSION=6
gdal_CXXFLAGS_generic=$(gdal_CFLAGS_generic)

assimp_CONFIG_EX=-DCMAKE_SYSTEM_NAME=Android                    \
    -DCMAKE_SYSTEM_VERSION=$(ANDROID_API_LEVEL)                 \
    -DCMAKE_ANDROID_STL_TYPE=gnustl_shared                      \
    -DCMAKE_ANDROID_ARCH_ABI=$(ANDROID_ABI)                     \
    -DCMAKE_ANDROID_ARM_NEON=ON                                 \
    -DCMAKE_ANDROID_STANDALONE_TOOLCHAIN=$(call PATH_CYGSAFE,$(OUTDIR)/toolchain)


curl_LIBS=-lstdc++ -lm -llog
proj_LIBS=-lstdc++ -lm -llog
expat_LIBS=-lstdc++ -lm -llog
libkml_LIBS=-lstdc++ -lm -llog
libspatialite_LIBS=-lstdc++ -lm
protobuf_LIBS=-llog
gdal_LIBS=-L$(OUTDIR_CYGSAFE)/lib -lssl -lcrypto -lstdc++

gdal_LDFLAGS=

gdal_KILL_JAVA_INCLUDE=yes


libxml2_installtargets=install-libLTLIBRARIES install-data
libxml2_buildtarget=libxml2.la

#commoncommo_BUILDSTATIC=yes
commoncommo_BUILDJAVA=yes
