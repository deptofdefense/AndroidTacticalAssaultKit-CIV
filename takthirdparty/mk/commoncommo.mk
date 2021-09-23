include mk/commoncommo-common.mk

.PHONY: commoncommo commoncommo_clean

ifeq ($(and $(commoncommo_CFLAGS)),)
    $(error Required var not set)
endif

commoncommo_libfile=$(LIB_PREFIX)commoncommo.$(if commoncommo_BUILDSTATIC,$(LIB_STATICSUFFIX),$(LIB_SHAREDSUFFIX))
commoncommo_local_libfile=$(OUTDIR)/$(commoncommo_local_srcdir)/core/impl/$(commoncommo_libfile)
commoncommo_out_lib=$(OUTDIR)/lib/$(commoncommo_libfile)

commoncommo_jnilibfile=$(LIB_PREFIX)commoncommojni.$(LIB_SHAREDSUFFIX)
commoncommo_out_jnilib=$(OUTDIR)/lib/$(commoncommo_jnilibfile)
commoncommo_local_jnilibfile=$(OUTDIR)/$(commoncommo_local_srcdir)/jni/native/$(commoncommo_jnilibfile)

commoncommo_java_targets=$(if $(commoncommo_BUILDJAVA),$(commoncommo_out_jnilib),)


commoncommo_objclibfile=$(LIB_PREFIX)commoncommoobjc.$(LIB_SHAREDSUFFIX)
commoncommo_out_objclib=$(OUTDIR)/lib/$(commoncommo_objclibfile)
commoncommo_local_objclibfile=$(OUTDIR)/$(commoncommo_local_srcdir)/objc/commoncommo/impl/$(commoncommo_objclibfile)

commoncommo_objc_targets=$(if $(commoncommo_BUILDOBJC),$(commoncommo_out_objclib),)


# Check that the outer source location is clean
ifneq ($(wildcard $(commoncommo_src)/core/impl/*.$(OBJ_SUFFIX)),)
    $(error commoncommo source dir $(commoncommo_src) not clean - cd there and clean it out)
endif


.PHONY: commoncommo_buildjava
commoncommo_buildjava:
	cd $(OUTDIR)/$(commoncommo_local_srcdir)/jni && ant $(commoncommo_ANT_FLAGS) jar
	$(MAKE) -C $(OUTDIR)/$(commoncommo_local_srcdir)/jni/native    \
            TAKTHIRDPARTYDIR=$(OUTDIR_CYGSAFE)                         \
            COMMO_CXXFLAGS="$(commoncommo_CXXFLAGS)"                   \
            TARGET="$(TARGET)"                                         \
	    CXX=$(CXX)

.PHONY: commoncommo_buildobjc
commoncommo_buildobjc:
	cd $(OUTDIR)/$(commoncommo_local_srcdir)/objc/commoncommo/impl
	$(MAKE)                                                               \
            -C $(OUTDIR)/$(commoncommo_local_srcdir)/objc/commoncommo/impl    \
            TAKTHIRDPARTYDIR=$(OUTDIR_CYGSAFE)                                \
            COMMO_CXXFLAGS="$(commoncommo_CXXFLAGS)"                          \
            TARGET="$(TARGET)"                                                \
	    CXX=$(CXX)

# This is phony because we always want to be invoking commoncommo make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: commoncommo_build
commoncommo_build: $(OUTDIR)/$(commoncommo_local_srcdir)
	$(MAKE) -C $(OUTDIR)/$(commoncommo_local_srcdir)/core/impl     \
            TAKTHIRDPARTYDIR=$(OUTDIR_CYGSAFE)                         \
            COMMO_CXXFLAGS="$(commoncommo_CXXFLAGS)"                   \
	    CXX=$(CXX)                                                 \
	    $(if $(RANLIB),RANLIB=$(RANLIB),)                          \
	    AR=$(AR)                                                   \
            $(commoncommo_libfile)


$(commoncommo_local_libfile): commoncommo_build
	@echo "commoncommo built"

$(commoncommo_out_lib): $(commoncommo_local_libfile)
	$(CP) $< $@
	$(CP) $(OUTDIR)/$(commoncommo_local_srcdir)/core/include/*.h $(OUTDIR)/include/

$(commoncommo_local_jnilibfile): commoncommo_buildjava
	@echo "commoncommo JNI bindings built"

$(commoncommo_out_jnilib): $(commoncommo_local_jnilibfile)
	$(CP) $< $@
	$(CP) $(OUTDIR)/$(commoncommo_local_srcdir)/jni/jcommoncommo.jar $(OUTDIR)/java/

$(commoncommo_local_objclibfile): commoncommo_buildobjc
	@echo "commoncommo Obj-C bindings built"

$(commoncommo_out_objclib): $(commoncommo_local_objclibfile)
	$(CP) $< $@
	$(CP) $(OUTDIR)/$(commoncommo_local_srcdir)/objc/commoncommo/*_objc.h $(OUTDIR)/include

commoncommo: $(commoncommo_out_lib) $(commoncommo_java_targets) $(commoncommo_objc_targets)

commoncommo_clean:
	rm -rf $(OUTDIR)/$(commoncommo_local_srcdir)
	rm -f $(commoncommo_out_lib)
	rm -f $(commoncommo_out_objclib)
	rm -f $(commoncommo_out_jnilib)
	rm -f $(OUTDIR)/java/jcommoncommo.jar
