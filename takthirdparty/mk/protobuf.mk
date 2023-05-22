ifeq ($(and $(protobuf_CFLAGS)),)
    $(error Required var not set)
endif

protobuf_libfile=$(LIB_PREFIX)protobuf-lite.$(LIB_STATICSUFFIX)

include mk/protobuf-common.mk

protobuf_configtouchfile=$(OUTDIR)/$(protobuf_srcdir)/.configured



$(protobuf_configtouchfile): $(protobuf_srctouchfile)
	cd $(OUTDIR)/$(protobuf_srcdir) &&                           \
		CFLAGS="$(protobuf_CFLAGS)"                          \
		CXXFLAGS="$(protobuf_CXXFLAGS)"                      \
		LDFLAGS="$(protobuf_LDFLAGS)"                        \
		CC="$(CC)"                                           \
		CPP="$(CPP)"                                         \
		CXX="$(CXX)"                                         \
		$(if $(protobuf_LIBS),LIBS="$(protobuf_LIBS)",)      \
		./configure                                          \
		$(CONFIGURE_TARGET)                                  \
		$(CONFIGURE_$(BUILD_TYPE))                           \
		--enable-static                                      \
		--disable-shared                                     \
		--without-zlib                                       \
		--with-protoc=$(protobuf_hosttools_dir)/bin/protoc   \
		--prefix=$(OUTDIR_CYGSAFE)
	touch $@

# This is phony because we always want to be invoking protobuf make to be sure
# the files are up to date;  it knows if anything needs to be done
# Overrides bin_PROGRAMS since we don't need protoc (it's in host tools!)
# It also fails to link right on Windows hosting an android build
.PHONY: protobuf_build
protobuf_build: $(protobuf_hosttools_builttouchfile) $(protobuf_configtouchfile)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(protobuf_srcdir)       \
		bin_PROGRAMS=""                                     

$(protobuf_src_lib): protobuf_build
	@echo "protobuf built"

# Overrides bin_PROGRAMS since we don't need protoc (it's in host tools!)
# It also fails to link right on Windows hosting an android build
$(protobuf_out_lib): $(protobuf_src_lib)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(protobuf_srcdir)      \
		mkinstalldirs="mkdir -p"                            \
		bin_PROGRAMS=""                                     \
		install
	cd $(OUTDIR)/lib && ( test "`echo *.la`" = "*.la" && true || cd $(OUTDIR)/lib && for i in *.la ; do dos2unix $$i ; done )


