#!/bin/sh

bat="`dirname $0`/vs14_x64.bat"
bat="`cygpath -m $bat`"

# Remove Cygwin additions to PATH prior to executing vsvars.
# Cygwin 64-bit pollutes DLL path and causes VC++ to not run properly.
export PATH="$ORIGINAL_PATH"

if [ "$TTP_EXTRA_PATH" ] ; then
    export PATH="$TTP_EXTRA_PATH:$PATH"
fi

# Clear out any external influences on nmake
unset MAKEFLAGS


export MSB_ARGS=$*
MSB_ARGS=$* cmd /Q /C $bat
