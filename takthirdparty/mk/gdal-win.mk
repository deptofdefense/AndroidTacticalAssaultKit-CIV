ifeq ($(and $(gdal_win_arch)),)
    $(error Required var not set)
endif

gdal_libfile=gdal200.$(LIB_SHAREDSUFFIX)
gdal_local_libfile=$(OUTDIR)/$(gdal_local_srcdir)/$(gdal_libfile)
gdal_out_lib=$(OUTDIR)/lib/$(gdal_libfile)
gdal_local_csharplibs=$(foreach ilib,gdalconst gdal ogr osr,$(OUTDIR)/$(gdal_local_srcdir)/swig/csharp/$(LIB_PREFIX)$(ilib)_csharp$(LIB_SHAREDSUFFIX) $(OUTDIR)/$(gdal_local_srcdir)/swig/csharp/$(LIB_PREFIX)$(ilib)_wrap$(LIB_SHAREDSUFFIX))
gdal_out_csharplibs=$(foreach ilib,gdalconst gdal ogr osr,$(OUTDIR)/lib/$(LIB_PREFIX)$(ilib)_csharp$(LIB_SHAREDSUFFIX) $(OUTDIR)/lib/$(LIB_PREFIX)$(ilib)_wrap$(LIB_SHAREDSUFFIX))
gdal_local_javalibs=$(foreach ilib,gdalalljni,$(OUTDIR)/$(gdal_local_srcdir)/swig/java/$(LIB_PREFIX)$(ilib)$(LIB_SHAREDSUFFIX))
gdal_out_javalibs=$(foreach ilib,gdalalljni,$(OUTDIR)/lib/$(LIB_PREFIX)$(ilib)$(LIB_SHAREDSUFFIX))
gdal_extra_swiglibs=$(gdal_out_csharplibs)
include mk/gdal-common.mk


gdalkdu_rd_suffix_release=R
gdalkdu_rd_suffix_debug=D
gdalkdu_rd_suffix=$(gdalkdu_rd_suffix_$(BUILD_TYPE))

gdal_kdu_yes=KAKDIR='$(OUTDIR_WIN)\\kdu'                                     \
KAKLIB='$(OUTDIR_WIN)\\kdu_bin\\lib_$(gdal_win_arch)\\kdu_v64$(gdalkdu_rd_suffix).lib' \
KAKCFLAGS="/DKDU_MAJOR_VERSION=6"                                            \
KAKSRC='$(OUTDIR_WIN)\\kdu_bin'                                                  \
KAKOBJDIR='$(OUTDIR_WIN)\\kdu_bin\v6_generated_$(gdal_win_arch)'                 \

gdal_kdu=$(gdal_kdu_$(GDAL_USE_KDU))

gdal_win64_win64=WIN64=yes
gdal_win64_win32=

gdal_pdfium_libdir=$(OUTDIR_CYGSAFE)/pdfium/lib

gdal_config=MRSID_DSDK_DIR=$(OUTDIR_CYGSAFE)/mrsid                           \
ZLIB_EXTERNAL_LIB=1                                                          \
ZLIB_INC="-I$(OUTDIR_CYGSAFE)/include /DZLIB_WINAPI /DZLIB_DLL"              \
ZLIB_LIB="$(OUTDIR_CYGSAFE)/lib/zlibwapi.lib"                                \
LIBICONV_DIR=$(OUTDIR_CYGSAFE)/libiconv                                      \
LIBICONV_INCLUDE=-I$(OUTDIR_CYGSAFE)/include                                 \
LIBICONV_LIBRARY=$(OUTDIR_CYGSAFE)/lib/iconv.dll.lib                         \
LIBICONV_CFLAGS=-DICONV_CONST=                                               \
LIBKML_DIR=$(OUTDIR_CYGSAFE)/libkml                                          \
LIBKML_INCLUDE="-I$(OUTDIR_CYGSAFE)/libkml/src -I$(OUTDIR_CYGSAFE)/libkml/third_party/boost_1_34_1" \
LIBKML_LIBRARY=$(OUTDIR_CYGSAFE)/libkml/msvc/$(gdal_kml_archdir)/$(BUILD_TYPE) \
LIBKML_LIBS="$$LIBKML_LIBRARY/libkmlbase.lib                                 \
                   $$LIBKML_LIBRARY/libkmlconvenience.lib                    \
                   $$LIBKML_LIBRARY/libkmldom.lib                            \
                   $$LIBKML_LIBRARY/libkmlengine.lib                         \
                   $$LIBKML_LIBRARY/libkmlregionator.lib                     \
                   $$LIBKML_LIBRARY/libkmlxsd.lib                            \
                   $$LIBKML_LIBRARY/minizip_static.lib                       \
                   $(OUTDIR_CYGSAFE)/lib/libexpat.lib                        \
                   $$LIBKML_DIR/third_party/uriparser-0.7.5.win32/$(BUILD_TYPE)/uriparser.lib \
                   $(OUTDIR_CYGSAFE)/lib/zlibwapi.lib"                       \
