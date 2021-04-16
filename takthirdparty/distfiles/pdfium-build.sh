#/bin/sh
# Adapted from
# https://raw.githubusercontent.com/rouault/pdfium/build/build-lin.sh
# and
# https://raw.githubusercontent.com/rouault/pdfium/build/build-win.bat

##########################
# Editable variables begin here
# Note: If editing variables after running once, completely removing
# the "pdfium" directory created herein is recommended.

# Build type
BUILDTYPE=Release
#BUILDTYPE=Debug

# System to build *for*. Building for windows supported only on windows
# and android only on Linux. 
# Pick *one* of these.
#SYSTEM=win64
#SYSTEM=win32
SYSTEM=android

# Android API level (SYSTEM=android only)
API_LEVEL=21

# Version of MSVC to use (SYSTEM=win* only)
export GYP_MSVS_VERSION="2015"

# Path of takthirdparty.  (currently only needed for SYSTEM=win* only)
TTP=$(cd $(dirname $0) ; pwd -P)/../takthirdparty

# Python executable to use
# NOTE:  Python 2.x required!  Python 3.x will not work due to upstream
# gyp and pdfium requiring 2.x!
LOCAL_PYTHON=python2.7


# End editable variables
##########################


failstate=dep

function fail()
{
    if [ $# -ne 1 -o "$1" = "" ] ; then
        err="Unknown failure"
    else
        err=$1
    fi
    echo "$err" >&2
    if [ "$failstate" = "$dep" ] ; then
        rm -rf pdfium_deps
    fi
    exit 1
}

depsdir=`pwd`/pdfium_deps
export PATH=$depsdir/depot_tools:$PATH

if [ ! -e "pdfium_deps/.deps_ok" ] ; then
  rm -rf pdfium_deps
  mkdir pdfium_deps || fail
  cd pdfium_deps || fail

  if [ "$SYSTEM" = "android" ] ; then
    if [ "$ANDROID_NDK" = "" ] ; then
        fail "ANDROID_NDK not set"
    fi

    mkdir toolchains || fail
    cd toolchains || fail
    ${ANDROID_NDK}/build/tools/make-standalone-toolchain.sh \
        --platform=android-$API_LEVEL \
        --arch=arm \
        --install-dir=armeabi-v7a/ || fail "Could not create NDK toolchain"
    ${ANDROID_NDK}/build/tools/make-standalone-toolchain.sh \
        --platform=android-$API_LEVEL \
        --arch=arm64 \
        --install-dir=arm64-v8a/ || fail "Could not create NDK toolchain"
    ${ANDROID_NDK}/build/tools/make-standalone-toolchain.sh \
        --platform=android-$API_LEVEL \
        --arch=x86 \
        --install-dir=x86/ || fail "Could not create NDK toolchain"
    cd ../ || fail
  fi
  git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git || fail "Could not clone depot_tools"
  echo "Depot tools rev:" > deps-revs
  ( cd depot_tools && git rev-parse HEAD >> ../deps-revs ) || fail
  git clone https://chromium.googlesource.com/external/gyp.git || fail "Could not clone gyp"
  echo "gyp rev:" >> deps-revs
  ( cd gyp && git rev-parse HEAD >> ../deps-revs ) || fail
  cd gyp || fail
  if [ "$SYSTEM" = "android" ] ; then
      $LOCAL_PYTHON ./setup.py build || fail "Failed to setup GYP"
  else
      $LOCAL_PYTHON ./setup.py install || fail "Failed to setup GYP"
  fi
  cd ../.. || fail
  touch pdfium_deps/.deps_ok
fi

failstate=main

# Download pdfium
if [ ! -e pdfium ] ; then
  git clone https://github.com/rouault/pdfium || fail "Could not clone pdfium"
  
  cp pdfium_deps/deps-revs pdfium/ || fail
  cd pdfium || fail
  if [ "$SYSTEM" = "android" ] ; then
    patch -p1 <<__EOF__
diff --git a/build/standalone.gypi b/build/standalone.gypi
index ecf849b..3407a56 100644
--- a/build/standalone.gypi
+++ b/build/standalone.gypi
@@ -151,7 +151,7 @@
       '-Wno-unused-parameter',
       '-pthread',
       '-fno-exceptions',
-      '-fvisibility=hidden',
+      #'-fvisibility=hidden',
       '-fPIC',
     ],
     'cflags_cc': [
diff --git a/core/src/fxge/android/fpf_skiafontmgr.cpp b/core/src/fxge/android/fpf_skiafontmgr.cpp
index 86bb052..939ff07 100644
--- a/core/src/fxge/android/fpf_skiafontmgr.cpp
+++ b/core/src/fxge/android/fpf_skiafontmgr.cpp
@@ -10,6 +10,7 @@
 #define FPF_SKIAMATCHWEIGHT_NAME2	60
 #define FPF_SKIAMATCHWEIGHT_1		16
 #define FPF_SKIAMATCHWEIGHT_2		8
+#include "../../../include/fxcrt/fx_ext.h"
 #include "fpf_skiafontmgr.h"
 #include "fpf_skiafont.h"
 #ifdef __cplusplus
diff --git a/pdfium.gyp b/pdfium.gyp
index 10cd716..f3bae94 100644
--- a/pdfium.gyp
+++ b/pdfium.gyp
@@ -18,6 +18,13 @@
       'V8_DEPRECATION_WARNINGS',
       '_CRT_SECURE_NO_WARNINGS',
     ],
