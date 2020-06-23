.PHONY: ogdi ogdi_clean

ogdi_libfile=$(LIB_PREFIX)ogdi.$(LIB_STATICSUFFIX)
ogdi_src=$(DISTFILESDIR)/ogdi.tar.gz
ogdi_patch=$(DISTFILESDIR)/ogdi-pgsc.patch
ogdi_srcdir=ogdi
ogdi_srctouchfile=$(OUTDIR)/$(ogdi_srcdir)/.unpacked
ogdi_out_lib=$(OUTDIR)/lib/$(ogdi_libfile)
ogdi_src_lib=$(OUTDIR)/$(ogdi_srcdir)/$(ogdi_libfile)


$(ogdi_srctouchfile): $(ogdi_src) $(ogdi_patch)
	rm -rf $(OUTDIR)/$(ogdi_srcdir)
	tar -x -z -f $< -C $(OUTDIR)
	cd $(OUTDIR) && mv ogdi-* $(ogdi_srcdir)
	patch -p0 -d $(OUTDIR) < $(ogdi_patch)
	cd $(OUTDIR)/$(ogdi_srcdir) && chmod 755 configure
	cd $(OUTDIR)/$(ogdi_srcdir)/include && mkdir Darwin && \
			cp Linux/ogdi_macro.h Darwin/
	touch $@

ogdi: $(ogdi_out_lib)

ogdi_clean:
	rm -rf $(OUTDIR)/$(ogdi_srcdir)
	rm -f $(ogdi_out_lib)
