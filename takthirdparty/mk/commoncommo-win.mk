include mk/commoncommo-common.mk

ifeq ($(and $(commoncommo_win_platform)),)
    $(error Required var not set)
endif

.PHONY: commoncommo commoncommo_clean

commoncommo_objdir=$(commoncommo_src)/win32/$(commoncommo_win_objdir)/$(BUILD_TYPE)

commmoncommo_build_flags="/t:build" "/p:Configuration=$(BUILD_TYPE)" "/p:Platform=$(commoncommo_win_platform)"
commmoncommo_clean_flags="/t:clean" "/p:Configuration=$(BUILD_TYPE)" "/p:Platform=$(commoncommo_win_platform)"

commoncommo_dll=commoncommo.dll
commoncommo_lib=commoncommo.lib
commoncommo_csdll=TAK.Commo.dll
commoncommo_jnidll=commoncommojni.dll

commoncommo_src_dll=$(commoncommo_objdir)/$(commoncommo_dll)
commoncommo_src_lib=$(commoncommo_objdir)/$(commoncommo_lib)
commoncommo_src_csdll=$(commoncommo_objdir)/$(commoncommo_csdll)
commoncommo_src_jnidll=$(commoncommo_src)/win32/commoncommojni/$(commoncommo_win_platform)/$(BUILD_TYPE)/$(commoncommo_jnidll)


commoncommo_out_dll=$(OUTDIR)/bin/$(commoncommo_dll)
commoncommo_out_lib=$(OUTDIR)/lib/$(commoncommo_lib)
commoncommo_out_csdll=$(OUTDIR)/csharp/$(commoncommo_csdll)
commoncommo_out_jnidll=$(OUTDIR)/bin/$(commoncommo_jnidll)


.PHONY: commoncommo_build
commoncommo_build: 
	cd $(commoncommo_src)/core/impl && make buildstampgen &&        \
	    touch versionimpl.cpp
	cd $(commoncommo_src)/win32 && \
	    export CommoBuildId="COMMO_BUILD_STAMP=\"`cat ../core/impl/.bstamp`\"" && \
	    $(VS_SETUP) MSBuild $(commmoncommo_build_flags)             \
                                  commocppcli.sln


commoncommo_build_jni:
	cd $(commoncommo_src)/jni && ant $(commoncommo_ANT_FLAGS) jar
	cd $(commoncommo_src)/core/impl && make buildstampgen &&        \
	    touch versionimpl.cpp
	cd $(commoncommo_src)/win32/commoncommojni &&                   \
	    export CommoBuildId="COMMO_BUILD_STAMP=\"`cat ../../core/impl/.bstamp`\"" && \
	    $(VS_SETUP) MSBuild $(commmoncommo_build_flags)             \
                                  commoncommojni.vcxproj

$(commoncommo_src_csdll): commoncommo_build
	@echo "commoncommo C# dll built"

$(commoncommo_src_dll): commoncommo_build
	@echo "commoncommo core dll built"
	
$(commoncommo_src_jnidll): commoncommo_build_jni
	@echo "commoncommojni dll built"

$(commoncommo_out_csdll): $(commoncommo_src_csdll)
	@mkdir -p $(OUTDIR)/csharp
	$(CP) $< $@
	$(CP) $(commoncommo_objdir)/TAK.Commo.pdb $(OUTDIR)/csharp/
	$(CP) $(commoncommo_objdir)/TAK.Commo.xml $(OUTDIR)/csharp/

$(commoncommo_out_dll): $(commoncommo_src_dll)
	$(CP) $< $@
	$(CP) $(commoncommo_src_lib) $(commoncommo_out_lib)
	$(CP) $(commoncommo_objdir)/commoncommo.pdb $(OUTDIR)/bin/
	
$(commoncommo_out_jnidll): $(commoncommo_src_jnidll)
	$(CP) $< $@
	$(CP) $(commoncommo_src)/jni/jcommoncommo.jar $(OUTDIR)/java/

commoncommo: $(commoncommo_out_dll) $(commoncommo_out_csdll) $(commoncommo_out_jnidll)

commoncommo_clean:
	cd $(commoncommo_src)/win32 &&                             \
	    $(VS_SETUP) MSBuild $(commmoncommo_clean_flags)        \
	                             commocppcli.sln
	cd $(commoncommo_src)/win32/commoncommojni &&              \
	    $(VS_SETUP) MSBuild $(commmoncommo_clean_flags)        \
	                             commoncommojni.vcxproj
	rm -f $(commoncommo_out_lib)
	rm -f $(commoncommo_out_csdll)
	rm -f $(commoncommo_out_dll)
	rm -f $(commoncommo_out_jnidll)
	rm -f $(OUTDIR)/java/jcommoncommo.jar
