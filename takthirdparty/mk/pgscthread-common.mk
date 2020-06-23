.PHONY: pgscthread pgscthread_clean

ifeq ($(and $(pgscthread_libfile),$(pgscthread_local_libfile),$(pgscthread_out_libfile)),)
    $(error Required var not set)
endif


pgscthread_src=../pgscthread
pgscthread_local_srcdir=pgscthread

pgscthread_srctouchfile=$(OUTDIR)/$(pgscthread_local_srcdir)/.unpacked


# Check that the outer source location is clean
ifneq ($(wildcard $(pgscthread_src)/src/*.$(OBJ_SUFFIX)),)
    $(error pgscthread source dir $(pgscthread_src) not clean - cd there and clean it out)
endif

.PHONY: $(OUTDIR)/$(pgscthread_local_srcdir)
$(OUTDIR)/$(pgscthread_local_srcdir):
	$(CP) -r $(pgscthread_src) $(OUTDIR)
	rm -rf $(OUTDIR)/$(pgscthread_local_srcdir)/.git


pgscthread: $(pgscthread_out_libfile)

