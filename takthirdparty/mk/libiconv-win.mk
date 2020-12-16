ifeq ($(and $(libiconv_win_confhost)),)
    $(error Required var not set)
endif

include mk/libiconv-common.mk


# From iconv's README.windows


libiconv_configtouchfile=$(OUTDIR)/$(libiconv_srcdir)/.configured
icv_sd=$(OUTDIR)/$(libiconv_srcdir)

winsh=$(shell cygpath -m /bin/bash)

$(libiconv_configtouchfile): $(libiconv_srctouchfile)
	sed -e '/^OBJECTS_RES_yes = /d' $(icv_sd)/src/Makefile.in >        \
		$(icv_sd)/src/Makefile.in.new &&                         \
		mv $(icv_sd)/src/Makefile.in.new $(icv_sd)/src/Makefile.in
	sed -e '/^OBJECTS_RES_yes = /d' $(icv_sd)/lib/Makefile.in >        \
		$(icv_sd)/lib/Makefile.in.new &&                         \
		mv $(icv_sd)/lib/Makefile.in.new $(icv_sd)/lib/Makefile.in
	@echo "cd $(icv_sd)" > $(icv_sd)/makescript
	@echo "export PATH=\"/usr/bin:\$$PATH\"" >> $(icv_sd)/makescript
	@cp $(icv_sd)/makescript $(icv_sd)/makeiscript
	@echo "$(MAKE) -C $(OUTDIR)/$(libiconv_srcdir) mkinstalldirs=\"mkdir -p\" install-lib" >> $(icv_sd)/makeiscript
	@echo "$(MAKE) -C $(OUTDIR)/$(libiconv_srcdir)" >> $(icv_sd)/makescript
	@echo "cd $(icv_sd)" > $(icv_sd)/confscript
	@echo "export PATH=\"/usr/bin:\$$PATH\"" >> $(icv_sd)/confscript
	@echo                                           \
		CC=\"$(icv_sd)/build-aux/compile cl -nologo\"      \
		CXX=\"$(icv_sd)/build-aux/compile cl -nologo\"      \
		CFLAGS=\"-MD\"                      \
		CXXFLAGS=\"-MD\"                  \
		CPPFLAGS=\"-D_WIN32_WINNT=_WIN32_WINNT_WIN7\"                  \
		LDFLAGS=\"\"                    \
		LD=\"link\" \
		NM=\"dumpbin -symbols\" \
		STRIP=\":\" \
		AR=\"$(icv_sd)/build-aux/ar-lib lib\" \
		RANLIB=\":\" \
		./configure                                      \
		--host=$(libiconv_win_confhost)                  \
		$(CONFIGURE_$(BUILD_TYPE))                       \
		--enable-shared                                  \
		--disable-static                                 \
		--prefix=$(OUTDIR_CYGSAFE) >> $(icv_sd)/confscript
	@echo dos2unix $(icv_sd)/makescript
	@echo dos2unix $(icv_sd)/makeiscript
	@echo dos2unix $(icv_sd)/confscript
	chmod a+x $(icv_sd)/makescript
	chmod a+x $(icv_sd)/makeiscript
	chmod a+x $(icv_sd)/confscript
	$(VS_SETUP) $(winsh) -l $(icv_sd)/confscript
	touch $@

# This is phony because we always want to be invoking libiconv make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: libiconv_build
libiconv_build: $(libiconv_configtouchfile)
	$(VS_SETUP) $(winsh) -l -c $(icv_sd)/makescript

$(libiconv_src_lib): libiconv_build
	@echo "libiconv built"

$(libiconv_out_lib): $(libiconv_src_lib)
	$(VS_SETUP) $(winsh) -l -c $(icv_sd)/makeiscript
