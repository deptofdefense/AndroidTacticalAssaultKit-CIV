libspatialite_libfile=$(LIB_PREFIX)spatialite.$(LIB_SHAREDSUFFIX)

libspatialite_HOSTCC=$(VS_SETUP) cl

include mk/libspatialite-common.mk


libspatialite_nmake_opts=INSTDIR='$(OUTDIR_WIN)'                        \


# This is phony because we always want to be invoking libspatialite make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: libspatialite_build
libspatialite_build: $(libspatialite_srctouchfile)
	cd $(OUTDIR)/$(libspatialite_srcdir) &&                         \
	    $(libspatialite_nmake_opts) $(VS_SETUP) nmake /f makefile.vc

$(libspatialite_src_lib): libspatialite_build
	@echo "libspatialite built"

$(libspatialite_out_lib): $(libspatialite_src_lib)
	cd $(OUTDIR)/$(libspatialite_srcdir) &&                                      \
	    $(libspatialite_nmake_opts) $(VS_SETUP) nmake /f makefile.vc install

