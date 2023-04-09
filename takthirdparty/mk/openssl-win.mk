openssl_libfiles=libssl.$(LIB_STATICSUFFIX) libcrypto.$(LIB_STATICSUFFIX)
openssl_cng_libfiles=engine-bcrypt.dll engine-ncrypt.dll
openssl_cng_out_libs=$(foreach ilib,$(openssl_cng_libfiles),$(OUTDIR)/bin/$(ilib))

openssl_extra_clean:=openssl_cng_clean
openssl_extra_libs=$(openssl_cng_out_libs)

include mk/openssl-common.mk

ifeq ($(openssl_cng_arch),)
    $(error Required var not set)
endif

openssl_cng_srcdir=cng-engine
openssl_cng_src=$(DISTFILESDIR)/cng-engine.zip
openssl_cng_patch=$(DISTFILESDIR)/cng-engine-pgsc.patch
openssl_cng_srctouchfile=$(OUTDIR)/$(openssl_cng_srcdir)/.unpacked
openssl_cng_builddir_release=bld/$(openssl_cng_arch)-Release-$(VS_VER_MSB)
openssl_cng_builddir_debug=bld/$(openssl_cng_arch)-Debug-$(VS_VER_MSB)
openssl_cng_builddir=$(openssl_cng_builddir_$(BUILD_TYPE))
openssl_cng_src_libs=$(foreach ilib,$(openssl_cng_libfiles),$(OUTDIR)/$(openssl_cng_srcdir)/$(openssl_cng_builddir)/$(ilib))


$(openssl_configtouchfile): $(openssl_srctouchfile) $(cng_srctouchfile)
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


.PHONY: openssl_cng_clean openssl_cng_build
openssl_cng_clean:
	rm -rf $(OUTDIR)/$(openssl_cng_srcdir)
	rm -f $(openssl_cng_out_libs)

$(openssl_cng_srctouchfile): $(openssl_cng_src) $(openssl_cng_patch)
	rm -rf $(OUTDIR)/$(openssl_cng_srcdir)
	unzip -q -b -d $(OUTDIR) $(openssl_cng_src)
	dos2unix $(openssl_cng_patch)
	patch -p1 --binary -d $(OUTDIR)/$(openssl_cng_srcdir) < $(openssl_cng_patch)
	touch $@

openssl_cng_build: $(openssl_cng_srctouchfile)
	cd $(OUTDIR)/$(openssl_cng_srcdir) &&                                \
		$(VS_SETUP) MSBuild /p:Configuration=$(BUILD_TYPE)           \
                                    /p:OpenSSLDir=$(OUTDIR_CYGSAFE)/         \
                                    /target:engine-ncrypt                    \
                                    /target:engine-bcrypt                    \
                                    openssl-cng-engine.sln

$(openssl_cng_src_libs): openssl_cng_build
	@echo "OpenSSL CNG Engine built"

$(openssl_cng_out_libs): $(openssl_cng_src_libs)
	cp $^ $(OUTDIR)/bin/
