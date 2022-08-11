.PHONY: pri

pri_libfile=$(LIB_PREFIX)pri.$(LIB_SHAREDSUFFIX)
pri_out_lib=$(OUTDIR)/lib/$(pri_libfile)

include mk/pri-common.mk

pri_local_libfile=$(pri_local_srcdir)/pricpp/src/pri/$(pri_libfile)

# This is phony because we always want to be invoking PRI's build to be sure
# the file is up to date;  it knows if anything needs to be done
.PHONY: $(pri_local_libfile)
$(pri_local_libfile): $(pri_local_srcdir)
	$(MAKE) -j `nproc` -C $(pri_local_srcdir)/pricpp/src/pri CC=$(CC) CXX=$(CXX) \
		PRI_CXXFLAGS="$(pri_CXXFLAGS) -I$(OUTDIR_CYGSAFE)/include -L$(OUTDIR_CYGSAFE)/lib" \
		PRI_SOSUFFIX="$(LIB_SHAREDSUFFIX)"

$(pri_out_lib): $(pri_local_libfile)
	$(CP) $< $@

pri: $(pri_out_lib) pri_install_headers

