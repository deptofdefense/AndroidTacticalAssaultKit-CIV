ifeq ($(and $(curl_win_arch)),)
    $(error Required var not set)
endif

include mk/curl-common.mk

# This is phony because we always want to be invoking curl make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: curl_build
curl_build: $(curl_srctouchfile)
	cd $(OUTDIR)/$(curl_srcdir)/winbuild &&                            \
		$(VS_SETUP) nmake /f Makefile.vc mode=dll                  \
			VC=12                                              \
			WITH_DEVEL=$(OUTDIR_CYGSAFE)                       \
			WITH_SSL=dll                                       \
			ENABLE_WINSSL=no                                   \
                        DEBUG=$(win_debug_yesno)

$(curl_src_lib): curl_build
	@echo "curl built"

$(curl_out_lib): $(curl_src_lib)
	cp -r $(OUTDIR)/$(curl_srcdir)/builds/libcurl-vc12-$(curl_win_arch)-$(BUILD_TYPE)-dll-ssl-dll-ipv6-sspi/{bin,lib,include} $(OUTDIR)/

