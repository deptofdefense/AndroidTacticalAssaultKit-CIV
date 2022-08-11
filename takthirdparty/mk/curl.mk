include mk/curl-common.mk

ifeq ($(and $(curl_CFLAGS)),)
    $(error Required var not set)
endif

$(curl_configtouchfile): $(curl_srctouchfile)
	cd $(OUTDIR)/$(curl_srcdir) &&                       \
		CFLAGS="$(curl_CFLAGS)"                      \
		LDFLAGS="$(curl_LDFLAGS)"                    \
		CC="$(CC)"                                   \
		CPP="$(CPP)"                                 \
		CXX="$(CXX)"                                 \
		$(if $(curl_LIBS),LIBS="$(curl_LIBS)",)      \
		./configure                                  \
		$(CONFIGURE_TARGET)                          \
		$(CONFIGURE_$(BUILD_TYPE))                   \
                --with-ssl=$(OUTDIR_CYGSAFE)                 \
		--with-zlib=$(OUTDIR_CYGSAFE)                \
		--disable-shared                             \
		--disable-ldap                               \
		--disable-symbol-hiding                      \
		--prefix=$(OUTDIR_CYGSAFE)
	touch $@

# This is phony because we always want to be invoking curl make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: curl_build
curl_build: $(curl_configtouchfile)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(curl_srcdir)

$(curl_src_lib): curl_build
	@echo "curl built"

$(curl_out_lib): $(curl_src_lib)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(curl_srcdir) install
	dos2unix $(OUTDIR)/bin/curl-config
	cd $(OUTDIR)/lib && ( test "`echo *.la`" = "*.la" && true || cd $(OUTDIR)/lib && for i in *.la ; do dos2unix $$i ; done )

