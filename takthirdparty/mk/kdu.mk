.PHONY: kdu kdu_apps

ifeq ($(and $(kdu_PLATFORM)),)
    $(error Required var not set)
endif

kdu_libfile=$(LIB_PREFIX)kdu.$(LIB_STATICSUFFIX)
kdu_out_lib=$(OUTDIR)/$(kdu_bindir)/$(kdu_libfile)

include mk/kdu-common.mk

kdu_local_libfile=$(kdu_local_srcdir)/lib/$(kdu_PLATFORM)/$(kdu_libfile)


kdu_app_objs=apps/make/args.o apps/make/image_in.o \
	apps/make/image_out.o \
	apps/make/jp2.o \
	apps/make/mj2.o \
	apps/make/palette.o \
	apps/make/roi_sources.o \
	apps/make/kdu_region_decompressor.o \
	apps/make/kdu_stripe_decompressor.o \
	apps/make/kdu_tiff.o \
	apps/make/jpx.o \
	apps/make/kdu_cache.o



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
	mkdir -p `dirname $@`
	$(CP) $< $@

kdu_apps: $(kdu_out_lib)
	$(kdu_envsets) $(MAKE) -C $(kdu_local_srcdir)/apps/make -f Makefile-$(kdu_PLATFORM) PAR_CFLAGS="$(kdu_CFLAGS)" LIB_SRC='$$(LIB_DIR)/$(kdu_libfile)' kdu_buffered_expand kdu_compress kdu_render kdu_expand kdu_cache.o
	for i in $(kdu_app_objs) ; do \
	    odir=`dirname $$i` ; \
            mkdir -p $(OUTDIR)/$(kdu_bindir)/$$odir ; \
	    $(CP) $(kdu_local_srcdir)/$$i $(OUTDIR)/$(kdu_bindir)/$$i ; \
	done

ifeq ($(KDU_MODE),source)
kdu: $(kdu_out_lib) kdu_apps
	for i in `cd $(kdu_local_srcdir) && find . -name \*.h` ; do \
	    hdir=`dirname $$i` ; \
	    mkdir -p $(OUTDIR)/$(kdu_bindir)/$$hdir ; \
	    $(CP) $(kdu_local_srcdir)/$$i $(OUTDIR)/$(kdu_bindir)/$$i ; \
	done
	cd $(OUTDIR) && tar cfz $(kdu_bindist_file) $(kdu_bindir)
else

kdu: $(kdu_bindist_repo)/$(kdu_bindist_file)
	cat $< | ( cd $(OUTDIR) ; tar xfz - )

endif
