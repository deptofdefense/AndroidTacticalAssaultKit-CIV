include mk/assimp-common.mk

assimp_local_libfile=$(assimp_local_srcdir)/$(assimp_builddir)/code/assimp.dll

assimp_implibfile=assimp.lib
assimp_local_implibfile=$(assimp_local_srcdir)/$(assimp_builddir)/code/assimp.lib
assimp_out_implib=$(OUTDIR)/lib/$(assimp_implibfile)

.PHONY: assimp_build
assimp_build: cmake_check $(assimp_local_srcdir)
	cd $(assimp_local_srcdir)/$(assimp_builddir)               &&     \
	    $(VS_SETUP)                                                   \
              \"`cygpath -m $(CMAKE)`\" -G \"NMake Makefiles\"            \
	          -DCMAKE_BUILD_TYPE=Release                              \
                  -DCMAKE_INSTALL_PREFIX=../../../                        \
                  -DLIBRARY_SUFFIX=                                       \
                  -DZLIB_HOME=$(OUTDIR_CYGSAFE)                           \
                  -DZLIB_ROOT=$(OUTDIR_CYGSAFE)                           \
                  $(assimp_win32_build_cmextraargs)                       \
                  ../                      &&                             \
	$(VS_SETUP) nmake assimp 

$(assimp_local_libfile): assimp_build
$(assimp_local_implibfile): assimp_build

$(assimp_out_lib): $(assimp_local_libfile) $(assimp_local_implibfile)
	$(CP) $(assimp_local_implibfile) $(assimp_out_implib)
	$(CP) $(assimp_local_libfile) $@

assimp: $(assimp_out_lib) assimp_install_headers
