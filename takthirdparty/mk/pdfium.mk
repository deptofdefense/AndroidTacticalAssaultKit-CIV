.PHONY: pdfium pdfium_clean

pdfium_binbundle=distfiles/pdfium/$(TARGET).tar.gz
pdfium_libfile=$(LIB_PREFIX)pdfium.$(LIB_STATICSUFFIX)
pdfium_srcdir=pdfium
pdfium_bundletouchfile=$(OUTDIR)/$(pdfium_srcdir)/.unpacked

$(pdfium_bundletouchfile): $(pdfium_binbundle)
	rm -rf $(OUTDIR)/$(pdfium_srcdir)
	$(if $(findstring .zip,$(suffix $(pdfium_binbundle))),unzip -d $(OUTDIR),tar -x -C $(OUTDIR) -z -f) $<
	$(if $(pdfium_patch),cd $(OUTDIR)/$(pdfium_srcdir) && patch -p1 < $(pdfium_patch),true)
	touch $@

pdfium: $(pdfium_bundletouchfile)

pdfium_clean:
	rm -rf $(OUTDIR)/$(pdfium_srcdir)
