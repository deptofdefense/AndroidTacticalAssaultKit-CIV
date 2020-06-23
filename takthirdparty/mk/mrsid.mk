.PHONY: mrsid mrsid_clean

ifeq ($(and $(mrsid_BINBUNDLE),$(mrsid_BINLIBPATH)),)
    $(error Required var not set)
endif

mrsid_libfile=$(LIB_PREFIX)lridsdk.$(LIB_SHAREDSUFFIX)
mrsid_srcdir=mrsid
mrsid_bundletouchfile=$(OUTDIR)/$(mrsid_srcdir)/.unpacked
mrsid_out_lib=$(OUTDIR)/lib/$(mrsid_libfile)

$(mrsid_bundletouchfile): $(mrsid_BINBUNDLE)
	rm -rf $(OUTDIR)/$(mrsid_srcdir)
	$(if $(findstring .zip,$(suffix $(mrsid_BINBUNDLE))),unzip -d $(OUTDIR),tar -x -C $(OUTDIR) -z -f) $<
	$(if $(mrsid_patch),cd $(OUTDIR)/$(mrsid_srcdir) && patch -p1 < $(mrsid_patch),true)
	touch $@

$(mrsid_out_lib): $(mrsid_bundletouchfile)
	cp -r $(OUTDIR)/$(mrsid_srcdir)/include $(OUTDIR)
	cp $(OUTDIR)/$(mrsid_srcdir)/$(mrsid_BINLIBPATH)/* $(OUTDIR)/lib/
	# Copy also dlls to bin to make life easier for running apps
	cd $(OUTDIR)/lib && ( test "`echo tbb*.dll`" = "tbb*.dll" && true || for i in tbb*.dll ; do chmod 755 $$i && $(CP) $$i ../bin/ ; done )
	cd $(OUTDIR)/lib && ( test "`echo lti*.dll`" = "lti*.dll" && true || for i in lti*.dll ; do chmod 755 $$i && $(CP) $$i ../bin/ ; done )

mrsid: $(mrsid_out_lib)

mrsid_clean:
	rm -rf $(OUTDIR)/$(mrsid_srcdir)
	rm -f $(mrsid_out_lib)
