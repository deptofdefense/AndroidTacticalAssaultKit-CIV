include mk/geos-common.mk


geos_nmake_opts=BUILD_DEBUG=$(win_debug_yesno)                            \


# This is phony because we always want to be invoking geos make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: geos_build
geos_build: $(geos_srctouchfile)
	cd $(OUTDIR)/$(geos_srcdir) &&                                      \
	    $(geos_nmake_opts) $(VS_SETUP) nmake /f makefile.vc

$(geos_src_lib): geos_build
	@echo "geos built"

$(geos_out_lib): $(geos_src_lib)
	$(CP) -r $(OUTDIR)/$(geos_srcdir)/include/geos $(OUTDIR)/include/
	$(CP) $(OUTDIR)/$(geos_srcdir)/include/geos.h $(OUTDIR)/include/
	$(CP) $(OUTDIR)/$(geos_srcdir)/capi/geos_c.h $(OUTDIR)/include/
	$(CP) $(OUTDIR)/$(geos_srcdir)/src/*.lib $(OUTDIR)/lib
	$(CP) $(OUTDIR)/$(geos_srcdir)/src/*.dll $(OUTDIR)/bin

