# Tools
CC=gcc
CXX=g++
CPP=cpp
RANLIB=ranlib

# "host" argument to autoconf-based configure scripts
# Leave blank for autodetect/non-cross compile
# CONFIGURE_TARGET=--host blah-blah-blah
CONFIGURE_TARGET=
CONFIGURE_debug=--enable-debug

# Library naming
LIB_PREFIX=lib
LIB_SHAREDSUFFIX=so
LIB_STATICSUFFIX=a

# Object file naming
OBJ_SUFFIX=o

# Flags - common to all packages
CFLAGS_generic:=-fPIC
CFLAGS_release:=-O2
CFLAGS_debug:=-g -O0
CXXFLAGS_generic:=$(CFLAGS_generic)
CXXFLAGS_release:=$(CFLAGS_release)
CXXFLAGS_debug:=$(CFLAGS_debug)
LDFLAGS_generic:=

# Per-package flags
kdu_PLATFORM=Linux-x86-64-gcc
pri_PLATFORM=x86

openssl_CFLAGS_generic=
openssl_CFLAGS_release=
openssl_CFLAGS_debug=
openssl_CXXFLAGS_generic=
openssl_CXXFLAGS_release=
openssl_CXXFLAGS_debug=
openssl_CONFIG=./config --prefix=$(OUTDIR_CYGSAFE) $(openssl_CFLAGS) -DPURIFY no-asm
openssl_LDFLAGS=


# Target-specific patches for libkml, space separated
libkml_EXTRAPATCHES=
# Target-specific patches to be applied before libkml's autoconf is run
libkml_EXTRAPATCHES_PREAC=

# Mr. SID binary bundle file path
# binbundle has mrsid/ top level directory with include/ under that
mrsid_BINBUNDLE=$(DISTFILESDIR)/mrsid/linux-amd64.tar.gz
# Path within expansion of above where the Mr.Sid library can be found
# omit mrsid/ initial directory
mrsid_BINLIBPATH=lib

gdal_CFLAGS_generic=-DKDU_MAJOR_VERSION=6
gdal_CXXFLAGS_generic=$(gdal_CFLAGS_generic)
gdal_NO_JAVA=true

curl_LIBS=
proj_LIBS=
libkml_LIBS=
# tbb needed for mrsid dsdk
gdal_LIBS=-L$(OUTDIR)/lib -Wl,-rpath=$(OUTDIR)/lib -lssl -lcrypto -ldl -ltbb

gdal_LDFLAGS=-rpath=$(OUTDIR)/lib

libxml2_installtargets=install

commoncommo_BUILDJAVA=yes
libspatialite_LIBS=-ldl -lpthread -lm -lstdc++
