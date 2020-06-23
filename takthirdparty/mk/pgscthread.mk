ifeq ($(and $(pgscthread_CXXFLAGS)),)
    $(error Required var not set)
endif

pgscthread_libfile=$(LIB_PREFIX)pgscthread.$(LIB_STATICSUFFIX)
pgscthread_local_libfile=$(OUTDIR)/$(pgscthread_local_srcdir)/src/$(pgscthread_libfile)
pgscthread_out_libfile=$(OUTDIR)/lib/$(pgscthread_libfile)

include mk/pgscthread-common.mk


# This is phony because we always want to be invoking pgscthread make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: pgscthread_build
pgscthread_build: $(OUTDIR)/$(pgscthread_local_srcdir)
	$(MAKE) -C $(OUTDIR)/$(pgscthread_local_srcdir)/src             \
		PGSCTHREAD_CXXFLAGS="$(pgscthread_CXXFLAGS)"            \
		PGSCTHREAD_LDFLAGS="$(pgscthread_LDFLAGS)"              \
		CXX=$(CXX)                                              \
		$(if $(RANLIB),RANLIB=$(RANLIB),)                       \
		INCLUDE_CP="$(CP)"                                      \
                static


$(pgscthread_local_libfile): pgscthread_build
	@echo "pgscthread built"

$(pgscthread_out_libfile): $(pgscthread_local_libfile)
	$(CP) $(OUTDIR)/$(pgscthread_local_srcdir)/include/*.h $(OUTDIR)/include/
	$(CP) $< $@

pgscthread_clean:
	rm -rf $(OUTDIR)/$(pgscthread_local_srcdir)
	rm -f $(pgscthread_out_libfile)
