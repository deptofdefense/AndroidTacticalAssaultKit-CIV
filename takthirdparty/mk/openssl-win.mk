openssl_libfiles=ssleay32.$(LIB_STATICSUFFIX) libeay32.$(LIB_STATICSUFFIX)

include mk/openssl-common.mk

$(openssl_configtouchfile): $(openssl_srctouchfile)
	cd $(OUTDIR)/$(openssl_srcdir) &&                                   \
		$(openssl_CONFIG)
	touch $@

PERL=$(shell cygpath -w `which perl`)

# This is phony because we always want to be invoking openssl make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: openssl_build
openssl_build: $(openssl_configtouchfile)
	cd $(OUTDIR)/$(openssl_srcdir) &&                                   \
		$(VS_SETUP) nmake -f makefile PERL='$(PERL)'


$(openssl_src_libs): openssl_build
	@echo "OpenSSL built"

$(openssl_out_libs): $(openssl_src_libs)
	cd $(OUTDIR)/$(openssl_srcdir) &&                                   \
		$(VS_SETUP) nmake -f makefile PERL='$(PERL)'                \
			INSTALLTOP="$(OUTDIR_CYGSAFE)"                      \
                        OPENSSLDIR="$(OUTDIR_CYGSAFE)"                      \
			install

