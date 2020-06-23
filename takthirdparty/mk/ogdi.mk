ifeq ($(and $(ogdi_CFLAGS)),)
    $(error Required var not set)
endif

include mk/ogdi-common.mk

ogdi_configtouchfile=$(OUTDIR)/$(ogdi_srcdir)/.configured


$(ogdi_configtouchfile): $(ogdi_srctouchfile)
	cd $(OUTDIR)/$(ogdi_srcdir) &&                       \
		TOPDIR="$(OUTDIR_CYGSAFE)/$(ogdi_srcdir)"    \
		CFLAGS="$(ogdi_CFLAGS) -Wno-strict-overflow" \
		CXXFLAGS="$(ogdi_CXXFLAGS)"                  \
		LDFLAGS="$(ogdi_LDFLAGS)"                    \
		CC="$(CC)"                                   \
		CPP="$(CPP)"                                 \
		CXX="$(CXX)"                                 \
		RANLIB="$(RANLIB)"                           \
		./configure                                  \
		$(CONFIGURE_TARGET)                          \
		$(CONFIGURE_$(BUILD_TYPE))                   \
		--with-zlib=$(OUTDIR_CYGSAFE)                \
		--with-proj=$(OUTDIR_CYGSAFE)                \
		--with-expat=$(OUTDIR_CYGSAFE)               \
		--prefix=$(OUTDIR_CYGSAFE)
	touch $@

# This is phony because we always want to be invoking ogdi make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: ogdi_build
ogdi_build: $(ogdi_configtouchfile)
	@# Unset MAKEFLAGS as passing our TARGET= to ogdi makes it break
	unset MAKEFLAGS ; unset TARGET; \
		TOPDIR="$(OUTDIR_CYGSAFE)/$(ogdi_srcdir)"            \
		make -C $(OUTDIR)/$(ogdi_srcdir) \
			CC="$(CC)"                                   \
			CPP="$(CPP)"                                 \
			CXX="$(CXX)"                                 \
			SHLIB_LD="$(CC)"                             \
			RANLIB="$(RANLIB)"                           \
			LD="$(CC)"

$(ogdi_src_lib): ogdi_build
	@echo "ogdi built"

$(ogdi_out_lib): $(ogdi_src_lib)
	@# Unset MAKEFLAGS as passing TARGET= to ogdi makes it break
	unset MAKEFLAGS ; unset TARGET;                              \
	TOPDIR="$(OUTDIR_CYGSAFE)/$(ogdi_srcdir)"                    \
	make -C $(OUTDIR)/$(ogdi_srcdir)                             \
			CC="$(CC)"                                   \
			CPP="$(CPP)"                                 \
			CXX="$(CXX)"                                 \
			SHLIB_LD="$(CC)"                             \
			LD="$(CC)"                                   \
		mkinstalldirs="mkdir -p"                             \
		install
	cd $(OUTDIR)/lib && ( test "`echo *.la`" = "*.la" && true || cd $(OUTDIR)/lib && for i in *.la ; do dos2unix $$i ; done )

