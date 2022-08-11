ifeq ($(and $(libiconv_CFLAGS)),)
    $(error Required var not set)
endif

include mk/libiconv-common.mk

libiconv_configtouchfile=$(OUTDIR)/$(libiconv_srcdir)/.configured


$(libiconv_configtouchfile): $(libiconv_srctouchfile)
	cd $(OUTDIR)/$(libiconv_srcdir) &&                   \
		CFLAGS="$(libiconv_CFLAGS)"                      \
		CXXFLAGS="$(libiconv_CXXFLAGS)"                  \
		LDFLAGS="$(libiconv_LDFLAGS)"                    \
		CC="$(CC)"                                       \
		CPP="$(CPP)"                                     \
		CXX="$(CXX)"                                     \
		$(if $(libiconv_LIBS),LIBS="$(libiconv_LIBS)",)  \
		./configure                                      \
		$(CONFIGURE_TARGET)                              \
		$(CONFIGURE_$(BUILD_TYPE))                       \
		--disable-shared                                 \
		--enable-static                                  \
		--prefix=$(OUTDIR_CYGSAFE)
	touch $@

# This is phony because we always want to be invoking libiconv make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: libiconv_build
libiconv_build: $(libiconv_configtouchfile)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(libiconv_srcdir)

$(libiconv_src_lib): libiconv_build
	@echo "libiconv built"

# We do make install-lib here as "install" fails during iconv program
# linking on win32 build for android.  No need for it, so skip it
$(libiconv_out_lib): $(libiconv_src_lib)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(libiconv_srcdir)   \
		mkinstalldirs="mkdir -p"                         \
		install-lib
	cd $(OUTDIR)/lib && ( test "`echo *.la`" = "*.la" && true || cd $(OUTDIR)/lib && for i in *.la ; do dos2unix $$i ; done )
