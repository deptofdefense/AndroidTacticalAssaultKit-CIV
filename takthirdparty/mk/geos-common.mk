.PHONY: geos geos_clean

geos_libfile=$(LIB_PREFIX)geos.$(LIB_STATICSUFFIX)
geos_src=$(DISTFILESDIR)/geos.tar.bz2
geos_patch=$(DISTFILESDIR)/geos-pgsc.patch
geos_srcdir=geos
geos_srctouchfile=$(OUTDIR)/$(geos_srcdir)/.unpacked
geos_out_lib=$(OUTDIR)/lib/$(geos_libfile)
geos_src_lib=$(OUTDIR)/$(geos_srcdir)/$(geos_libfile)


$(geos_srctouchfile): $(geos_src) $(geos_patch)
	rm -rf $(OUTDIR)/$(geos_srcdir)
	tar -x -j -f $< -C $(OUTDIR)
	cd $(OUTDIR) && mv geos-* $(geos_srcdir)
	patch -p0 -d $(OUTDIR) < $(geos_patch)
	cd $(OUTDIR)/$(geos_srcdir) && chmod 755 configure
	touch $@

geos: $(geos_out_lib)

geos_clean:
	rm -rf $(OUTDIR)/$(geos_srcdir)
	rm -f $(geos_out_lib)
