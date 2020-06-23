ifeq ($(and $(kdu_win_arch),$(kdu_win_platform)),)
    $(error Required var not set)
endif

.PHONY: kdu

kdu_rd_suffix_release=R
kdu_rd_suffix_debug=D
kdu_rd_suffix=$(kdu_rd_suffix_$(BUILD_TYPE))

kdu_libfile=$(LIB_PREFIX)kdu_v64$(kdu_rd_suffix).$(LIB_SHAREDSUFFIX)
kdu_out_lib=$(OUTDIR)/bin/$(kdu_libfile)

include mk/kdu-common.mk

kdu_local_libfile=$(kdu_local_srcdir)/bin_$(kdu_win_arch)/$(kdu_libfile)


kdu_common_flags="/t:build" "/p:Configuration=$(BUILD_TYPE)" "/p:Platform=$(kdu_win_platform)" "/p:PlatformToolset=$(VS_VER_MSB)"

$(kdu_local_libfile): $(kdu_local_srcdir)
	cd $(kdu_local_srcdir)/coresys &&                                     \
            $(VS_SETUP) MSBuild $(kdu_common_flags) coresys_2010.vcxproj
	cd $(kdu_local_srcdir)/apps/kdu_hyperdoc &&                           \
            $(VS_SETUP) MSBuild $(kdu_common_flags) kdu_hyperdoc_2010.vcxproj
	cd $(kdu_local_srcdir)/managed/kdu_aux &&                             \
            $(VS_SETUP) MSBuild $(kdu_common_flags) kdu_aux_2010.vcxproj

$(kdu_out_lib): $(kdu_local_libfile)
	$(CP) $< $@

kdu: $(kdu_out_lib)

