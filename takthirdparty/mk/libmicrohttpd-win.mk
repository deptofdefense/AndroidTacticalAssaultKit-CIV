libmicrohttpd_libfile_base_debug=libmicrohttpd-dll_d
libmicrohttpd_libfile_base_release=libmicrohttpd-dll
libmicrohttpd_libfile_base=$(libmicrohttpd_libfile_base_$(BUILD_TYPE))

libmicrohttpd_libfile=$(libmicrohttpd_libfile_base).lib


include mk/libmicrohttpd-common.mk

# This is phony because we always want to be invoking libmicrohttpd make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: libmicrohttpd_build
libmicrohttpd_build: $(libmicrohttpd_srctouchfile)
	$(VS_SETUP) devenv /upgrade $(OUTDIR_CYGSAFE)/$(libmicrohttpd_srcdir)/w32/VS2013/libmicrohttpd.vcxproj
	$(VS_SETUP) MSBuild /property:Configuration=$(BUILD_TYPE)-dll $(OUTDIR_CYGSAFE)/$(libmicrohttpd_srcdir)/w32/VS2013/libmicrohttpd.vcxproj


$(libmicrohttpd_src_lib): libmicrohttpd_build
	@echo "Libxml built"

$(libmicrohttpd_out_lib): $(libmicrohttpd_src_lib)
	$(CP) $(OUTDIR_CYGSAFE)/$(libmicrohttpd_srcdir)/w32/VS2013/Output/$(libmicrohttpd_win_arch_dir)/$(libmicrohttpd_libfile) $@
	$(CP) $(OUTDIR_CYGSAFE)/$(libmicrohttpd_srcdir)/w32/VS2013/Output/$(libmicrohttpd_win_arch_dir)/$(libmicrohttpd_libfile_base).dll $(OUTDIR)/bin/
	$(CP) $(OUTDIR_CYGSAFE)/$(libmicrohttpd_srcdir)/w32/VS2013/Output/$(libmicrohttpd_win_arch_dir)/microhttpd.h $(OUTDIR)/include
