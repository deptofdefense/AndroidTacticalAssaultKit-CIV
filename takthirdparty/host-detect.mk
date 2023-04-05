# Supported host platforms:
# Cygwin on Windows
# Linux
# MacOS X
#

HOST_UNAME=$(shell uname -s)


ifeq (CYGWIN, $(findstring CYGWIN, $(HOST_UNAME)))
    PLATFORM=win32
    SEP=;
    LN_S=cp
    SWIG=C:\\swigwin-4.0.1\\swig.exe
    PATH_CYGSAFE=$(shell cygpath -m $(1))
    PATH_WIN=$(shell cygpath -w $(1))
else
    ifeq "$(HOST_UNAME)" "Darwin"
        PLATFORM=darwin
    endif
    ifeq "$(HOST_UNAME)" "Linux"
        PLATFORM=linux
    endif
    LN_S=ln -s
    PATH_CYGSAFE=$(1)
endif

ifndef PLATFORM
    $(error host platform could not be determined)
endif

