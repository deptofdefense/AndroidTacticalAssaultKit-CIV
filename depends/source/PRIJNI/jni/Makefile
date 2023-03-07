LIBRARY = libprijni.so

LIBS = ../libs/armeabi-v7a/$(LIBRARY) ../libs/x86/$(LIBRARY) ../libs/arm64-v8a/$(LIBRARY)

ifeq ($(shell uname),Linux)
SEP = :
else ifeq ($(shell uname),Darwin)
SEP = :
else
SEP = ;
endif # shell

CLASSPATH = "../java/bin/classes"

all : release

clean :
	rm -f com_iai_pri_PRIJNI.h
	rm -f com_iai_pri_ElevationSegmentData.h
	rm -rf ../libs
	rm -rf ../obj

install : release
	cp ../libs/armeabi-v7a/$(LIBRARY) ../../../ATAKMapEngine/libs/armeabi-v7a
	cp ../libs/armeabi-v7a/libpri.so ../../../ATAKMapEngine/libs/armeabi-v7a
	cp ../libs/x86/$(LIBRARY) ../../../ATAKMapEngine/libs/x86
	cp ../libs/x86/libpri.so ../../../ATAKMapEngine/libs/x86

com_iai_pri_PRIJNI.cpp : com_iai_pri_PRIJNI.h
com_iai_pri_ElevationSegmentData.cpp : com_iai_pri_ElevationSegmentData.h

$(LIBS) : com_iai_pri_PRIJNI.cpp com_iai_pri_ElevationSegmentData.cpp
	unzip -q -o ../pri-android.zip -d ../pri-android

com_iai_pri_PRIJNI.h : 
	javac -classpath $(CLASSPATH) -h . ../java/src/com/iai/pri/ElevationPoint.java ../java/src/com/iai/pri/ElevationSegmentData.java ../java/src/com/iai/pri/PRI.java ../java/src/com/iai/pri/PRICorners.java ../java/src/com/iai/pri/PRIGroundPoint.java ../java/src/com/iai/pri/PRIImagePoint.java ../java/src/com/iai/pri/PRIJNI.java

com_iai_pri_ElevationSegmentData.h : 
	javac -classpath $(CLASSPATH) -h . ../java/src/com/iai/pri/ElevationPoint.java ../java/src/com/iai/pri/ElevationSegmentData.java

release : $(LIBS)
	$(ANDROID_NDK)/ndk-build