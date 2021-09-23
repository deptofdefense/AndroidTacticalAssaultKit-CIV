.PHONY: assimp assimp_clean

#ifeq ($(and $(assimp_libfile)),)
#    $(error Required var not set)
#endif

assimp_libfile=$(LIB_PREFIX)assimp.$(LIB_SHAREDSUFFIX)
assimp_src=../assimp
assimp_local_srcdir=$(OUTDIR)/assimp
assimp_incdir=$(OUTDIR)/include/assimp
assimp_builddir=ttpbuild
assimp_out_lib=$(OUTDIR)/lib/$(assimp_libfile)

# Check that the outer source location is clean
ifneq ($(wildcard $(assimp_src)/$(assimp_builddir)/*),)
    $(error assimp source dir $(assimp_src) not clean - go there and remove the build directory $(assimp_builddir)
endif

.PHONY: $(assimp_local_srcdir)
$(assimp_local_srcdir):
	$(CP) -r $(assimp_src) $(OUTDIR)
	rm -rf $(assimp_local_srcdir)/.git
	mkdir -p $(assimp_local_srcdir)/$(assimp_builddir)

$(assimp_incdir):
	mkdir -p $(assimp_incdir)

.PHONY: assimp_install_headers
assimp_install_headers: $(assimp_incdir)
	$(CP) -r $(assimp_local_srcdir)/include/assimp/* $(assimp_incdir)/
	$(CP) $(assimp_local_srcdir)/$(assimp_builddir)/include/assimp/config.h $(assimp_incdir)/

assimp_clean:
	rm -f $(assimp_out_lib)
	rm -rf $(assimp_local_srcdir)
	rm -rf $(assimp_incdir)
