#!/bin/sh

bat="`dirname $0`/vs16_x64.bat"
bat="`cygpath -m $bat`"

# Remove Cygwin additions to PATH prior to executing vsvars.
# Cygwin 64-bit pollutes DLL path and causes VC++ to not run properly.
export PATH="$ORIGINAL_PATH"

if [ "$TTP_EXTRA_PATH" ] ; then
    export PATH="$TTP_EXTRA_PATH:$PATH"
fi

# Clear out any external influences on nmake
unset MAKEFLAGS

VCVARS_PATH="\"c:/Program Files (x86)/Microsoft Visual Studio/2019/Professional/VC/Auxiliary/Build/vcvars64.bat\""
if [ ! -f "/cygdrive/c/Program Files (x86)/Microsoft Visual Studio/2019/Professional/VC/Auxiliary/Build/vcvars64.bat" -a -f "/cygdrive/c/Program Files (x86)/Microsoft Visual Studio/2019/BuildTools/VC/Auxiliary/Build/vcvars64.bat" ] ; then
    VCVARS_PATH="\"c:/Program Files (x86)/Microsoft Visual Studio/2019/BuildTools/VC/Auxiliary/Build/vcvars64.bat\""
fi
export VCVARS_PATH

export MSB_ARGS=$*
MSB_ARGS=$* cmd /Q /C $bat
