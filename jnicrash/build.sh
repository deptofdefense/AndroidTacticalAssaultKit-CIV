#!/bin/bash

ATAK=../atak/ATAK/app

function fail()
{
    echo "Failed build while $1" >&2
    exit 1
}


echo "Building libunwindstack sources"
mkdir -p libunwind
for i in arm64-v8a armeabi-v7a x86 ; do
    mkdir -p libunwind/$i || fail "creating directory for libunwind"
    cd libunwind/$i || fail "swapping to directory for libunwind"
    cmake \
        -DANDROID_ABI=$i \
        -DANDROID_ARM_NEON=ON \
        -DANDROID_NATIVE_API_LEVEL=21 \
        -DANDROID_STL=gnustl_shared \
        -DCMAKE_TOOLCHAIN_FILE=../../../libunwindstack-ndk/cmake/android.toolchain.cmake \
        -DANDROID_NDK=${ANDROID_NDK} \
        ../../../libunwindstack-ndk/cmake || fail "cmake configuration failed"
    make || fail "Failed to build libunwindstack for $i"
    cd ../../
    
done



echo "Building Java sources...."

${JAVA_HOME}/bin/javac com/atakmap/jnicrash/*.java || fail "compiling java sources"
${JAVA_HOME}/bin/jar cf jnicrash.jar com/atakmap/jnicrash/*.class || fail "creating JAR file"

echo "Generating JNI headers"
${JAVA_HOME}/bin/javac -h jni/ com/atakmap/jnicrash/*.java || fail "generating JNI headers"
mv jni/com_atakmap_jnicrash_JNICrash.h jni/jjnicrash.h

${ANDROID_NDK}/ndk-build || fail "building native sources"

echo "Build success - copying files to ATAK"
cp jnicrash.jar ${ATAK}/libs/ || fail "copying jar file to ATAK"
for i in arm64-v8a armeabi-v7a x86 ; do
    cp libs/${i}/libjnicrash.so ${ATAK}/src/main/jniLibs/${i}/ || fail "copying native lib for $i"
done

echo "Build and install complete!"
