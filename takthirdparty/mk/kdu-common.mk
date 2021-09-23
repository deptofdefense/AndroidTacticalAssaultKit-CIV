.PHONY: kdu_clean

ifeq ($(and $(kdu_out_lib)),)
    $(error Required var not set)
endif


kdu_src=../kdu
kdu_local_srcdir=$(OUTDIR)/kdu
kdu_bindir=kdu_bin

kdu_bindist_repo=../pgsc-kdu
kdu_bindist_file=kdu_bin-$(TARGET)-$(BUILD_TYPE).tar.gz

# Check that the outer source location is clean
ifneq ($(or $(wildcard $(kdu_src)/coresys/make/*.$(OBJ_SUFFIX)),$(wildcard $(kdu_src)/apps/make/*.$(OBJ_SUFFIX)),$(wildcard $(kdu_src)/lib/$(kdu_PLATFORM)/*)),)
    $(error KDU source dir $(kdu_src) not clean - cd there and clean it out)
endif


.PHONY: $(kdu_local_srcdir)
$(kdu_local_srcdir):
	$(CP) -r $(kdu_src) $(OUTDIR)

kdu_clean:
	rm -rf $(OUTDIR)/$(kdu_bindir)
	rm -rf $(kdu_local_srcdir)
	rm -f $(OUTDIR)/$(kdu_bindist_file)
	rm -f $(OUTDIR)/bin/$(kdu_out_lib)
