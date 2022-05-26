ifeq ($(and $(libkml_win_platform)),)
    $(error Required var not set)
endif

libkml_libfiles:=$(foreach ilib,kmlbase kmlconvenience kmldom kmlengine kmlregionator kmlxsd,lib$(ilib).$(LIB_STATICSUFFIX)) \
                  minizip_static.$(LIB_STATICSUFFIX)
libkml_out_libs=$(foreach ilib,$(libkml_libfiles) zlib.lib uriparser.lib,$(OUTDIR)/lib/$(ilib))
libkml_src_lib_dir=$(OUTDIR)/$(libkml_srcdir)/msvc/$(libkml_win_arch_dir)/$(BUILD_TYPE)
libkml_src_libs=$(foreach ilib,$(libkml_libfiles),$(libkml_src_lib_dir)/$(ilib)) \
                $(OUTDIR)/$(libkml_srcdir)/third_party/zlib-1.2.3.win32/lib/zlib.lib \
                $(OUTDIR)/$(libkml_srcdir)/third_party/uriparser-0.7.5/win32/uriparser.lib

include mk/libkml-common.mk


libkml_mz_dir=$(OUTDIR)/$(libkml_srcdir)/third_party/zlib-1.2.3/contrib/minizip
libkml_mz_oldproj=minizip_static.vcproj
libkml_mz_newproj=minizip_static.vcxproj
libkml_mz_lib=$(libkml_src_lib_dir)/minizip_static.lib

libkml_urip_dir=$(OUTDIR)/$(libkml_srcdir)/third_party/uriparser-0.7.5/win32/Visual_Studio_2005
libkml_urip_oldproj=uriparser.vcproj
libkml_urip_newproj=uriparser.vcxproj

libkml_win32_dir=$(OUTDIR)/$(libkml_srcdir)/msvc

libkml_patch_win_mz=$(DISTFILESDIR)/libkml-pgsc-win-mz-$(VS_VER_MSB).patch
libkml_patch_win_urip=$(DISTFILESDIR)/libkml-pgsc-win-urip-$(VS_VER_MSB).patch
libkml_patch_win=$(DISTFILESDIR)/libkml-pgsc-win-$(VS_VER_MSB).patch


$(libkml_mz_dir)/.libkml_mz_patched: $(libkml_mz_dir)/$(libkml_mz_oldproj) \
                                     $(libkml_patch_win_mz)
	patch -p1 --binary -d $(OUTDIR)/$(libkml_srcdir) < $(libkml_patch_win_mz)
	touch $@

$(libkml_urip_dir)/.libkml_urip_patched: $(libkml_urip_dir)/$(libkml_urip_oldproj) \
                                           $(libkml_patch_win_urip)
	cd $(libkml_urip_dir) && rm -f uriparser.sln
	patch -p1 -d $(OUTDIR)/$(libkml_srcdir) < $(libkml_patch_win_urip)
	touch $@

libkml_msbuild_opts=/p:Configuration=$(BUILD_TYPE) /p:Platform=$(libkml_win_platform) /t:Build


# These are phony because we always want to be invoking libkml make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: libkml_build libkml_build_minizip libkml_build_uriparser
libkml_build_minizip: $(libkml_mz_dir)/.libkml_mz_patched
	cd $(libkml_mz_dir) && $(VS_SETUP) MSBuild minizip.sln              \
	    $(libkml_msbuild_opts)
	mkdir -p $(libkml_src_lib_dir)
	$(CP) $(libkml_mz_dir)/$(libkml_win_arch_dir)/$(BUILD_TYPE)/minizip_static.lib             \
	    $(libkml_mz_lib)

libkml_build_uriparser: $(libkml_urip_dir)/.libkml_urip_patched
	cd $(libkml_urip_dir) && $(VS_SETUP) MSBuild $(libkml_urip_newproj) \
	    $(libkml_msbuild_opts)
	$(CP) $(libkml_urip_dir)/../uriparser.lib                           \
	    $(OUTDIR)/$(libkml_srcdir)/third_party/uriparser-0.7.5.win32/$(BUILD_TYPE)/

$(libkml_win32_dir)/.libkmlwin.patched: $(libkml_patch_win)
	patch -p1 -d $(OUTDIR)/$(libkml_srcdir) < $(libkml_patch_win)
	touch $@

libkml_build: $(libkml_srctouchfile) $(libkml_win32_dir)/.libkmlwin.patched libkml_build_minizip libkml_build_uriparser
	cd $(libkml_win32_dir) && $(VS_SETUP) msbuild libkml.sln            \
	    $(libkml_msbuild_opts)

$(libkml_src_libs): libkml_build
	@echo "LibKML built"

libkml_oid=$(OUTDIR)/include/libkml

$(libkml_out_libs): $(libkml_src_libs)
	mkdir -p $(libkml_oid)/kml
	cd $(libkml_oid)/kml && $(CP) $(OUTDIR)/$(libkml_srcdir)/src/kml/*.h .
	mkdir -p $(libkml_oid)/kml/base
	cd $(libkml_oid)/kml/base && $(CP) $(OUTDIR)/$(libkml_srcdir)/src/kml/base/*.h .
	mkdir -p $(libkml_oid)/kml/convenience
	cd $(libkml_oid)/kml/convenience &&                    \
            $(CP) $(OUTDIR)/$(libkml_srcdir)/src/kml/convenience/*.h .
	mkdir -p $(libkml_oid)/kml/dom
	cd $(libkml_oid)/kml/dom && $(CP) $(OUTDIR)/$(libkml_srcdir)/src/kml/dom/*.h .
	mkdir -p $(libkml_oid)/kml/engine
	cd $(libkml_oid)/kml/engine && $(CP) $(OUTDIR)/$(libkml_srcdir)/src/kml/engine/*.h .
	mkdir -p $(libkml_oid)/kml/regionator
	cd $(libkml_oid)/kml/regionator &&                     \
            $(CP) $(OUTDIR)/$(libkml_srcdir)/src/kml/regionator/*.h .
	mkdir -p $(libkml_oid)/kml/xsd
	cd $(libkml_oid)/kml/xsd && $(CP) $(OUTDIR)/$(libkml_srcdir)/src/kml/xsd/*.h .
	cd $(libkml_oid) && $(CP) -r $(OUTDIR)/$(libkml_srcdir)/third_party/boost_1_34_1/boost .
	mkdir -p $(libkml_oid)/zlib-1.2.3/contrib/minizip
	cd $(libkml_oid)/zlib-1.2.3/contrib/minizip && $(CP) $(OUTDIR)/$(libkml_srcdir)/third_party/zlib-1.2.3/contrib/minizip/*.h .
	for i in $^ ; do $(CP) $$i $(OUTDIR)/lib/ ; done
