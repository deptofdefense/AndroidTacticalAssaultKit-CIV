ifeq ($(and $(zlib_libfile)),)
    $(error Required var from zlib mk not set)
endif

.PHONY: zlib zlib_clean

zlib_src=$(DISTFILESDIR)/zlib.tar.gz
zlib_srcdir=zlib
zlib_srctouchfile=$(OUTDIR)/$(zlib_srcdir)/.unpacked
zlib_out_lib=$(OUTDIR)/lib/$(zlib_libfile)
zlib_src_lib=$(OUTDIR)/$(zlib_srcdir)/$(zlib_libfile)
zlib_patch=$(DISTFILESDIR)/zlib-pgsc.patch

$(zlib_srctouchfile): $(zlib_src) $(zlib_patch)
	rm -rf $(OUTDIR)/$(zlib_srcdir)
	tar -x -z -f $< -C $(OUTDIR)
	cd $(OUTDIR) && mv zlib-* $(zlib_srcdir)
	patch -p0 -d $(OUTDIR) < $(zlib_patch)
	cd $(OUTDIR)/$(zlib_srcdir) && chmod 755 configure
	touch $@

zlib: $(zlib_out_lib)

