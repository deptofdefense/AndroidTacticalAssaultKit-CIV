include mk/ogdi-common.mk


ogdi_nmake_opts=BUILD_DEBUG=$(win_debug_yesno)                              \


# This is phony because we always want to be invoking ogdi make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: ogdi_build
ogdi_build: $(ogdi_srctouchfile)
	unset MAKEFLAGS ; unset TARGET; \
		TOPDIR="$(OUTDIR_CYGSAFE)/$(ogdi_srcdir)" \
		TARGET=win32 \
		CFG=$(BUILD_TYPE) \
		TTP_EXTRA_PATH="/bin" \
		$(VS_SETUP) $(call PATH_CYGSAFE,$(shell which make)) -C $(OUTDIR)/$(ogdi_srcdir)


$(ogdi_src_lib): ogdi_build
	@echo "ogdi built"

$(ogdi_out_lib): $(ogdi_src_lib)
	mkdir -p $(OUTDIR)/include/
	$(CP) -r $(OUTDIR)/$(ogdi_srcdir)/ogdi/include/*.h $(OUTDIR)/include
	$(CP) $(OUTDIR)/$(ogdi_srcdir)/bin/win32/ogdi.dll $(OUTDIR)/bin/
	$(CP) $(OUTDIR)/$(ogdi_srcdir)/bin/win32/vrf.dll $(OUTDIR)/bin/
	$(CP) $(OUTDIR)/$(ogdi_srcdir)/lib/win32/ogdi.lib $(OUTDIR)/lib/

