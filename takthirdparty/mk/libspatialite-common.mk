ifeq ($(and $(libspatialite_libfile),$(libspatialite_HOSTCC)),)
    $(error Required var from libspatialite mk not set)
endif

.PHONY: libspatialite libspatialite_clean

libspatialite_src=$(DISTFILESDIR)/libspatialite.tar.gz
libspatialite_patch=$(DISTFILESDIR)/libspatialite-pgsc.patch
libspatialite_srcdir=libspatialite
libspatialite_srctouchfile=$(OUTDIR)/$(libspatialite_srcdir)/.unpacked
libspatialite_out_lib=$(OUTDIR)/lib/$(libspatialite_libfile)
libspatialite_src_lib=$(OUTDIR)/$(libspatialite_srcdir)/$(libspatialite_libfile)


$(libspatialite_srctouchfile): $(libspatialite_src) $(libspatialite_patch)
	rm -rf $(OUTDIR)/$(libspatialite_srcdir)
	tar -x -z -f $< -C $(OUTDIR)
	cd $(OUTDIR) && mv libspatialite-* $(libspatialite_srcdir)
	patch -p0 -d $(OUTDIR) < $(libspatialite_patch)
	cd $(OUTDIR)/$(libspatialite_srcdir) && chmod 755 configure
	# Bump # of connections up to 1024
	# Intentionally use host compiler bare; we dont want CC which is
	# the cross compiler
	cd $(OUTDIR)/$(libspatialite_srcdir)/src/connection_cache/generator && \
		$(libspatialite_HOSTCC) -o code_generator code_generator.c  && \
		rm -f cache_aux_*.h                                       && \
		./code_generator 1024                                     && \
		rm -f ../cache_aux_*.h                                    && \
		mv cache_aux_*.h ..
	touch $@

libspatialite: $(libspatialite_out_lib)

libspatialite_clean:
	rm -rf $(OUTDIR)/$(libspatialite_srcdir)
	rm -f $(libspatialite_out_lib)
