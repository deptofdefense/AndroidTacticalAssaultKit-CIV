.PHONY: pri

pri_libfile=$(LIB_PREFIX)pri.$(LIB_SHAREDSUFFIX)
pri_out_lib=$(OUTDIR)/bin/$(pri_libfile)

include mk/pri-common.mk

pri-win_patch=$(DISTFILESDIR)/pri-pgsc-winproj-$(VS_VER_MSB).patch
pri_local_libfile=$(pri_local_srcdir)/pricpp/$(pri_win_arch_dir)/$(BUILD_TYPE)/$(pri_libfile)

.PHONY: pri_build
pri_build: $(pri_local_srcdir)
	if [ -f $(pri-win_patch) ] ; then patch -p0 -d $(OUTDIR) < $(pri-win_patch) ; fi
	cd $(pri_local_srcdir)/pricpp && $(VS_SETUP) MSBuild /p:Configuration=$(BUILD_TYPE) /p:PRI_ZLIB_LIB=$(OUTDIR_CYGSAFE)/lib/zlibwapi.lib pri.vcxproj

$(pri_local_libfile): pri_build

$(pri_out_lib): $(pri_local_libfile)
	$(CP) $(pri_local_srcdir)/pricpp/$(pri_win_arch_dir)/$(BUILD_TYPE)/pri.lib $(OUTDIR)/lib/
	$(CP) $< $@

pri: $(pri_out_lib) pri_install_headers
