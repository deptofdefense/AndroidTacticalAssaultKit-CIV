# Sources for the following packages are already in the repository:
#  org.apache.commons.lang
#  com.arrowmaker.android.maps.layers.RangeSeekBar
#  org.draper.android.gui.ImportFileBrowser
#  opencsv.CSVReader
#  KMLMatcher
#
# The following packages are commercial:
#  jpgscmedia
#  libltidsdk

ifndef ANDROID_HOME
   $(error $$ANDROID_HOME is not set, maven will fail in obtuse ways)
endif

ifndef JAVA_HOME
   $(error $$JAVA_HOME is not set, maven will fail in obtuse ways)
endif

JAVAVERSION=$(shell $(JAVA_HOME)/bin/java -version 2>&1 )
ifneq (1.8, $(findstring 1.8, $(JAVAVERSION)))
    $(error wrong java version [needs oracle java 8] pointed to by $$JAVA_HOME, maven will fail in obtuse ways)
endif


# default target
.PHONY:			all
all:			build

################################################################ the things we need to build and install

AChartEngine =		AChartEngine/build/libs/achartengine-1.2.0.jar
ACRA =			ACRA/target/acra-4.6.1.jar
DataDroid =		DataDroid/build/intermediates/bundles/default/classes.jar

commolibs :=		libcommoncommojni.so
commojars :=		jcommoncommo.jar
commoarm   =		$(foreach file,$(commolibs),GDAL/raster/takthirdparty/builds/android-armeabi-v7a-release/lib/$(file)) 
commox86   =		$(foreach file,$(commolibs),GDAL/raster/takthirdparty/builds/android-x86-release/lib/$(file)) 
commojarx86   = 		$(foreach file,$(commojars),GDAL/raster/takthirdparty/builds/android-x86-release/java/$(file)) 
commojararm   = 		$(foreach file,$(commojars),GDAL/raster/takthirdparty/builds/android-armeabi-v7a-release/java/$(file)) 

GDALlibs :=		libogrjni.so					\
			libgdaljni.so					\
			libgdal.so					\
			libltidsdk.so					\
			libosrjni.so					\
			libgdalconstjni.so				\
			libproj.so
GDALjars :=		gdal.jar gdaldata.jar

GDALarm =		$(foreach file,$(GDALlibs),GDAL/raster/takthirdparty/builds/android-armeabi-v7a-release/lib/$(file)) \
			GDAL/raster/pgsc/agdaljni/gdalext/armeabi-v7a-libs/armeabi-v7a/libgdalext.so
# jars only in x86 per prior revision of this Makefile
GDALx86 =		$(foreach file,$(GDALlibs),GDAL/raster/takthirdparty/builds/android-x86-release/lib/$(file)) \
			GDAL/raster/pgsc/agdaljni/gdalext/x86-libs/x86/libgdalext.so					\
                        $(foreach file,$(GDALjars),GDAL/raster/takthirdparty/builds/android-x86-release/java/$(file)) \
                        GDAL/raster/pgsc/agdaljni/gdalext/armeabi-v7a-libs/gdalext.jar

#Jackcess =		Jackcess/target/jackcess-2.1.6.jar
Jackcess =		Jackcess/target/jackcess-1.2.14.3.jar
MrSidJNI =		MrSidJNI/libs/armeabi-v7a/libltidsdk.so \
			MrSidJNI/libs/armeabi-v7a/libMrsidLibrary.so \
			MrSidJNI/libs/x86/libltidsdk.so \
			MrSidJNI/libs/x86/libMrsidLibrary.so
OpenNMEA =		android-opennmea/opennmea.jar
SerialDriver =		android-serial/android-serial.jar
PRIJNI =		PRIJNI/java/bin/prijni.jar \
			PRIJNI/libs/armeabi-v7a/libgnustl_shared.so \
			PRIJNI/libs/armeabi-v7a/libprijni.so \
			PRIJNI/libs/x86/libgnustl_shared.so \
			PRIJNI/libs/x86/libprijni.so
Rover =			Rover/rover.jar
SimpleKML =		SimpleKML/build/intermediates/bundles/default/classes.jar
SimpleXML =		SimpleXML/jar/simple-xml-2.7.1.jar
Spatialite_libs =	libjsqlite.so libcharset.so libiconv.so libproj.so
Spatialite =		$(foreach file,$(Spatialite_libs),GDAL/raster/takthirdparty/builds/android-armeabi-v7a-release/lib/$(file)) \
			$(foreach file,$(Spatialite_libs),GDAL/raster/takthirdparty/builds/android-x86-release/lib/$(file))

