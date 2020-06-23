.PHONY: kdu kdu_apps

ifeq ($(and $(kdu_PLATFORM)),)
    $(error Required var not set)
endif

kdu_libfile=$(LIB_PREFIX)kdu.$(LIB_STATICSUFFIX)
kdu_out_lib=$(OUTDIR)/lib/$(kdu_libfile)

include mk/kdu-common.mk

kdu_local_libfile=$(kdu_local_srcdir)/lib/$(kdu_PLATFORM)/$(kdu_libfile)

kdu_envsets=KDU_CFLAGS="$(kdu_CFLAGS)" KDU_CC="$(CC)" KDU_AR="$(AR)" KDU_RANLIB="$(RANLIB)"


# Check that the outer source location is clean
ifneq ($(or $(wildcard $(kdu_src)/coresys/make/*.$(OBJ_SUFFIX)),$(wildcard $(kdu_src)/apps/make/*.$(OBJ_SUFFIX)),$(wildcard $(kdu_src)/lib/$(kdu_PLATFORM)/*)),)
    $(error KDU source dir $(kdu_src) not clean - cd there and clean it out)
endif


# This is phony because we always want to be invoking KDU make to be sure
# the file is up to date;  it knows if anything needs to be done
.PHONY: $(kdu_local_libfile)
$(kdu_local_libfile): $(kdu_local_srcdir)
	$(kdu_envsets) $(MAKE) -C $(kdu_local_srcdir)/coresys/make -f Makefile-$(kdu_PLATFORM) $(kdu_libfile)

$(kdu_out_lib): $(kdu_local_libfile)
	$(CP) $< $@

kdu_apps: $(kdu_out_lib)
	$(kdu_envsets) $(MAKE) -C $(kdu_local_srcdir)/apps/make -f Makefile-$(kdu_PLATFORM) PAR_CFLAGS="$(kdu_CFLAGS)" LIB_SRC='$$(LIB_DIR)/$(kdu_libfile)' kdu_buffered_expand kdu_compress kdu_render kdu_expand

kdu: $(kdu_out_lib) kdu_apps

