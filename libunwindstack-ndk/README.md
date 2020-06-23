Initially sourced from https://github.com/ivanarh/libunwindstack-ndk

Modified further to work with version 12 of Android NDK


BUILDING
0. Install android NDK 12b
1. Clone this repo
2. Pull submodule "lzma" -- cd into this directory then: git submodule update --init
3. Choose a build directory, call it $BDIR. Must be unique for a given ABI's
   build
4. mkdir $BDIR
5. cd $BDIR
6. cmake \
    -DANDROID_ABI=${YOUR_ABI_HERE} \
    -DANDROID_ARM_NEON=ON \
    -DANDROID_NATIVE_API_LEVEL=21 \
    -DANDROID_STL=gnustl_shared \
    -DCMAKE_TOOLCHAIN_FILE=/path/to/libunwindstack-ndk/cmake/android.toolchain.cmake \
    -DANDROID_NDK=/path/to/your/ndk \
    /path/to/libunwindstack-ndk/cmake
7. make

