openssl_libfiles=$(LIB_PREFIX)ssl.$(LIB_STATICSUFFIX) $(LIB_PREFIX)crypto.$(LIB_STATICSUFFIX)

include mk/openssl-common.mk

ifeq ($(openssl_CFLAGS),)
    $(error Required var not set)
endif

$(openssl_configtouchfile): $(openssl_srctouchfile)
	cd $(OUTDIR)/$(openssl_srcdir) &&                                   \
		$(openssl_CONFIG)
	touch $@

# This is phony because we always want to be invoking openssl make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: openssl_build
openssl_build: $(openssl_configtouchfile)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(openssl_srcdir) Makefile build_libs
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(openssl_srcdir) build_apps openssl.pc libssl.pc libcrypto.pc

$(openssl_src_libs): openssl_build
	@echo "OpenSSL built"

$(openssl_out_libs): $(openssl_src_libs)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(openssl_srcdir) install_sw
	cd $(OUTDIR)/lib && ( test "`echo *.la`" = "*.la" && true || cd $(OUTDIR)/lib && for i in *.la ; do dos2unix $$i ; done )
