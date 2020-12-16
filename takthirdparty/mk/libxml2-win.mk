include mk/libxml2-common.mk

$(libxml2_configtouchfile): $(libxml2_srctouchfile)
	cd $(OUTDIR)/$(libxml2_srcdir)/win32 &&                 \
		cscript configure.js compiler=msvc              \
		prefix=`cygpath -w $(OUTDIR)`                   \
		iconv=no                                        \
		cruntime=/MD                                    \
		threads=native                                  \
		debug=$(win_debug_yesno)                        \
		vcmanifest=yes
	touch $@

# This is phony because we always want to be invoking libxml2 make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: libxml2_build
libxml2_build: $(libxml2_configtouchfile)
	cd $(OUTDIR)/$(libxml2_srcdir)/win32 && $(VS_SETUP) nmake

$(libxml2_src_lib): libxml2_build
	@echo "Libxml built"

$(libxml2_out_lib): $(libxml2_src_lib)
	cd $(OUTDIR)/$(libxml2_srcdir)/win32 && $(VS_SETUP) nmake install

