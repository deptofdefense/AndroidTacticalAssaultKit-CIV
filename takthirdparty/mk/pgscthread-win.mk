pgscthread_libfile=$(LIB_PREFIX)pgscthread.$(LIB_SHAREDSUFFIX)
pgscthread_local_libfile=$(OUTDIR)/$(pgscthread_local_srcdir)/win32/$(pgscthread_win_arch_dir)/$(BUILD_TYPE)/$(pgscthread_libfile)
pgscthread_out_libfile=$(OUTDIR)/bin/$(pgscthread_libfile)

include mk/pgscthread-common.mk


# Check that the outer source location is clean
ifneq ($(wildcard $(pgscthread_src)/win32/$(pgscthread_win_arch_dir)/$(BUILD_TYPE)/*.$(OBJ_SUFFIX)),)
    $(error pgscthread source dir $(pgscthread_src)/win32/$(pgscthread_win_arch_dir)/$(BUILD_TYPE)/ not clean - clean it out)
endif


# This is phony because we always want to be invoking pgscthread to make sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: pgscthread_build
pgscthread_build: $(OUTDIR)/$(pgscthread_local_srcdir)
	$(VS_SETUP) devenv /upgrade $(OUTDIR_CYGSAFE)/$(pgscthread_local_srcdir)/win32/pgscthread.vcxproj
	$(VS_SETUP) MSBuild /property:Configuration=$(BUILD_TYPE) $(OUTDIR_CYGSAFE)/$(pgscthread_local_srcdir)/win32/pgscthread.vcxproj

$(pgscthread_local_libfile): pgscthread_build
	@echo "pgscthread built"

$(pgscthread_out_libfile): $(pgscthread_local_libfile)
	$(CP) $(OUTDIR)/$(pgscthread_local_srcdir)/include/*.h $(OUTDIR)/include/
	$(CP) $(OUTDIR)/$(pgscthread_local_srcdir)/win32/$(pgscthread_win_arch_dir)/$(BUILD_TYPE)/pgscthread.lib $(OUTDIR)/lib
	$(CP) $< $@

pgscthread_clean:
	rm -rf $(OUTDIR)/$(pgscthread_local_srcdir)
	rm -f $(pgscthread_out_libfile)
	rm -f $(OUTDIR)/lib/pgscthread.lib
