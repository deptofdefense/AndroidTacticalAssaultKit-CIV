.PHONY: pgscutils pgscutils_clean

ifeq ($(pgscutils_libdir),)
    $(error Required var not set)
endif

pgscutils_libfile=$(LIB_PREFIX)Threads.$(LIB_STATICSUFFIX)
pgscutils_src=../../pgsc-utils
pgscutils_local_srcdir=pgsc-utils
pgscutils_local_libfile=$(OUTDIR)/$(pgscutils_local_srcdir)/$(pgscutils_libdir)/$(pgscutils_libfile)
pgscutils_out_libfile=$(OUTDIR)/lib/$(pgscutils_libfile)

pgscutils_srctouchfile=$(OUTDIR)/$(pgscutils_local_srcdir)/.unpacked


# Check that the outer source location is clean
ifneq ($(wildcard $(pgscutils_src)/src/threads/*.$(OBJ_SUFFIX)),)
    $(error pgscutils source dir $(pgscutils_src) not clean - cd there and clean it out)
endif

.PHONY: $(OUTDIR)/$(pgscutils_local_srcdir)
$(OUTDIR)/$(pgscutils_local_srcdir):
	$(CP) -r $(pgscutils_src) $(OUTDIR)
	cd $(OUTDIR)/$(pgscutils_local_srcdir)


pgscutils: $(pgscutils_out_libfile)

