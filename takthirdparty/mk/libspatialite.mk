ifeq ($(and $(libspatialite_CFLAGS)),)
    $(error Required var not set)
endif

libspatialite_libfile=$(LIB_PREFIX)spatialite.$(LIB_STATICSUFFIX)

# Compiler to build binaries that run on build *host*
libspatialite_HOSTCC_win32=$(VS_SETUP_win32) cl
libspatialite_HOSTCC=$(if $(libspatialite_HOSTCC_$(PLATFORM)),$(libspatialite_HOSTCC_$(PLATFORM)),gcc)

include mk/libspatialite-common.mk

jsqlite_fixedflags :=                                                 \
    -D__ANDROID__                                                     \
    -DHAVE_SQLITE2=0                                                  \
    -DHAVE_SQLITE3=1                                                  \
    -DHAVE_SQLITE3_MALLOC=1                                           \
    -DHAVE_SQLITE3_OPEN_V2=1                                          \
    -DHAVE_SQLITE3_PREPARE_V2=1                                       \
    -DHAVE_SQLITE3_PREPARE16_V2=1                                     \
    -DHAVE_SQLITE3_CLEAR_BINDINGS=1                                   \
    -DHAVE_SQLITE3_BIND_PARAMETER_COUNT=1                             \
    -DHAVE_SQLITE3_BIND_PARAMETER_NAME=1                              \
    -DHAVE_SQLITE3_BIND_PARAMETER_INDEX=1                             \
    -DHAVE_SQLITE3_BIND_ZEROBLOB=1                                    \
    -DHAVE_SQLITE3_RESULT_ZEROBLOB=1                                  \
    -DHAVE_SQLITE3_INCRBLOBIO=1                                       \
    -DHAVE_SQLITE3_SHARED_CACHE=1                                     \
    -DHAVE_SQLITE_SET_AUTHORIZER=1                                    \
    -DHAVE_SQLITE3_BACKUPAPI=1                                        \
    -DHAVE_SQLITE3_PROFILE=1                                          \
    -DHAVE_SQLITE3_STATUS=1                                           \
    -DHAVE_SQLITE3_DB_STATUS=1                                        \
    -DHAVE_SQLITE3_STMT_STATUS=1                                      \
    -DHAVE_SQLITE3_LOAD_EXTENSION=1                                   \
    -DCANT_PASS_VALIST_AS_CHARPTR=1


libspatialite_configtouchfile=$(OUTDIR)/$(libspatialite_srcdir)/.configured


$(libspatialite_configtouchfile): $(libspatialite_srctouchfile)
	cd $(OUTDIR)/$(libspatialite_srcdir)                                                 && \
		CFLAGS="$(libspatialite_CFLAGS) -fvisibility=hidden -I$(OUTDIR_CYGSAFE)/include"    \
		CPPFLAGS="$(CPPFLAGS) -I$(OUTDIR_CYGSAFE)/include"                                  \
		LDFLAGS="$(libspatialite_LDFLAGS) -L$(OUTDIR_CYGSAFE)/lib"                          \
		CC="$(CC)"                                                                          \
		CPP="$(CPP)"                                                                        \
		CXX="$(CXX)"                                                                        \
		LIBXML2_CFLAGS="$(shell $(OUTDIR)/libxml2/xml2-config --cflags)"                    \
		LIBXML2_LIBS="$(shell $(OUTDIR)/libxml2/xml2-config --libs)"                        \
		LIBS="-lcrypto $(libspatialite_LIBS)"                                               \
		./configure                                                                         \
		$(CONFIGURE_TARGET)                                                                 \
		$(CONFIGURE_$(BUILD_TYPE))                                                          \
		--enable-geos                                                                       \
		--enable-proj                                                                       \
		--enable-iconv                                                                      \
		--enable-libxml2                                                                    \
		--disable-freexl                                                                    \
		--with-geosconfig=$(OUTDIR)/bin/geos-config                                         \
		--disable-examples                                                                  \
		--prefix=$(OUTDIR_CYGSAFE)
	touch $@

#		--disable-iconv                              \
#		--disable-libxml2                            \


# This is phony because we always want to be invoking libspatialite make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: libspatialite_build
libspatialite_build: $(libspatialite_configtouchfile)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(libspatialite_srcdir)

$(libspatialite_src_lib): libspatialite_build
	@echo "libspatialite built"

$(libspatialite_out_lib): $(libspatialite_src_lib)
	$(MAKE) -j `nproc` -C $(OUTDIR)/$(libspatialite_srcdir) \
		mkinstalldirs="mkdir -p"                            \
		install
	cd $(OUTDIR)/lib && ( test "`echo *.la`" = "*.la" && true || cd $(OUTDIR)/lib && for i in *.la ; do dos2unix $$i ; done )

