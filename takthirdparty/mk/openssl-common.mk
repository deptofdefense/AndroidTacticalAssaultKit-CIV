.PHONY: openssl openssl_clean

ifeq ($(openssl_CONFIG),)
    $(error Required var not set)
endif

openssl_src=$(DISTFILESDIR)/openssl.tar.gz
openssl_srcdir=openssl
openssl_srctouchfile=$(OUTDIR)/$(openssl_srcdir)/.unpacked
openssl_configtouchfile=$(OUTDIR)/$(openssl_srcdir)/.configured
openssl_out_libs=$(foreach ilib,$(openssl_libfiles),$(OUTDIR)/lib/$(ilib))
openssl_src_libs=$(foreach ilib,$(openssl_libfiles),$(OUTDIR)/$(openssl_srcdir)/$(ilib))
openssl_patch=$(DISTFILESDIR)/openssl-pgsc.patch

$(openssl_srctouchfile): $(openssl_src)
	rm -rf $(OUTDIR)/$(openssl_srcdir)
	tar -x -z -f $< -C $(OUTDIR)
	cd $(OUTDIR) && mv openssl-* $(openssl_srcdir)
	patch -p0 -d $(OUTDIR)/$(openssl_srcdir) < $(openssl_patch)
	cd $(OUTDIR)/$(openssl_srcdir) && chmod 755 Configure config
	touch $@

openssl: $(openssl_out_libs) $(openssl_extra_libs)

openssl_clean: $(openssl_extra_clean)
	rm -rf $(OUTDIR)/$(openssl_srcdir)
	rm -f $(openssl_out_libs)
