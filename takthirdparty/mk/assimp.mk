include mk/assimp-common.mk

assimp_local_libfile=$(assimp_local_srcdir)/$(assimp_builddir)/code/$(assimp_libfile)
assimp_local_jlibfile=$(assimp_local_srcdir)/$(assimp_builddir)/libjassimp.so

assimp_out_jlib=$(OUTDIR)/lib/libjassimp.so

assimp_extra_cmake_armeabi-v7a=-DCMAKE_ANDROID_ARM_NEON=ON
assimp_extra_cmake_x86=

.PHONY: assimp_build
assimp_build: $(assimp_local_srcdir)
	cd $(assimp_local_srcdir)/$(assimp_builddir)               &&     \
          $(ASSIMP_CMAKE) -G "Unix Makefiles" -DCMAKE_BUILD_TYPE=Release  \
              -DCMAKE_SYSTEM_NAME=Android                                 \
              -DCMAKE_SYSTEM_VERSION=$(ANDROID_API_LEVEL)                 \
              -DCMAKE_ANDROID_STL_TYPE=gnustl_shared                      \
              -DCMAKE_ANDROID_ARCH_ABI=$(ANDROID_ABI)                     \
              $(assimp_extra_cmake_$(ANDROID_ABI))                        \
              -DCMAKE_ANDROID_STANDALONE_TOOLCHAIN=$(call PATH_CYGSAFE,$(OUTDIR)/toolchain)  \
              -DCMAKE_INSTALL_PREFIX=../../../                            \
              -DZLIB_HOME=$(OUTDIR_CYGSAFE)                               \
              -DZLIB_ROOT=$(OUTDIR_CYGSAFE)                               \
              ../                      &&                                 \
	    make assimp && make jassimp
	cd $(assimp_local_srcdir)/port/jassimp && ant

$(assimp_local_libfile): assimp_build

$(assimp_out_jlib): $(assimp_local_jlibfile) $(assimp_local_srcdir)/port/jassimp/dist/jassimp.jar
	$(CP) $(assimp_local_jlibfile) $@
	$(CP) $(assimp_local_srcdir)/port/jassimp/dist/jassimp.jar $(OUTDIR)/java/

$(assimp_out_lib): $(assimp_local_libfile)
	$(CP) $(assimp_local_libfile) $@

assimp: $(assimp_out_lib) $(assimp_out_jlib) assimp_install_headers
