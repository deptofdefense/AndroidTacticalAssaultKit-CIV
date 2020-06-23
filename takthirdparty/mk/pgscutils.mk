ifeq ($(and $(pgscutils_CXXFLAGS),$(pgscutils_LIBARCHDIR)),)
    $(error Required var not set)
endif

pgscutils_libdir=lib/$(pgscutils_LIBARCHDIR)

include mk/pgscutils-common.mk


# This is phony because we always want to be invoking pgscutils make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: pgscutils_build
pgscutils_build: $(OUTDIR)/$(pgscutils_local_srcdir)
	$(MAKE) -C $(OUTDIR)/$(pgscutils_local_srcdir)/                \
		PGSC_UTILS_CXXFLAGS="$(pgscutils_CXXFLAGS)"            \
		PGSC_UTILS_LDFLAGS="$(pgscutils_LDFLAGS)"              \
		$(if $(pgscutils_OS),OS="$(pgscutils_OS)",)            \
		$(if $(pgscutils_ARCH),ARCH="$(pgscutils_ARCH)",)      \
		CXX=$(CXX)                                             \
		$(if $(RANLIB),RANLIB=$(RANLIB),)                      \
		INCLUDE_CP="$(CP)"                                     \
                all-notests


$(pgscutils_local_libfile): pgscutils_build
	@echo "pgscutils built"

$(pgscutils_out_libfile): $(pgscutils_local_libfile)
	$(CP) $< $@

pgscutils_clean:
	rm -rf $(OUTDIR)/$(pgscutils_local_srcdir)
	rm -f $(pgscutils_out_libfile)
