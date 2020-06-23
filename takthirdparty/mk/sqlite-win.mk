sqlite_libfile=$(LIB_PREFIX)sqlite3.$(LIB_SHAREDSUFFIX)

include mk/sqlite-common.mk


sqlite_fixedflags :=                                                  \
    $(sqlite_fixedflags_common)                                       \
    -DSQLITE_API='_declspec(dllexport)'                               \


sqlite_implib=sqlite3_i.lib
sqlite_src_implib=$(OUTDIR)/$(sqlite_srcdir)/$(sqlite_implib)
sqlite_out_implib=$(OUTDIR)/lib/$(sqlite_implib)



$(OUTDIR)/$(sqlite_srcdir)/sqlite3.c: $(sqlite_srctouchfile)
	cd $(OUTDIR)/$(sqlite_srcdir) &&                              \
            TTP_EXTRA_PATH="/cygdrive/c/tcl/bin" $(VS_SETUP)          \
		nmake /f Makefile.msc sqlite3.c

$(sqlite_src_lib): $(OUTDIR)/$(sqlite_srcdir)/sqlite3.c
	cd $(OUTDIR_CYGSAFE)/$(sqlite_srcdir) &&                      \
	    $(VS_SETUP) cl -O2 $(call PATH_CYGSAFE,$<)                \
	    /I $(OUTDIR_CYGSAFE)/include                              \
	    $(OUTDIR_CYGSAFE)/lib/libcrypto.lib                       \
	    $(sqlite_fixedflags) -link                                \
	    -dll -out:$(call PATH_CYGSAFE,$@)                         \
	    -implib:$(call PATH_CYGSAFE,$(sqlite_src_implib))
	@echo "sqlite built"

$(sqlite_out_lib): $(sqlite_src_lib)
	$(CP) $< $@
	$(CP) $(sqlite_src_implib) $(sqlite_out_implib)
	$(CP) $(OUTDIR)/$(sqlite_srcdir)/sqlite3*.h $(OUTDIR)/include/

