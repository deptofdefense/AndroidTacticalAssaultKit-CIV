ifeq ($(and $(expat_win_arch)),)
    $(error Required var not set)
endif

expat_libfile=libexpat.$(LIB_STATICSUFFIX)

include mk/expat-common.mk

expat_win32_dir=$(OUTDIR)/$(expat_srcdir)/win32/bin/$(BUILD_TYPE)
expat_src_lib=$(expat_win32_dir)/$(expat_libfile)
expat_windows_patch=$(DISTFILESDIR)/expat-pgsc-winproj-$(VS_VER_MSB).patch


expat_subdir=lib
expat_projfile_old=expat.dsp
expat_projfile_cur=expat.vcxproj
expat_common_flags="/t:build"                                                 \
                   "/p:Configuration=$(BUILD_TYPE)"                           \
                   "/p:Platform=$(expat_win_arch)"

$(OUTDIR)/$(expat_srcdir)/$(expat_subdir)/$(expat_projfile_cur):              \
		$(OUTDIR)/$(expat_srcdir)/$(expat_subdir)/$(expat_projfile_old)
	patch --binary -p1 -d $(OUTDIR)/$(expat_srcdir) < $(expat_windows_patch) || \
	    (rm -f $(OUTDIR)/$(expat_srcdir)/$(expat_subdir)/$(expat_projfile_cur) ; exit 1 )


# This is phony because we always want to be invoking expat make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: expat_build
expat_build: $(expat_srctouchfile)                                            \
              $(OUTDIR)/$(expat_srcdir)/$(expat_subdir)/$(expat_projfile_cur)
	cd $(OUTDIR)/$(expat_srcdir)/$(expat_subdir) &&                       \
	    $(VS_SETUP) msbuild $(expat_common_flags) $(expat_projfile_cur)

$(expat_src_lib): expat_build
	@echo "Expat built"

$(expat_out_lib): $(expat_src_lib)
	$(CP) $< $@
	$(CP) $(expat_win32_dir)/libexpat.dll $(OUTDIR)/bin/
	$(CP) $(expat_win32_dir)/libexpat.dll $(OUTDIR)/bin/
	$(CP) $(OUTDIR)/$(expat_srcdir)/$(expat_subdir)/expat*.h              \
	      $(OUTDIR)/include/
