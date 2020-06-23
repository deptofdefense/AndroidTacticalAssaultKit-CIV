ifeq ($(and $(libmicrohttpd_libfile)),)
    $(error Required var not set)
endif

.PHONY: libmicrohttpd libmicrohttpd_clean

libmicrohttpd_src=$(DISTFILESDIR)/libmicrohttpd.tar.gz
libmicrohttpd_srcdir=libmicrohttpd
libmicrohttpd_srctouchfile=$(OUTDIR)/$(libmicrohttpd_srcdir)/.unpacked
libmicrohttpd_configtouchfile=$(OUTDIR)/$(libmicrohttpd_srcdir)/.configured
libmicrohttpd_out_lib=$(OUTDIR)/lib/$(libmicrohttpd_libfile)
libmicrohttpd_src_lib=$(OUTDIR)/$(libmicrohttpd_srcdir)/$(libmicrohttpd_libfile)

$(libmicrohttpd_srctouchfile): $(libmicrohttpd_src)
	rm -rf $(OUTDIR)/$(libmicrohttpd_srcdir)
	tar -x -z -f $< -C $(OUTDIR)
	cd $(OUTDIR) && mv libmicrohttpd-* $(libmicrohttpd_srcdir)
	cd $(OUTDIR)/$(libmicrohttpd_srcdir) && chmod 755 configure
	$(if $(libmicrohttpd_patch),cd $(OUTDIR)/$(libmicrohttpd_srcdir) && patch -p1 < $(libmicrohttpd_patch),true)
	touch $@

libmicrohttpd: $(libmicrohttpd_out_lib)

libmicrohttpd_clean:
	rm -rf $(OUTDIR)/$(libmicrohttpd_srcdir)
	rm -f $(libmicrohttpd_out_lib)
