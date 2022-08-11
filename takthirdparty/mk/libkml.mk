ifeq ($(and $(libkml_CFLAGS)),)
    $(error Required var not set)
endif

libkml_libfiles:=$(foreach ilib,kmlbase kmlconvenience kmldom kmlengine kmlregionator kmlxsd minizip uriparser,$(LIB_PREFIX)$(ilib).$(LIB_STATICSUFFIX))
libkml_out_libs=$(foreach ilib,$(libkml_libfiles),$(OUTDIR)/lib/$(ilib))
libkml_src_libs=$(foreach ilib,$(libkml_libfiles),$(OUTDIR)/$(libkml_srcdir)/$(ilib))

include mk/libkml-common.mk

libkml_actouchfile=$(OUTDIR)/$(libkml_srcdir)/.aconfigured
libkml_configtouchfile=$(OUTDIR)/$(libkml_srcdir)/.configured
libkml_patches=$(DISTFILESDIR)/libkml-pgsc-postac.patch $(libkml_EXTRAPATCHES)

# These are needed to turn off warnings (treated as errors)
# of deprecated c++ construct use in libkml
# Seen with gcc 6.4.0
libkml_nowarn=-Wno-deprecated -Wno-deprecated-declarations

$(libkml_actouchfile): $(libkml_srctouchfile)
	cd $(OUTDIR)/$(libkml_srcdir) && chmod 755 autogen.sh && ./autogen.sh
	for p in $(libkml_patches) ; do \
		patch -p0 -N -d $(OUTDIR)/$(libkml_srcdir) < $$p || exit 1 ; \
	done
	cd $(OUTDIR)/$(libkml_srcdir) && chmod 755 configure
	# Substitute directory variables used in include statements in
	# makefiles to work around bug in ios/apple autoconf
	sed -e 's,$$(googletest),googletest-r108,g' \
		-e 's,$$(uriparser),uriparser-0.7.5,g' \
		$(OUTDIR)/$(libkml_srcdir)/third_party/Makefile.in \
		> $(OUTDIR)/$(libkml_srcdir)/third_party/Makefile.in.fixed
	mv $(OUTDIR)/$(libkml_srcdir)/third_party/Makefile.in.fixed \
		$(OUTDIR)/$(libkml_srcdir)/third_party/Makefile.in
	touch $@

$(libkml_configtouchfile): $(libkml_actouchfile)
	cd $(OUTDIR)/$(libkml_srcdir) &&                                     \
		CFLAGS="$(libkml_CFLAGS)"                                    \
		CXXFLAGS="-std=c++0x $(libkml_CXXFLAGS) $(libkml_nowarn)"    \
		LDFLAGS="$(libkml_LDFLAGS)"                                  \
		CC="$(CC)"                                                   \
		CPP="$(CPP)"                                                 \
		CXX="$(CXX)"                                                 \
		CURL_CONFIG=$(OUTDIR)/bin/curl-config                        \
		$(if $(proj_LIBS),LIBS="$(proj_LIBS)",)                      \
		./configure                                                  \
		$(CONFIGURE_TARGET)                                          \
		$(CONFIGURE_$(BUILD_TYPE))                                   \
		--with-expat-include-dir=$(call PATH_CYGSAFE,$(OUTDIR)/include) \
		--with-expat-lib-dir=$(call PATH_CYGSAFE,$(OUTDIR)/lib)      \
		--disable-shared                                             \
		--prefix=$(OUTDIR_CYGSAFE)
	touch $@

# This is phony because we always want to be invoking libkml make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: libkml_build
libkml_build: $(libkml_configtouchfile)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(libkml_srcdir)

$(libkml_src_libs): libkml_build
	@echo "LibKML built"

$(libkml_out_libs): $(libkml_src_libs)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(libkml_srcdir) install
	cd $(OUTDIR)/lib && ( test "`echo *.la`" = "*.la" && true || cd $(OUTDIR)/lib && for i in *.la ; do dos2unix $$i ; done )
