pgscutils_libdir=win32/pgsc-utils/$(BUILD_TYPE)-dll

include mk/pgscutils-common.mk


pthreads_bintouchfile=$(OUTDIR)/.pthreads_unpacked
pthreads_binbundle=$(DISTFILESDIR)/pthreads-win32.zip


# Check that the outer source location is clean
ifneq ($(wildcard $(pgscutils_src)/$(pgscutils_libdir)/*.$(OBJ_SUFFIX)),)
    $(error pgscutils source dir $(pgscutils_src)/$(pgscutils_libdir) not clean - clean it out)
endif


$(pthreads_bintouchfile): $(pthreads_binbundle)
	cd $(OUTDIR) &&                                                      \
		unzip -o -j $(pthreads_binbundle)                            \
			Pre-built.2/lib/x86/pthreadVC2.lib -d lib/ &&        \
		unzip -o -j $(pthreads_binbundle)                            \
			Pre-built.2/dll/x86/pthreadVC2.dll -d bin/ &&        \
		unzip -o -j $(pthreads_binbundle)                            \
			Pre-built.2/include/*.h -d include/
	touch $@

# This is phony because we always want to be invoking pgscutils make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: pgscutils_build
pgscutils_build: $(pthreads_bintouchfile) \
		 $(OUTDIR)/$(pgscutils_local_srcdir)
	PTHREADS_PATH=$(OUTDIR_CYGSAFE) $(VS_SETUP) MSBuild /property:Configuration=$(BUILD_TYPE)-dll $(OUTDIR_CYGSAFE)/$(pgscutils_local_srcdir)/win32/PGSC_Utils/PGSC_Utils.vcxproj

$(pgscutils_local_libfile): pgscutils_build
	@echo "pgscutils built"

$(pgscutils_out_libfile): $(pgscutils_local_libfile)
	$(CP) $< $@
	$(CP) $(OUTDIR)/$(pgscutils_local_srcdir)/$(pgscutils_libdir)/threads.dll $(OUTDIR)/bin
	$(CP) $(OUTDIR)/$(pgscutils_local_srcdir)/$(pgscutils_libdir)/threads.pdb $(OUTDIR)/bin

pgscutils_clean:
	rm -rf $(OUTDIR)/$(pgscutils_local_srcdir)
	rm -f $(pgscutils_out_libfile)
	rm -f $(pthreads_bintouchfile)
