ifeq ($(and $(zlib_CFLAGS)),)
    $(error Required var not set)
endif

zlib_libfile=$(LIB_PREFIX)zlib.$(LIB_STATICSUFFIX)

include mk/zlib-common.mk

zlib_configtouchfile=$(OUTDIR)/$(zlib_srcdir)/.configured


$(zlib_configtouchfile): $(zlib_srctouchfile)
	cd $(OUTDIR)/$(zlib_srcdir) &&                   \
		CFLAGS="$(zlib_CFLAGS)"                      \
		CXXFLAGS="$(zlib_CXXFLAGS)"                  \
		LDFLAGS="$(zlib_LDFLAGS)"                    \
		AR="$(AR)"                                   \
		RANLIB="$(RANLIB)"                           \
		CC="$(CC)"                                   \
		CPP="$(CPP)"                                 \
		CXX="$(CXX)"                                 \
		$(if $(zlib_LIBS),LIBS="$(zlib_LIBS)",)      \
		./configure                                  \
		--static                                     \
		--prefix=$(OUTDIR_CYGSAFE)
	touch $@

# This is phony because we always want to be invoking zlib make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: zlib_build
zlib_build: $(zlib_configtouchfile)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(zlib_srcdir)

$(zlib_src_lib): zlib_build
	@echo "zlib built"

$(zlib_out_lib): $(zlib_src_lib)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(zlib_srcdir) install

zlib_clean:
	rm -rf $(OUTDIR)/$(zlib_srcdir)
	rm -f $(zlib_out_lib)
