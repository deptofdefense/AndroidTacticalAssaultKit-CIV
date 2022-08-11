include mk/libxml2-common.mk

ifeq ($(and $(libxml2_CFLAGS),$(libxml2_installtargets)),)
    $(error Required var not set)
endif

$(libxml2_configtouchfile): $(libxml2_srctouchfile)
	cd $(OUTDIR)/$(libxml2_srcdir) &&                   \
		CFLAGS="$(libxml2_CFLAGS)"                      \
		LDFLAGS="$(libxml2_LDFLAGS)"                    \
		CC="$(CC)"                                      \
		CPP="$(CPP)"                                    \
		CXX="$(CXX)"                                    \
		$(if $(libxml2_LIBS),LIBS="$(libxml2_LIBS)",)   \
		./configure                                     \
		$(CONFIGURE_TARGET)                             \
		$(CONFIGURE_$(BUILD_TYPE))                      \
		--with-iconv$(if $(libiconv_skip_build),,=$(OUTDIR)) \
		--disable-shared                                \
		--enable-static                                 \
		--without-python                                \
        --without-lzma                                  \
        --without-zlib                                  \
		--prefix=$(OUTDIR_CYGSAFE)
	touch $@

# This is phony because we always want to be invoking libxml2 make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: libxml2_build
libxml2_build: $(libxml2_configtouchfile)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(libxml2_srcdir) $(libxml2_buildtarget)

$(libxml2_src_lib): libxml2_build
	@echo "Libxml built"

$(libxml2_out_lib): $(libxml2_src_lib)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(libxml2_srcdir) \
		mkinstalldirs="mkdir -p"                      \
		$(libxml2_installtargets)
	cd $(OUTDIR)/lib && ( test "`echo *.la`" = "*.la" && true || cd $(OUTDIR)/lib && for i in *.la ; do dos2unix $$i ; done )

