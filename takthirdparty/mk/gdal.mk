ifeq ($(and $(gdal_CFLAGS)),)
    $(error Required var not set)
endif

gdal_libfile=$(LIB_PREFIX)gdal.$(LIB_SHAREDSUFFIX)
gdal_local_libfile=$(OUTDIR)/$(gdal_local_srcdir)/$(gdal_libfile)
gdal_out_lib=$(OUTDIR)/lib/$(gdal_libfile)
gdal_local_javalibs=$(foreach ilib,gdalalljni,$(OUTDIR)/$(gdal_local_srcdir)/swig/java/$(LIB_PREFIX)$(ilib)$(LIB_SHAREDSUFFIX))
gdal_out_javalibs=$(foreach ilib,gdalalljni,$(OUTDIR)/lib/$(LIB_PREFIX)$(ilib)$(LIB_SHAREDSUFFIX))

include mk/gdal-common.mk


gdal_configtouchfile=$(OUTDIR)/$(gdal_local_srcdir)/.configured

gdal_kdu_yes=--with-kakadu=$(call PATH_CYGSAFE,$(OUTDIR)/kdu)
gdal_kdu=$(gdal_kdu_$(GDAL_USE_KDU))

gdal_mrsid_yes=--with-mrsid=$(OUTDIR_CYGSAFE)                              \
gdal_mrsid=$(gdal_mrsid_$(GDAL_USE_MRSID))


$(gdal_configtouchfile): $(OUTDIR)/$(gdal_local_srcdir)/configure
	cd $(OUTDIR)/$(gdal_local_srcdir) &&                                \
		CFLAGS="$(gdal_CFLAGS)"                                     \
		CXXFLAGS="$(gdal_CXXFLAGS)"                                 \
		LDFLAGS="$(gdal_LDFLAGS)"                                   \
		CC="$(CC)"                                                  \
		CPP="$(CPP)"                                                \
		CXX="$(CXX)"                                                \
		SO_EXT="$$(echo $$LIB_SHAREDSUFFIX |awk '{ s=substr($$0, 2); print $s; }' )"                             \
		$(if $(LN_S),LN_S="$(LN_S)",)                               \
		$(if $(gdal_LIBS),LIBS="$(gdal_LIBS)",)                     \
		./configure                                                 \
		$(CONFIGURE_TARGET)                                         \
		$(CONFIGURE_$(BUILD_TYPE))                                  \
		$(gdal_kdu)                                                 \
		--with-libkml=$(OUTDIR_CYGSAFE)                             \
		--with-expat=$(OUTDIR_CYGSAFE)                              \
		$(gdal_mrsid)                                               \
		--with-curl=$(OUTDIR)/bin/curl-config                       \
		--with-xml2=$(OUTDIR)/libxml2/xml2-config                   \
		--with-geos=$(OUTDIR)/bin/geos-config                       \
		--with-sqlite3=$(OUTDIR_CYGSAFE)                            \
		--with-ogdi=$(OUTDIR_CYGSAFE)                               \
		--without-lerc                                              \
		--with-pdfium=$(OUTDIR_CYGSAFE)/pdfium                      \
		--with-pdfium-extra-lib-for-test=                           \
		--enable-static                                             \
		--prefix=$(OUTDIR_CYGSAFE)
	touch $@

# This is phony because we always want to be invoking gdal make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: gdal_build gdal_java gdal_java_data
gdal_build: $(OUTDIR)/$(gdal_local_srcdir) $(gdal_configtouchfile)
	$(MAKE) -C $(OUTDIR)/$(gdal_local_srcdir) $(gdal_BUILD_MAKEOVERRIDES)

gdal_java: $(OUTDIR)/$(gdal_local_srcdir) $(gdal_configtouchfile)
	$(MAKE) -C $(OUTDIR)/$(gdal_local_srcdir)/swig                   \
		$(if $(SWIG),SWIG="$(call PATH_CYGSAFE,$(SWIG))",)       \
		$(if $(gdal_KILL_JAVA_INCLUDE),JAVA_INCLUDE="",)         \
		BINDINGS=java                                            \
		build

gdal_java_data:
	rm -rf $(OUTDIR)/$(gdal_local_srcdir)/data/CVS
	cd $(OUTDIR)/$(gdal_local_srcdir)/data &&                        \
            ls -1 > gdaldata.files
	cd $(OUTDIR) &&                                                  \
            $(JAVA_HOME)/bin/jar cvf $(OUTDIR)/java/gdaldata.jar ./gdal/data
	rm -f $(OUTDIR)/$(gdal_local_srcdir)/data/gdaldata.files

$(gdal_local_libfile): gdal_build
	@echo "gdal built"

$(gdal_local_javalibs): gdal_java gdal_java_data
	@echo "gdal Java Bindings built"

$(gdal_out_lib): $(gdal_local_libfile)
	$(MAKE) -C $(OUTDIR)/$(gdal_local_srcdir) install
	cd $(OUTDIR)/lib && ( test "`echo *.la`" = "*.la" && true || cd $(OUTDIR)/lib && for i in *.la ; do dos2unix $$i ; done )

$(gdal_out_javalibs): $(gdal_local_javalibs)
	$(MAKE) -C $(OUTDIR)/$(gdal_local_srcdir)/swig BINDINGS=java install
	cp $(OUTDIR)/$(gdal_local_srcdir)/swig/java/gdal.jar $(OUTDIR)/java/

