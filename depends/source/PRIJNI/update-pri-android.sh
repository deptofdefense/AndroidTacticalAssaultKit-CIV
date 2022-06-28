#!/bin/sh

if [ ! -d "$1" ]; then
    echo "usage: ./update-pri-android.sh <takthirdparty-directory>"
    exit 1
fi

if [ ! -f "$1/builds/android-armeabi-v7a-release/lib/libpri.so" ]; then
    echo "usage: ./update-pri-android.sh <takthirdparty-directory>"
    echo "No PRI ARM NDK build found, build PRI first"
    exit 1
fi

if [ ! -f "$1/builds/android-x86-release/lib/libpri.so" ]; then
    echo "usage: ./update-pri-android.sh <takthirdparty-directory>"
    echo "No PRI x86 NDK build found, build PRI first"
    exit 1
fi

PRIJNI_BASE_DIR=`pwd`
rm -rf pri-android.zip
rm -rf pri-android
cd $1/builds
zip -r $PRIJNI_BASE_DIR/pri-android.zip \
  ./android-*-release/include/pri \
  ./android-*-release/lib/libpri.so

