.PHONY: proj proj_clean

proj_libfile=$(LIB_PREFIX)proj.$(LIB_STATICSUFFIX)
proj_src=$(DISTFILESDIR)/proj.tar.gz
proj_patch=$(DISTFILESDIR)/proj-pgsc.patch
proj_srcdir=proj
proj_srctouchfile=$(OUTDIR)/$(proj_srcdir)/.unpacked
proj_out_lib=$(OUTDIR)/lib/$(proj_libfile)
proj_src_lib=$(OUTDIR)/$(proj_srcdir)/$(proj_libfile)


$(proj_srctouchfile): $(proj_src) $(proj_patch)
	rm -rf $(OUTDIR)/$(proj_srcdir)
	tar -x -z -f $< -C $(OUTDIR)
	cd $(OUTDIR) && mv proj-* $(proj_srcdir)
	patch -p0 -d $(OUTDIR) < $(proj_patch)
	cd $(OUTDIR)/$(proj_srcdir) && chmod 755 configure
	touch $@

proj: $(proj_out_lib)

proj_clean:
	rm -rf $(OUTDIR)/$(proj_srcdir)
	rm -f $(proj_out_lib)
