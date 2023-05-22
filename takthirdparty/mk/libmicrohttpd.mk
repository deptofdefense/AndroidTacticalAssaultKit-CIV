libmicrohttpd_libfile=$(LIB_PREFIX)libmicrohttpd.$(LIB_STATICSUFFIX)

ifeq ($(and $(libmicrohttpd_CFLAGS)),)
    $(error Required var not set)
endif

include mk/libmicrohttpd-common.mk

$(libmicrohttpd_configtouchfile): $(libmicrohttpd_srctouchfile)
	cd $(OUTDIR)/$(libmicrohttpd_srcdir) &&                         \
		CFLAGS="$(libmicrohttpd_CFLAGS)"                            \
		LDFLAGS="$(libmicrohttpd_LDFLAGS)"                          \
		CC="$(CC)"                                                  \
		CPP="$(CPP)"                                                \
		CXX="$(CXX)"                                                \
		$(if $(libmicrohttpd_LIBS),LIBS="$(libmicrohttpd_LIBS)",)   \
		./configure                                                 \
		$(CONFIGURE_TARGET)                                         \
		$(CONFIGURE_$(BUILD_TYPE))                                  \
		--disable-shared                                            \
		--enable-static                                             \
		--disable-epoll                                             \
		--disable-https                                             \
		--disable-curl                                              \
		--disable-postprocessor                                     \
		--disable-dauth                                             \
		--prefix=$(OUTDIR_CYGSAFE)
	touch $@

# This is phony because we always want to be invoking libmicrohttpd make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: libmicrohttpd_build
libmicrohttpd_build: $(libmicrohttpd_configtouchfile)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(libmicrohttpd_srcdir)

$(libmicrohttpd_src_lib): libmicrohttpd_build
	@echo "Libxml built"

$(libmicrohttpd_out_lib): $(libmicrohttpd_src_lib)
	$(MAKE) -j `nproc`  -C $(OUTDIR)/$(libmicrohttpd_srcdir)        \
		mkinstalldirs="mkdir -p"                                    \
		install
	cd $(OUTDIR)/lib && ( test "`echo *.la`" = "*.la" && true || cd $(OUTDIR)/lib && for i in *.la ; do dos2unix $$i ; done )

