ifeq ($(and $(kdu_win_arch),$(kdu_win_platform)),)
    $(error Required var not set)
endif

.PHONY: kdu

kdu_rd_suffix_release=R
kdu_rd_suffix_debug=D
kdu_rd_suffix=$(kdu_rd_suffix_$(BUILD_TYPE))

kdu_libfile=$(LIB_PREFIX)kdu_v64$(kdu_rd_suffix).$(LIB_SHAREDSUFFIX)
kdu_out_lib=$(OUTDIR)/$(kdu_bindir)/$(kdu_libfile)

include mk/kdu-common.mk

kdu_local_libfile=$(kdu_local_srcdir)/bin_$(kdu_win_arch)/$(kdu_libfile)

kdu_aux_objs=v6_generated_$(kdu_win_arch)/kdu_aux/$(BUILD_TYPE)/args.obj \
	v6_generated_$(kdu_win_arch)/kdu_aux/$(BUILD_TYPE)/image_in.obj \
	v6_generated_$(kdu_win_arch)/kdu_aux/$(BUILD_TYPE)/image_out.obj \
	v6_generated_$(kdu_win_arch)/kdu_aux/$(BUILD_TYPE)/jp2.obj \
	v6_generated_$(kdu_win_arch)/kdu_aux/$(BUILD_TYPE)/mj2.obj \
	v6_generated_$(kdu_win_arch)/kdu_aux/$(BUILD_TYPE)/palette.obj \
	v6_generated_$(kdu_win_arch)/kdu_aux/$(BUILD_TYPE)/roi_sources.obj \
	v6_generated_$(kdu_win_arch)/kdu_aux/$(BUILD_TYPE)/kdu_region_decompressor.obj \
	v6_generated_$(kdu_win_arch)/kdu_aux/$(BUILD_TYPE)/kdu_stripe_decompressor.obj \
	v6_generated_$(kdu_win_arch)/kdu_aux/$(BUILD_TYPE)/kdu_tiff.obj \
	v6_generated_$(kdu_win_arch)/kdu_aux/$(BUILD_TYPE)/jpx.obj \
	v6_generated_$(kdu_win_arch)/kdu_aux/$(BUILD_TYPE)/kdu_cache.obj




kdu_common_flags="/t:build" "/p:Configuration=$(BUILD_TYPE)" "/p:Platform=$(kdu_win_platform)" "/p:PlatformToolset=$(VS_VER_MSB)"

$(kdu_local_libfile): $(kdu_local_srcdir)
	cd $(kdu_local_srcdir)/coresys &&                                     \
            $(VS_SETUP) MSBuild $(kdu_common_flags) coresys_2010.vcxproj
	cd $(kdu_local_srcdir)/apps/kdu_hyperdoc &&                           \
            $(VS_SETUP) MSBuild $(kdu_common_flags) kdu_hyperdoc_2010.vcxproj
	cd $(kdu_local_srcdir)/managed/kdu_aux &&                             \
            $(VS_SETUP) MSBuild $(kdu_common_flags) kdu_aux_2010.vcxproj

$(kdu_out_lib): $(kdu_local_libfile)
	mkdir -p `dirname $@`
	$(CP) $< $@
	$(CP) $< $(OUTDIR)/bin/

ifeq ($(KDU_MODE),source)
kdu: $(kdu_out_lib)
	for i in $(kdu_aux_objs) ; do \
	    odir=`dirname $$i` ; \
            mkdir -p $(OUTDIR)/$(kdu_bindir)/$$odir ; \
	    $(CP) $(kdu_local_srcdir)/$$i $(OUTDIR)/$(kdu_bindir)/$$i ; \
	done
	for i in `cd $(kdu_local_srcdir) && find . -name \*.h` ; do \
	    hdir=`dirname $$i` ; \
	    mkdir -p $(OUTDIR)/$(kdu_bindir)/$$hdir ; \
	    $(CP) $(kdu_local_srcdir)/$$i $(OUTDIR)/$(kdu_bindir)/$$i ; \
	done
	$(CP) -r $(kdu_local_srcdir)/lib_$(kdu_win_arch)/ $(OUTDIR)/$(kdu_bindir)/

	cd $(OUTDIR) && tar cfz $(kdu_bindist_file) $(kdu_bindir)
else

kdu: $(kdu_bindist_repo)/$(kdu_bindist_file)
	cat $< | ( cd $(OUTDIR) ; tar xfz - )
	$(CP) $(OUTDIR)/$(kdu_bindir)/$(kdu_libfile) $(OUTDIR)/bin/

endif
