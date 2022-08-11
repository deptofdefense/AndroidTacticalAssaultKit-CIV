ifeq ($(and $(geos_CFLAGS)),)
    $(error Required var not set)
endif

include mk/geos-common.mk

geos_configtouchfile=$(OUTDIR)/$(geos_srcdir)/.configured


$(geos_configtouchfile): $(geos_srctouchfile)
	cd $(OUTDIR)/$(geos_srcdir) &&                   \
		CFLAGS="$(geos_CFLAGS)"                      \
		CXXFLAGS="$(geos_CXXFLAGS)"                  \
		LDFLAGS="$(geos_LDFLAGS)"                    \
		CC="$(CC)"                                   \
		CPP="$(CPP)"                                 \
		CXX="$(CXX)"                                 \
		$(if $(geos_LIBS),LIBS="$(geos_LIBS)",)      \
		./configure                                  \
		$(CONFIGURE_TARGET)                          \
		$(CONFIGURE_$(BUILD_TYPE))                   \
		--disable-shared                             \
		--prefix=$(OUTDIR_CYGSAFE)
	touch $@

# This is phony because we always want to be invoking geos make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: geos_build
geos_build: $(geos_configtouchfile)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(geos_srcdir)

$(geos_src_lib): geos_build
	@echo "geos built"

$(geos_out_lib): $(geos_src_lib)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(geos_srcdir)   \
		mkinstalldirs="mkdir -p"                     \
		install
	cd $(OUTDIR)/lib && ( test "`echo *.la`" = "*.la" && true || cd $(OUTDIR)/lib && for i in *.la ; do dos2unix $$i ; done )

