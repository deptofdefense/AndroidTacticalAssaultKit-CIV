.PHONY: expat expat_clean

ifeq ($(and $(expat_libfile)),)
    $(error Required var not set)
endif

expat_src=$(DISTFILESDIR)/expat.tar.gz
expat_srcdir=expat
expat_srctouchfile=$(OUTDIR)/$(expat_srcdir)/.unpacked
expat_configtouchfile=$(OUTDIR)/$(expat_srcdir)/.configured
expat_out_lib=$(OUTDIR)/lib/$(expat_libfile)
expat_patch=$(DISTFILESDIR)/expat-pgsc.patch

$(expat_srctouchfile): $(expat_src)
	rm -rf $(OUTDIR)/$(expat_srcdir)
	tar -x -z -f $< -C $(OUTDIR)
	cd $(OUTDIR) && mv expat-* $(expat_srcdir)
	cd $(OUTDIR)/$(expat_srcdir) && chmod 755 configure
	patch -p1 -d $(OUTDIR)/$(expat_srcdir) < $(expat_patch)
	touch $@


expat: $(expat_out_lib)

expat_clean:
	rm -rf $(OUTDIR)/$(expat_srcdir)
	rm -f $(expat_out_lib)
