ifeq ($(and $(proj_CFLAGS)),)
    $(error Required var not set)
endif

include mk/proj-common.mk

proj_configtouchfile=$(OUTDIR)/$(proj_srcdir)/.configured


$(proj_configtouchfile): $(proj_srctouchfile)
	cd $(OUTDIR)/$(proj_srcdir) &&                   \
		CFLAGS="$(proj_CFLAGS)"                      \
		LDFLAGS="$(proj_LDFLAGS)"                    \
		CC="$(CC)"                                   \
		CPP="$(CPP)"                                 \
		CXX="$(CXX)"                                 \
		$(if $(proj_LIBS),LIBS="$(proj_LIBS)",)      \
		./configure                                  \
		$(CONFIGURE_TARGET)                          \
		$(CONFIGURE_$(BUILD_TYPE))                   \
		--disable-shared                             \
		--enable-static                              \
		--without-jni                                \
		--prefix=$(OUTDIR_CYGSAFE)
	touch $@

# This is phony because we always want to be invoking proj make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: proj_build
proj_build: $(proj_configtouchfile)
	echo "fixing up libtool"
	# fix up for issue with some Android OS's not liking the libproj.so calling itself libproj.so.9
	sed -e 's/\\\$$major//g'                             \
	    -e 's/\\\$$versuffix//g'                         \
	    $(OUTDIR)/$(proj_srcdir)/libtool >               \
            $(OUTDIR)/$(proj_srcdir)/libtool.new
	mv $(OUTDIR)/$(proj_srcdir)/libtool.new              \
           $(OUTDIR)/$(proj_srcdir)/libtool
	echo "finished fixing up libtool"
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(proj_srcdir)

$(proj_src_lib): proj_build
	@echo "proj built"

$(proj_out_lib): $(proj_src_lib)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(proj_srcdir)   \
		mkinstalldirs="mkdir -p"                     \
		install
	cd $(OUTDIR)/lib && ( test "`echo *.la`" = "*.la" && true || cd $(OUTDIR)/lib && for i in *.la ; do dos2unix $$i ; done )


