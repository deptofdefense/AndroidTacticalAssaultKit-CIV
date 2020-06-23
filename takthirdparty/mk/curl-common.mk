.PHONY: curl curl_clean

curl_libfile=$(LIB_PREFIX)curl.$(LIB_STATICSUFFIX)
curl_src=$(DISTFILESDIR)/curl.tar.bz2
curl_srcdir=curl
curl_srctouchfile=$(OUTDIR)/$(curl_srcdir)/.unpacked
curl_configtouchfile=$(OUTDIR)/$(curl_srcdir)/.configured
curl_out_lib=$(OUTDIR)/lib/$(curl_libfile)
curl_src_lib=$(OUTDIR)/$(curl_srcdir)/$(curl_libfile)
curl_patch=$(DISTFILESDIR)/curl-pgsc.patch

$(curl_srctouchfile): $(curl_src)
	rm -rf $(OUTDIR)/$(curl_srcdir)
	tar -x -j -f $< -C $(OUTDIR)
	cd $(OUTDIR) && mv curl-* $(curl_srcdir)
	patch -p0 -d $(OUTDIR) < $(curl_patch)
	cd $(OUTDIR)/$(curl_srcdir) && chmod 755 configure
	touch $@

curl: $(curl_out_lib)

curl_clean:
	rm -rf $(OUTDIR)/$(curl_srcdir)
	rm -f $(curl_out_lib)
