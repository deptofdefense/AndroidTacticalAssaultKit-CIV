.PHONY: libkml libkml_clean

ifeq ($(and $(libkml_out_libs)),)
    $(error Required var not set)
endif


libkml_src=$(DISTFILESDIR)/libkml.tar.gz
libkml_patches_preac=$(DISTFILESDIR)/libkml-pgsc.patch $(libkml_EXTRAPATCHES_PREAC)
libkml_srcdir=libkml
libkml_srctouchfile=$(OUTDIR)/$(libkml_srcdir)/.unpacked


$(libkml_srctouchfile): $(libkml_src) $(libkml_patches_preac)
	rm -rf $(OUTDIR)/$(libkml_srcdir)
	tar -x -z -f $< -C $(OUTDIR)
	cd $(OUTDIR) && mv libkml-* $(libkml_srcdir)
	cd $(OUTDIR)/$(libkml_srcdir)  && ln -s config/install-sh
	for p in $(libkml_patches_preac) ; do \
		patch -p0 -N -d $(OUTDIR)/$(libkml_srcdir) < $$p || exit 1 ; \
	done
	touch $@


libkml: $(libkml_out_libs)

libkml_clean:
	rm -rf $(OUTDIR)/$(libkml_srcdir)
	rm -f $(libkml_out_libs)
