ARCH ?=			$(shell uname -m)

ifeq (${ARCH},x86_64)
ARCH_FLAG =		-march=x86-64
endif

PROJ ?=			.
WS =			$(PROJ)/..

# $HERE specifies the relative path to the source files and is set by the
# Makefile that includes Common.mk
INTERFACE_DEST =	$(PROJ)/$(HERE:src/%=include/%)
LIB_DEST ?=		$(PROJ)/lib

STL_SOFT ?=		$(WS)/STLSoft
PGSC_UTILS ?=		$(WS)/PGSC_Utils


INCLUDES =		-I. -I$(PROJ)/include \
			-I$(STL_SOFT)/include \
			-I$(PGSC_UTILS)/include

CPPFLAGS =		$(ARCH_FLAG) -g3 -Wall -fmessage-length=0 \
			$(INCLUDES)

CFLAGS =		-std=c99

CXXFLAGS =		-Winline \
			-D__STDC_CONSTANT_MACROS \
			-D__STDC_LIMIT_MACROS \
			-std=c++03

LDFLAGS =		$(ARCH_FLAG) \
			-L.

LIB_PATH =		$(LIB_OS)/$(LIB_ARCH)

INCLUDE_CP ?=		ln -s

OS =			$(shell uname)

ifeq (${OS},Darwin)
##
##  If Mac OS
##
LIB_OS =		macosx
LIB_ARCH =		$(ARCH)
LIB_PREFIX =		lib
LIB_EXT =		.a
SHLIB_EXT =		.dylib
JNILIB_EXT =		.dylib
JAVA_INCLUDE =		$(shell /usr/libexec/java_home -v 1.6)/include
# STLSOFT needs to have one of UNIX|unix|__unix__|__unix defined for Mac OS X
CPPFLAGS +=		-DUNIX -D_POSIX_C_SOURCE=200809L
JVM_FLAGS =		-I$(JAVA_INCLUDE) -I$(JAVA_INCLUDE)/darwin
JVM_LIBS =		-framework JavaVM
SHLIB_FLAGS =		-dynamiclib \
			-install_name @rpath/$(@F) \
			-Wl,-rpath,@loader_path/.
# Sadly, the Mac linker doesn't support switching between static and dynamic
# linking of dependent libs.  One must instead place static libs in a directory
# that is in the library search path before a directory that contains their
# dynamic counterparts.
LINK_DYNAMIC =		
LINK_STATIC =		
else ifeq (${OS},Linux)
##
##  If Linux
##
LIB_OS =		linux
LIB_ARCH =		$(ARCH)
LIB_PREFIX =		lib
LIB_EXT =		.a
SHLIB_EXT =		.so
JNILIB_EXT =		.so
JAVA_INCLUDE =		$(JAVA_HOME)/include
CPPFLAGS +=		-D_POSIX_C_SOURCE=200809L \
			-fPIC
LDFLAGS +=		-L$(JAVA_HOME)/jre/lib/amd64/server \
			-Wl,-Bsymbolic
JVM_FLAGS =		-I$(JAVA_INCLUDE) -I$(JAVA_INCLUDE)/linux
JVM_LIBS =		-ljvm
SHLIB_FLAGS =		-shared -Wl,-soname,${@F},-rpath,'$$ORIGIN'
LINK_DYNAMIC =		-Wl,-Bdynamic
LINK_STATIC =		-Wl,-Bstatic
else
##
##  Assume Windows (via MinGW & Msys)
##
LIB_OS =		windows
LIB_ARCH =		$(ARCH)
CC =			gcc
LIB_PREFIX =		
LIB_EXT =		.a
SHLIB_EXT =		.dll
JNILIB_EXT =		.dll
JAVA_INCLUDE =		$(JAVA_HOME)/include
			# -D__USE_MINGW_ANSI_STDIO makes %zu work for size_t
CPPFLAGS +=		-DLIBXML_STATIC -D__USE_MINGW_ANSI_STDIO
LDFLAGS +=		-static -L$(JAVA_HOME)/lib -Wl,--kill-at
JVM_FLAGS =		-I$(JAVA_INCLUDE) -I$(JAVA_INCLUDE)/win32
JVM_LIBS =		-ljvm
SHLIB_FLAGS =		-shared
LINK_DYNAMIC =		-Wl,-Bdynamic
LINK_STATIC =		-Wl,-Bstatic
endif