+    'cflags':[
+      '-std=gnu++11',
+      '-fPIC',
+    ],
+    'cflags!':[
+      '-fvisibility=hidden',
+    ], 
     'include_dirs': [
       'third_party/freetype/include',
     ],
__EOF__

    [ $? -eq 0 ] || fail "Failed to patch"
  else
    git checkout win_gdal_build || fail "Could not get windows build branch"
  fi
  git rev-parse HEAD > pdfium-rev || fail
  cd ../ || fail

fi

if [ "$SYSTEM" = "android" ] ; then
    export PYTHONPATH=$PWD/pdfium_deps/gyp/build/`ls $PWD/pdfium_deps/gyp/build`
fi

cd pdfium || fail
${LOCAL_PYTHON} ./build/gyp_pdfium || fail "Could not generate GYP project files"


if [ "$SYSTEM" = "android" ] ; then
  for i in native arm64-v8a armeabi-v7a x86 ; do
    TCPATH=$depsdir/toolchains/$i/bin/
    case $i in
      native)
        TCPREF=
        TCPATH=
        ;;
      arm64-v8a)
        TCPREF="aarch64-linux-android-"
        ;;
      armeabi-v7a)
        TCPREF="arm-linux-androideabi-"
        ;;
      x86)
        TCPREF="i686-linux-android-"
        ;;
    esac

    # Clean up any leftovers
    rm -rf out || fail

    make BUILDTYPE=$BUILDTYPE \
      CXX=${TCPATH}${TCPREF}g++ CC=${TCPATH}${TCPREF}gcc \
      pdfium \
      fdrm \
      fpdfdoc \
      fpdfapi \
      fpdftext \
      fxcodec \
      fxcrt \
      fxge \
      fxedit \
      pdfwindow \
      formfiller \
      bigint \
      freetype \
      fx_agg \
      fx_lcms2 \
      fx_zlib \
      pdfium_base \
      fx_libjpeg \
      fx_libopenjpeg || fail "Building for $i failed"
    
    rm -rf "lib" || fail
    mkdir "lib" || fail
    cd out/$BUILDTYPE/obj.target || fail
    for lib in `find -name '*.a'`;
        do ar -t $lib | xargs ar rvs libpdfium.a.new || fail "Failed to flatten .a"
    done
    mv libpdfium.a.new ../../../lib/libpdfium.a || fail
    cd ../../../ || fail
    rm -rf out || fail
    cd .. || fail
    rm -f $i.tar.gz
    tar cfz $i.tar.gz pdfium || fail "Failed to tar up results for $i"
    cd pdfium || fail
  done
else

  case "$SYSTEM" in
    win32)
      for i in *.vcxproj ; do
        sed -e 's,<Lib>,<Lib><TargetMachine>MachineX86</TargetMachine>,' $i > $i.tmp && mv $i.tmp $i
      done
      vss="${TTP}/mk/vs14_x86.sh"
      wplatform="Win32"
      ;;
    win64)
      vss="${TTP}/mk/vs14_x64.sh"
      wplatform="x64"
      ;;
    *)
      fail "Unsupported windows version - check SYSTEM variable (top of script)"
      ;;
  esac

  $vss msbuild build\\all.sln \
      /p:Configuration=${BUILDTYPE} /p:Platform=${wplatform} /m  \
    || fail "msbuild failed"

  rm -rf "lib" || fail
  mkdir "lib" || fail
  cp build/${BUILDTYPE}/lib/*.lib lib/ || fail
  rm -rf build/${BUILDTYPE}

  cd .. || fail
  rm -f ${SYSTEM}.tar.gz
  tar cfz ${SYSTEM}.tar.gz pdfium || fail "Failed to tar up results"

fi