$(gdal_kdu)                                                                  \
MRSID_RASTER_DIR=$(OUTDIR_CYGSAFE)/mrsid                                     \
MRSID_RDLLBUILD=YES                                                          \
PDFIUM_ENABLED=YES                                                           \
PDFIUM_CFLAGS="-I$(OUTDIR_CYGSAFE)/pdfium/ -I$(OUTDIR_CYGSAFE)/pdfium/public -I$(OUTDIR_CYGSAFE)/pdfium/core" \
PDFIUM_LIBS="$(gdal_pdfium_libdir)/pdfium.lib $(gdal_pdfium_libdir)/bigint.lib $(gdal_pdfium_libdir)/fdrm.lib $(gdal_pdfium_libdir)/formfiller.lib $(gdal_pdfium_libdir)/fpdfapi.lib $(gdal_pdfium_libdir)/fpdfdoc.lib $(gdal_pdfium_libdir)/fpdftext.lib $(gdal_pdfium_libdir)/freetype.lib $(gdal_pdfium_libdir)/fxcodec.lib $(gdal_pdfium_libdir)/fxcrt.lib $(gdal_pdfium_libdir)/fxedit.lib $(gdal_pdfium_libdir)/fxge.lib $(gdal_pdfium_libdir)/pdfwindow.lib $(gdal_pdfium_libdir)/fx_agg.lib $(gdal_pdfium_libdir)/fx_lcms2.lib $(gdal_pdfium_libdir)/fx_libjpeg.lib $(gdal_pdfium_libdir)/fx_libopenjpeg.lib $(gdal_pdfium_libdir)/fx_zlib.lib gdi32.lib kernel32.lib advapi32.lib" \
PROJ_DIR=$(OUTDIR_CYGSAFE)/proj                                              \
PROJ_INCLUDE=-I$(OUTDIR_CYGSAFE)/proj/src                                    \
PROJ_LIBRARY=$(OUTDIR_CYGSAFE)/lib/proj_i.lib                                \
CURL_DIR="$(OUTDIR_CYGSAFE)/curl"                                            \
CURL_INC="-I$(OUTDIR_CYGSAFE)/curl/include"                                  \
CURL_LIB="$(OUTDIR_CYGSAFE)/lib/libcurl.lib wsock32.lib wldap32.lib winmm.lib" \
EXPAT_DIR="$(OUTDIR_CYGSAFE)/expat"                                          \
EXPAT_INCLUDE="-I$(OUTDIR_CYGSAFE)/include"                                  \
EXPAT_LIB=$(OUTDIR_CYGSAFE)/lib/libexpat.lib                                 \
SQLITE_INC="-I$(OUTDIR_CYGSAFE)/include"                                     \
SQLITE_LIB=$(OUTDIR_CYGSAFE)/lib/sqlite3_i.lib                               \
SWIG='C:\\swigwin-1.3.31\\swig.exe'                                          \
OGDIDIR=$(OUTDIR_CYGSAFE)                                                    \
OGDI_INCLUDE=$(OUTDIR_CYGSAFE)/include                                       \
OGDILIB=$(OUTDIR_CYGSAFE)/lib/ogdi.lib                                       \
$(gdal_win64_$(TARGET))                                                      \
GDAL_HOME='$(OUTDIR_WIN)'

# VS2015
gdal_nmake_msvcver_v140=MSVC_VER=1900
# VS2019
gdal_nmake_msvcver_v142=MSVC_VER=1900

gdal_nmake_msvcver=$(gdal_nmake_msvcver_$(VS_VER_MSB))

# This is phony because we always want to be invoking gdal make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: gdal_build gdal_csharp gdal_java
gdal_build: $(OUTDIR)/$(gdal_local_srcdir)
	cd $(OUTDIR)/$(gdal_local_srcdir) &&                             \
	    $(gdal_config)                                               \
	    $(VS_SETUP) nmake /f makefile.vc $(gdal_nmake_msvcver) default

gdal_csharp: $(OUTDIR)/$(gdal_local_srcdir)
	# Remove these files ; OsrPINVOKE has errors in it and the others
	# depend on it.  If these are actually needed (they no longer seem to be)
	# they will get remade by the nmake command below which re-runs swig
	cd $(OUTDIR)/$(gdal_local_srcdir)/swig &&                        \
	    for i in CoordinateTransformation Osr SpatialReference       \
	             OsrPINVOKE ; do                                     \
	                 rm -f csharp/ogr/$$i.cs ;                       \
	    done
	cd $(OUTDIR)/$(gdal_local_srcdir)/swig &&                        \
	    $(gdal_config)                                               \
	    $(VS_SETUP) nmake /f makefile.vc $(gdal_nmake_msvcver) csharp

gdal_java: $(OUTDIR)/$(gdal_local_srcdir)
	cd $(OUTDIR)/$(gdal_local_srcdir)/swig &&                        \
	    $(gdal_config)                                               \
	    $(VS_SETUP) nmake /f makefile.vc $(gdal_nmake_msvcver) java
	cd $(OUTDIR)/$(gdal_local_srcdir)/swig/java && ant

$(gdal_local_libfile): gdal_build
	@echo "gdal built"

$(gdal_local_csharplibs): gdal_csharp
	@echo "gdal csharp Bindings built"

$(gdal_local_javalibs): gdal_java
	@echo "gdal java Bindings built"

$(gdal_out_lib): $(gdal_local_libfile)
	cd $(OUTDIR)/$(gdal_local_srcdir) &&                             \
	    $(gdal_config)                                               \
	    $(VS_SETUP) nmake /f makefile.vc $(gdal_nmake_msvcver) devinstall install

$(gdal_out_csharplibs): $(gdal_local_csharplibs)
	cd $(OUTDIR)/$(gdal_local_srcdir)/swig/csharp &&                 \
	    $(gdal_config)                                               \
	    $(VS_SETUP) nmake /f makefile.vc $(gdal_nmake_msvcver) install

$(gdal_out_javalibs): $(gdal_local_javalibs)
	cd $(OUTDIR)/$(gdal_local_srcdir)/swig/java &&                 \
	    $(gdal_config)                                               \
	    $(VS_SETUP) nmake /f makefile.vc $(gdal_nmake_msvcver) install


