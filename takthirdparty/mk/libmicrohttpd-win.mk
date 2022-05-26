libmicrohttpd_libfile_base_debug=libmicrohttpd-dll_d
libmicrohttpd_libfile_base_release=libmicrohttpd-dll
libmicrohttpd_libfile_base=$(libmicrohttpd_libfile_base_$(BUILD_TYPE))

libmicrohttpd_libfile=$(libmicrohttpd_libfile_base).lib

libmicrohttpd_projdir_v140=VS2015
libmicrohttpd_projdir_v142=VS2019
libmicrohttpd_projdir=$(libmicrohttpd_projdir_$(VS_VER_MSB))

include mk/libmicrohttpd-common.mk

# This is phony because we always want to be invoking libmicrohttpd make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: libmicrohttpd_build
libmicrohttpd_build: $(libmicrohttpd_srctouchfile)
	$(VS_SETUP) MSBuild /property:Configuration=$(BUILD_TYPE)-dll $(OUTDIR_CYGSAFE)/$(libmicrohttpd_srcdir)/w32/$(libmicrohttpd_projdir)/libmicrohttpd.vcxproj


$(libmicrohttpd_src_lib): libmicrohttpd_build
	@echo "Libxml built"

$(libmicrohttpd_out_lib): $(libmicrohttpd_src_lib)
	$(CP) $(OUTDIR_CYGSAFE)/$(libmicrohttpd_srcdir)/w32/$(libmicrohttpd_projdir)/Output/$(libmicrohttpd_win_arch_dir)/$(libmicrohttpd_libfile) $@
	$(CP) $(OUTDIR_CYGSAFE)/$(libmicrohttpd_srcdir)/w32/$(libmicrohttpd_projdir)/Output/$(libmicrohttpd_win_arch_dir)/$(libmicrohttpd_libfile_base).dll $(OUTDIR)/bin/
	$(CP) $(OUTDIR_CYGSAFE)/$(libmicrohttpd_srcdir)/w32/$(libmicrohttpd_projdir)/Output/$(libmicrohttpd_win_arch_dir)/microhttpd.h $(OUTDIR)/include
