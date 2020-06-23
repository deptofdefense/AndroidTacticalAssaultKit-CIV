.PHONY: libiconv libiconv_clean

libiconv_libfile=$(LIB_PREFIX)iconv.$(LIB_SHAREDSUFFIX)
libiconv_src=$(DISTFILESDIR)/libiconv.tar.gz
libiconv_srcdir=libiconv
libiconv_srctouchfile=$(OUTDIR)/$(libiconv_srcdir)/.unpacked
libiconv_out_lib=$(OUTDIR)/lib/$(libiconv_libfile)
libiconv_src_lib=$(OUTDIR)/$(libiconv_srcdir)/$(libiconv_libfile)
libiconv_patch=$(DISTFILESDIR)/libiconv-pgsc.patch


$(libiconv_srctouchfile): $(libiconv_src) $(libiconv_patch)
	rm -rf $(OUTDIR)/$(libiconv_srcdir)
	tar -x -z -f $< -C $(OUTDIR)
	cd $(OUTDIR) && mv libiconv-* $(libiconv_srcdir)
	patch -p0 -d $(OUTDIR) < $(libiconv_patch)
	cd $(OUTDIR)/$(libiconv_srcdir) && chmod 755 configure
	touch $@

libiconv: $(if $(libiconv_skip_build),,$(libiconv_out_lib))

libiconv_clean:
	rm -rf $(OUTDIR)/$(libiconv_srcdir)
	rm -f $(libiconv_out_lib)