# staging locations
STAGEDIR =		staging
ATAKLIBS =		$(STAGEDIR)/ATAK/libs
AMELIBS =		$(STAGEDIR)/ATAKMapEngine/libs
MCILIBS =		$(STAGEDIR)/MapCoreInterfaces/libs

# packages whose libraries are installed as is in $(ATAKLIBS)
ATAKPKGS =		ACRA \
                        Jackcess \
			OpenNMEA \
			SerialDriver \
			Rover \
			SimpleXML \

# packages whose libraries are installed with name changes; each of these need an explicit *_stage rule
# attempt to stage in order java first followed by native
CUSTPKGS =		AChartEngine \
			DataDroid \
			SimpleKML \
			GDALarm \
			GDALx86 \
			commoarm \
			commox86 \
			MrSidJNI \
			PRIJNI \
			Spatialite

# packages whose sources are in ATAK's svn repository
SVNPKGS =		MrSidJNI PRIJNI SimpleKML

# packages whose sources are in PAR's cvs repository
CVSPKGS =		GDALarm GDALx86 Rover Spatialite commoarm commox86

# packages whose sources are downloaded from the Internet
DLPKGS =		$(filter-out $(CVSPKGS) $(SVNPKGS),$(PACKAGES))

# packages with native code
NATIVEPKGS =		GDALarm GDALx86 MrSidJNI PRIJNI Spatialite commoarm commox86
NATIVEBUILDS =		$(patsubst %,%_build,$(NATIVEPKGS))

# Java-only packages
JAVAPKGS =		$(filter-out $(NATIVEPKGS),$(PACKAGES))
JAVABUILDS =		$(patsubst %,%_build,$(JAVAPKGS))

PACKAGES =		$(ATAKPKGS) $(CUSTPKGS)
BUILDS =		$(patsubst %,%_build,$(PACKAGES))
CLEANS =		$(patsubst %,%_clean,$(PACKAGES))
DOWNLOADS =		$(patsubst %,%.tgz,$(DLPKGS))
STAGES =		$(patsubst %,%_stage,$(PACKAGES))

DUMMYDIR =		.dummies
MARKDONE =		@mkdir -p $(DUMMYDIR) && touch $(DUMMYDIR)/$@

VPATH =			downloads $(DUMMYDIR)

$(foreach pkg,$(PACKAGES),$(eval $($(pkg)):	$(pkg)_build))
$(foreach pkg,$(PACKAGES),$(eval $(pkg)_clean:;	$(RM) $($(pkg)) $(DUMMYDIR)/$(pkg)_build))
$(foreach pkg,$(ATAKPKGS),$(eval $(pkg)_stage: $($(pkg)); cp $($(pkg)) $(ATAKLIBS)))
$(foreach pkg,$(PACKAGES),$(eval PRODUCTS += $($(pkg))))
$(foreach pkg,$(PACKAGES),$(eval $(pkg).tgz: PKG = $(subst .tgz,,$(pkg))))
$(foreach pkg,$(filter-out AChartEngine,$(DLPKGS)),$(eval $(pkg): | $(pkg).tgz; tar -xzf downloads/$(pkg).tgz))

.PHONY:			build clean distclean get help install pristine stage $(CLEANS) $(STAGES)
build:			$(BUILDS)
buildjava:		$(JAVABUILDS)
buildnative:		$(NATIVEBUILDS)
get:			$(PACKAGES)
stage:			$(STAGEDIR) $(STAGES)
clean:;			$(RM) -r $(PRODUCTS) $(DUMMYDIR)
distclean:		clean
			$(RM) -r $(PACKAGES)
			svn up -q
pristine:		distclean
			$(RM) -r $(STAGEDIR)
download:		$(DOWNLOADS)

.PHONY:			$(STAGEDIR)
$(STAGEDIR):;		mkdir -p \
				$(ATAKLIBS)/armeabi-v7a \
				$(ATAKLIBS)/x86 \
				$(AMELIBS)/armeabi-v7a \
				$(AMELIBS)/x86 \
				$(MCILIBS)

install:		stage
			cd $(STAGEDIR); \
			find . -type f -print \
			| while read file; do \
				cp $$file ../../$$file; \
			done

# $(call GREPSTRING, search-string, word-list)
GREPSTRING =		$(strip $(foreach w, $2, $(if $(findstring $1, $w), $w)))

