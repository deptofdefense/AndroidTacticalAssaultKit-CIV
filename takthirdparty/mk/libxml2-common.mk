.PHONY: libxml2 libxml2_clean

libxml2_libfile=$(LIB_PREFIX)libxml2.$(LIB_STATICSUFFIX)
libxml2_src=$(DISTFILESDIR)/libxml2.tar.gz
libxml2_srcdir=libxml2
libxml2_srctouchfile=$(OUTDIR)/$(libxml2_srcdir)/.unpacked
libxml2_configtouchfile=$(OUTDIR)/$(libxml2_srcdir)/.configured
libxml2_out_lib=$(OUTDIR)/lib/$(libxml2_libfile)
libxml2_src_lib=$(OUTDIR)/$(libxml2_srcdir)/$(libxml2_libfile)

$(libxml2_srctouchfile): $(libxml2_src)
	rm -rf $(OUTDIR)/$(libxml2_srcdir)
	tar -x -z -f $< -C $(OUTDIR)
	cd $(OUTDIR) && mv libxml2-* $(libxml2_srcdir)
	cd $(OUTDIR)/$(libxml2_srcdir) && chmod 755 configure
	touch $@

libxml2: $(libxml2_out_lib)

libxml2_clean:
	rm -rf $(OUTDIR)/$(libxml2_srcdir)
	rm -f $(libxml2_out_lib)

