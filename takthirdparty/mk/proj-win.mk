include mk/proj-common.mk

proj_configtouchfile=$(OUTDIR)/$(proj_srcdir)/.configured


proj_nmake_opts=INSTDIR='$(OUTDIR_WIN)'                                     \
                DEBUG=$(win32_debug_en)                                     \
                EXE_PROJ=proj_i.lib

# This is phony because we always want to be invoking proj make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: proj_build
proj_build: $(proj_srctouchfile)
	cd $(OUTDIR)/$(proj_srcdir) &&                                      \
	    $(proj_nmake_opts) $(VS_SETUP) nmake /f makefile.vc

$(proj_src_lib): proj_build
	@echo "proj built"

$(proj_out_lib): $(proj_src_lib)
	cd $(OUTDIR)/$(proj_srcdir) &&                                      \
	    $(proj_nmake_opts) $(VS_SETUP) nmake /f makefile.vc install-all

