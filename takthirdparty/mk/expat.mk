ifeq ($(and $(expat_CFLAGS)),)
    $(error Required var not set)
endif

expat_libfile=$(LIB_PREFIX)expat.$(LIB_STATICSUFFIX)

include mk/expat-common.mk

expat_src_lib=$(OUTDIR)/$(expat_srcdir)/$(expat_libfile)

$(expat_configtouchfile): $(expat_srctouchfile)
	cd $(OUTDIR)/$(expat_srcdir)                   && \
		CFLAGS="$(expat_CFLAGS)"                      \
		LDFLAGS="$(expat_LDFLAGS)"                    \
		CC="$(CC)"                                    \
		CPP="$(CPP)"                                  \
		CXX="$(CXX)"                                  \
		$(if $(expat_LIBS),LIBS="$(expat_LIBS)",)     \
		./configure                                   \
		$(CONFIGURE_TARGET)                           \
		$(CONFIGURE_$(BUILD_TYPE))                    \
		--disable-shared                              \
		--prefix=$(OUTDIR_CYGSAFE)
	touch $@

# This is phony because we always want to be invoking expat make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: expat_build
expat_build: $(expat_configtouchfile)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(expat_srcdir)

$(expat_src_lib): expat_build
	@echo "Expat built"

$(expat_out_lib): $(expat_src_lib)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(expat_srcdir)   \
		mkinstalldirs="mkdir -p"                      \
		install
	cd $(OUTDIR)/lib && ( test "`echo *.la`" = "*.la" && true || cd $(OUTDIR)/lib && for i in *.la ; do dos2unix $$i ; done )