################################################################ Java-only packages

# Use this command for svn updates so they can be run in parallel (make -j).
SVNUPDATE =		until svn up -q $@ 2>/dev/null; do sleep 2; done

AChartEngine:		| AChartEngine.tgz
			tar -xzf $| \
			&& $(RM) -r $@/demo/*
AChartEngine_build:	| AChartEngine
			cd $| \
			&& ( patch -Np 0 < ../patches/achartengine.build.patch & ) \
			&& ant clean \
			&& ant
			$(MARKDONE)
AChartEngine_stage:	$(AChartEngine)
			cp $(AChartEngine) $(ATAKLIBS)/achartengine-1.2.0r569.jar

# remove NaiveTrustManager so that this is not flagged as a security risk even though we do not make use of it.
ACRA_build:		| ACRA
			cd $| \
			&& ( rm -fr src/main/java/org/acra/util/NaiveTrustManager.java & ) \
			&& ( rm -fr src/main/java/org/acra/util/FakeSocketFactory.java & ) \
			&& ( rm -fr src/test/java/org/acra/util/HttpRequestTest.java & ) \
			&& ( patch -Np 0 < ../patches/acra_4.6.1_httprequest.patch & ) \
			&& ( patch -Np 0 < ../patches/acra-03e237f.patch & ) \
			&& mvn clean package
			$(MARKDONE)


DataDroid_build:	| DataDroid
			cd $| \
			&& ./gradlew assembleRelease
			$(MARKDONE)
DataDroid_stage:	$(DataDroid)
			cp $(DataDroid) $(ATAKLIBS)/datadroid-2.1.2.jar

Jackcess_build:		| Jackcess
			( patch -Np 0 < patches/rowid-1.2.14.3.patch & )
			( patch -Np 0 < patches/Database-1.2.14.3.patch & )
#			( patch -Np 0 < patches/rowid-2.1.6.patch & )
#			( patch -Np 0 < patches/Database-2.1.6.patch & )
			echo "removing test cases"
#			( rm -fr ./Jackcess/src/test & )
			( rm -fr ./Jackcess/test & )
			cd $| \
			&& mvn clean package
			$(MARKDONE)

OpenNMEA_build:		| android-opennmea
			cd $| \
			&& ./gradlew assembleRelease \
			&& cp ./build/intermediates/packaged-classes/release/classes.jar opennmea.jar
			$(MARKDONE)

SerialDriver_build:	| android-serial
			cd $| \
			&& ./gradlew assembleRelease \
			&& cp ./build/intermediates/packaged-classes/release/classes.jar android-serial.jar
			$(MARKDONE)


Rover:;			cvs -Q co -d $@ coolfire
Rover_build:		| Rover
			cd $| \
			&& ant clean \
			&& ant jar
			$(MARKDONE)

SimpleKML:;		$(SVNUPDATE)
SimpleKML_build:	| SimpleKML $(SimpleXML)
			cd $(word 1,$|) \
			&& mkdir -p libs \
			&& cp ../$(SimpleXML) libs \
			&& ./gradlew assembleRelease 
			$(MARKDONE)
SimpleKML_stage:	$(SimpleKML)
			cp $(SimpleKML) $(ATAKLIBS)/simple-kml.jar

SimpleXML_build:	| SimpleXML
			cd $| \
			&& ( patch -Np 0 < ../patches/simplexml.build.patch & ) \
			&& ( patch -Np 0 < ../patches/simplexml.lintfix.patch & ) \
			&& ( rm -fr ./src/org/simpleframework/xml/stream/StreamProvider.java & ) \
			&& ( rm -fr ./src/org/simpleframework/xml/stream/StreamReader.java & ) \
			&& ant clean \
			&& ant
			$(MARKDONE)

################################################################ packages with native code

.PHONY: commoarm commox86 GDALarm GDALx86 Spatialite
commoarm:
commox86:
GDALarm:
GDALx86:
Spatialite:

commoarm_build:;	$(MAKE) -j 1 $@2
			$(MARKDONE)
commoarm_build2:	| GDAL
			cd $|/raster/takthirdparty \
			&& SHELL=/bin/bash make TARGET=android-armeabi-v7a BUILD_TYPE=release build_commoncommo

commoarm_stage:		$(commoarm)
			cp $(commoarm) $(ATAKLIBS)/armeabi-v7a
			cp $(commojararm) $(ATAKLIBS)

commox86_build:;	$(MAKE) -j 1 $@2
			$(MARKDONE)
commox86_build2:	| GDAL
			cd $|/raster/takthirdparty \
			&& SHELL=/bin/bash make TARGET=android-x86 BUILD_TYPE=release build_commoncommo

commox86_stage:		$(commox86)
			cp $(commox86) $(ATAKLIBS)/x86
			cp $(commojarx86) $(ATAKLIBS)



# GDALarm and GDALx86 fail to build in parallel (-j) even though they build successfully when run as two separate but concurrent
# makes. We work around the problem by building them with submakes.
GDAL:;			mkdir -p $@
			cd $@ && cvs -Q co raster && \
			    cvs -Q co PGSC_Utils && cvs -Q co commoncommo && \
			    git clone https://git.pargovernment.com/PAR-Government/pgscthread.git

GDALarm_build:;		$(MAKE) -j 1 $@2
			$(MARKDONE)
GDALarm_build2:		| GDAL
			cd $|/raster/takthirdparty \
			&& SHELL=/bin/bash make TARGET=android-armeabi-v7a BUILD_TYPE=release build_gdal
			cd $|/raster/pgsc/agdaljni/gdalext/jni && \
			make \
			    AGDAL_STAGE_DIR=../../../../takthirdparty/builds/android-armeabi-v7a-release \
			    TARGET=armeabi-v7a \
			    TOOLCHAIN=android-15-toolchain \
			    CONFIG_FLAGS="-O3 -mfloat-abi=softfp -mfpu=neon -march=armv7-a -mtune=cortex-a8 -ftree-vectorize -fstack-protector-all" \

GDALarm_stage:		$(GDALarm)
			cp $(GDALarm) $(AMELIBS)/armeabi-v7a

GDALx86_build:;		$(MAKE) -j 1 $@2
			$(MARKDONE)
GDALx86_build2:		| GDAL
			cd $|/raster/takthirdparty \
			&& SHELL=/bin/bash make TARGET=android-x86 BUILD_TYPE=release build_gdal
			cd $|/raster/pgsc/agdaljni/gdalext/jni && \
			make \
			    AGDAL_STAGE_DIR=../../../../takthirdparty/builds/android-x86-release \
			    TARGET=x86 \
			    TOOLCHAIN=androidx86-15-toolchain \
			    CONFIG_FLAGS="-O3 -mno-sse -fstack-protector-all"

GDALx86_stage:		$(GDALx86)
			cp $(filter %.jar,$(GDALx86)) $(AMELIBS)
			cp $(filter %.so,$(GDALx86)) $(AMELIBS)/x86

# MrSidJNI depends on ATAK.
../ATAKMapEngine/build/intermediates/classes/debug/com/lizardtech/android/mrsid/MrsidImageReaderImp.class:;
			@echo You must build ATAK before building MrSidJNI!
			@false

MrSidJNI:;		$(SVNUPDATE)
MrSidJNI_build:		| MrSidJNI ../ATAKMapEngine/build/intermediates/classes/debug/com/lizardtech/android/mrsid/MrsidImageReaderImp.class
			cd $(word 1,$|)/jni \
			&& tar xvf  android.tar.gz \
			&& $(MAKE) clean \
			&& $(MAKE) -j 1
			$(MARKDONE)
MrSidJNI_stage:		$(MrSidJNI)
			cp MrSidJNI/libs/armeabi-v7a/*.so $(AMELIBS)/armeabi-v7a
			cp MrSidJNI/libs/x86/*.so $(AMELIBS)/x86

PRIJNI:;		$(SVNUPDATE)
PRIJNI_build:		| PRIJNI
			cd $|/java \
			&& ant clean jar \
			&& cd ../jni \
			&& $(MAKE) clean \
			&& $(MAKE) -j 1
			$(MARKDONE)
PRIJNI_stage:		$(PRIJNI)
			cp PRIJNI/java/bin/prijni.jar $(AMELIBS)
			cp PRIJNI/libs/armeabi-v7a/libgnustl_shared.so $(ATAKLIBS)/armeabi-v7a
			cp PRIJNI/libs/x86/libgnustl_shared.so $(ATAKLIBS)/x86
			cp PRIJNI/libs/armeabi-v7a/libprijni.so $(AMELIBS)/armeabi-v7a
			cp PRIJNI/libs/x86/libprijni.so $(AMELIBS)/x86

Spatialite_build:	| GDAL
			cd $|/raster/takthirdparty \
			&& SHELL=/bin/bash make TARGET=android-armeabi-v7a BUILD_TYPE=release build_spatialite \
			&& SHELL=/bin/bash make TARGET=android-x86 BUILD_TYPE=release build_spatialite
			$(MARKDONE)
Spatialite_stage:	$(Spatialite)
			cp $(call GREPSTRING,/android-armeabi-v7a-release/,$(Spatialite)) $(AMELIBS)/armeabi-v7a
			cp $(call GREPSTRING,/android-x86-release/,$(Spatialite)) $(AMELIBS)/x86

################################################################ miscellany

help:;			@echo "$$HELPSTRING"

define HELPSTRING
# Retrieve, build, stage, clean all packages:
make get
make build				# default target
make stage
make clean

# Remove all packages (including those in SVN) in their entirety:
make distclean

# Remove everything including the staging directory:
make pristine

# Retrieve, build, stage, clean, remove a single package:
make PackageName			# assuming it does not exist
make PackageName_build
make PackageName_stage
make PackageName_clean
rm -rf PackageName

# Build all Java-only packages:
make buildjava

# Build all packages containing native code:
make buildnative

# Compare list of needed libraries with those we've built.
make missing

# Compare the file types of the libraries we've built with those checked in.
make compare

# Expected usage is to retrieve once and update rarely.
#
# Note that clean does not do an internal clean; it simply deletes the relevant
# objects to force a rebuild which does do an internal clean. This was done to
# simplify the makefile and is consistent with our primary purpose (Coverity).
endef
export HELPSTRING

################################################################ verification targets

missing:		stage
			cd ..; \
			for dir in ATAK ATAKMapEngine CoTService MapCoreInterfaces; do \
				cd $$dir/libs; \
				for file in *.jar; do \
					ls ../../thirdparty/$(STAGEDIR)/$$dir/libs/$$file; \
				done; \
				cd ../..; \
			done
			cd ..; \
			for dir in ATAK ATAKMapEngine; do \
				for sub in armeabi-v7a x86; do \
					cd $$dir/libs/$$sub; \
						for file in *.so; do \
							ls ../../../thirdparty/$(STAGEDIR)/$$dir/libs/$$sub/$$file; \
						done; \
					cd ../../..; \
				done; \
			done

compare:
			@cd $(STAGEDIR); \
			find . -name \*.jar -o -name \*.so \
			| while read path; do \
				this=`file -b $$path`; \
				that=`file -b ../../$$path`; \
				if [ "$$this" != "$$that" ]; then \
					echo $$path; \
					echo "$$this"; \
					echo "$$that"; \
					echo ""; \
				fi; \
			done

################################################################ Internet (open-source) downloads
# These are intended to be one-time operations unless updates are wanted.

downloads:;		mkdir -p $@

MKTARBALL =		tar -czf $@ --remove-files $(PKG)

AChartEngine.tgz:	| downloads
			cd $| \
			&& svn export -q -r 569 http://achartengine.googlecode.com/svn/trunk/achartengine $(PKG) \
			&& $(MKTARBALL)

ACRA.tgz:		| downloads
			cd $| \
			&& svn export -q https://github.com/ACRA/acra.git/tags/acra-4.6.1 $(PKG) \
			&& $(MKTARBALL)

DataDroid.tgz:		| downloads
			cd $| \
			&& svn export -q https://github.com/foxykeep/DataDroid.git/trunk $(PKG) \
			&& $(MKTARBALL)

Jackcess.tgz:		| downloads
			cd $| \
			&& svn export -q http://svn.code.sf.net/p/jackcess/code/jackcess/tags/jackcess-1.2.14.3 $(PKG) \
			&& $(MKTARBALL)
#Jackcess.tgz:		| downloads
#			cd $| \
#			&& svn export -q http://svn.code.sf.net/p/jackcess/code/jackcess/tags/jackcess-2.1.6 $(PKG) \
#			&& $(MKTARBALL)

SimpleXML.tgz:		| downloads
			cd $| \
			&& ZIPFILE=simple-xml-2.7.1.zip \
			&& curl -O http://iweb.dl.sourceforge.net/project/simple/$$ZIPFILE \
			&& unzip -q $$ZIPFILE \
			&& $(RM) $$ZIPFILE \
			&& mv simple-xml-2.7.1 $(PKG) \
			&& $(MKTARBALL)

