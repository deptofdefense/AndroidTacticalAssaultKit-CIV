ifeq ($(and $(zlib_win_arch_dir),$(zlib_win_platform)),)
    $(error Required var not set)
endif

zlib_libfile=$(LIB_PREFIX)zlibwapi.$(LIB_STATICSUFFIX)

include mk/zlib-common.mk

zlib_conf_release=ReleaseWithoutAsm
zlib_conf_debug=Debug
zlib_conf=$(zlib_conf_$(BUILD_TYPE))

zlib_projdir_v140=vc14
zlib_projdir_v142=vc14

zlib_src_projdir=$(OUTDIR)/$(zlib_srcdir)/contrib/vstudio/$(zlib_projdir_$(VS_VER_MSB))/

zlib_src_libdir=$(zlib_src_projdir)/$(zlib_win_arch_dir)/ZlibDll$(zlib_conf)
zlib_src_lib=$(zlib_src_libdir)/$(zlib_libfile)

zlib_dll=zlibwapi.dll

zlib_build_flags="/t:build" "/p:Configuration=$(zlib_conf)" "/p:Platform=$(zlib_win_platform)" "/p:PlatformToolset=$(VS_VER_MSB)"



$(zlib_src_lib): $(zlib_srctouchfile)
	cd $(zlib_src_projdir) && \
            $(VS_SETUP) MSBuild $(zlib_build_flags) zlibvc.vcxproj

$(zlib_out_lib): $(zlib_src_lib)
	$(CP) $(zlib_src_libdir)/$(zlib_dll) $(OUTDIR)/bin/$(zlib_dll)
	$(CP) $(OUTDIR)/$(zlib_srcdir)/zlib.h $(OUTDIR)/$(zlib_srcdir)/zconf.h $(OUTDIR)/include/
	$(CP) $< $@

zlib: $(zlib_out_lib)

zlib_clean:
	rm -rf $(OUTDIR)/$(zlib_srcdir)
	rm -f $(zlib_out_lib)
	rm -f $(OUTDIR)/bin/$(zlib_dll)

