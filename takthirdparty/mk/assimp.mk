include mk/assimp-common.mk

assimp_local_libfile=$(assimp_local_srcdir)/$(assimp_builddir)/code/$(assimp_libfile)
assimp_local_jlibfile=$(assimp_local_srcdir)/$(assimp_builddir)/$(LIB_PREFIX)jassimp.$(LIB_SHAREDSUFFIX)

assimp_out_jlib=$(OUTDIR)/lib/$(LIB_PREFIX)jassimp.$(LIB_SHAREDSUFFIX)

.PHONY: assimp_build
assimp_build: cmake_check $(assimp_local_srcdir)
	cd $(assimp_local_srcdir)/$(assimp_builddir)               &&     \
          $(CMAKE) -G "Unix Makefiles" -DCMAKE_BUILD_TYPE=Release         \
              -DCMAKE_INSTALL_PREFIX=../../../                            \
              -DZLIB_HOME=$(OUTDIR_CYGSAFE)                               \
              -DZLIB_ROOT=$(OUTDIR_CYGSAFE)                               \
              $(assimp_CONFIG_EX)                                         \
              ../                      &&                                 \
	    make assimp && make jassimp
	cd $(assimp_local_srcdir)/port/jassimp && ant

$(assimp_local_libfile): assimp_build

$(assimp_out_jlib): $(assimp_local_jlibfile) $(assimp_local_srcdir)/port/jassimp/dist/jassimp.jar
	$(CP) $(assimp_local_jlibfile) $@
	$(STRIP) $@
	$(CP) $(assimp_local_srcdir)/port/jassimp/dist/jassimp.jar $(OUTDIR)/java/

$(assimp_out_lib): $(assimp_local_libfile)
	$(CP) $(assimp_local_libfile) $@
	$(STRIP) $@

assimp: $(assimp_out_lib) $(assimp_out_jlib) assimp_install_headers
