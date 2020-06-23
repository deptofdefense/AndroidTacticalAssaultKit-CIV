#!/bin/bash

mkdir build-android-x86
cd build-android-x86
cmake -DCMAKE_TOOLCHAIN_FILE=../android.toolchain.cmake -DANDROID_TOOLCHAIN_NAME=x86-4.9 -S .. -B .
make -j8 assimp
make -j8 jassimp

cd ..
mkdir build-android-arm
cd build-android-arm
cmake cmake -DCMAKE_TOOLCHAIN_FILE=../android.toolchain.cmake -S .. -B .
make -j8 assimp
make -j8 jassimp

cd ../port/jassimp
ant
