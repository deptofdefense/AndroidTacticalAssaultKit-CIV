ifeq ($(and $(sqlite_CFLAGS)),)
    $(error Required var not set)
endif

sqlite_libfile=$(LIB_PREFIX)sqlite3.$(LIB_STATICSUFFIX)


include mk/sqlite-common.mk

sqlite_fixedflags :=                                                  \
    $(sqlite_fixedflags_common)                                       \
    -DHAVE_USLEEP=1                                                   \
    -DSQLITE_OMIT_BUILTIN_TEST=1

# Build CC (cc that builds things that run on build host, rather than target
# Uses detection in configure if not set
bcc_win32=BUILD_CC="$(VS_SETUP_win32) cl"

$(OUTDIR)/$(sqlite_srcdir)/sqlite3.c: $(sqlite_srctouchfile)
	cd $(OUTDIR)/$(sqlite_srcdir) &&                            \
		LDFLAGS=-L$(OUTDIR_CYGSAFE)/lib                     \
                CFLAGS="$(sqlite_CFLAGS) -I$(OUTDIR_CYGSAFE)/include" \
		CC="$(CC)"                                          \
		$(bcc_$(PLATFORM))                                  \
		./configure --prefix=$(OUTDIR)                      \
			$(CONFIGURE_TARGET)                         \
			--disable-tcl && make sqlite3.c

$(OUTDIR)/$(sqlite_srcdir)/sqlite3.o: $(OUTDIR)/$(sqlite_srcdir)/sqlite3.c
	$(CC) -c -I. -I$(OUTDIR_CYGSAFE)/include $(sqlite_CFLAGS) $(sqlite_fixedflags) -o $(OUTDIR_CYGSAFE)/$(sqlite_srcdir)/sqlite3.o $(OUTDIR_CYGSAFE)/$(sqlite_srcdir)/sqlite3.c

$(sqlite_src_lib): $(OUTDIR)/$(sqlite_srcdir)/sqlite3.o
	rm -rf $(OUTDIR)/$(sqlite_srcdir)/tmp
	mkdir -p $(OUTDIR)/$(sqlite_srcdir)/tmp
	#cd $(OUTDIR)/$(sqlite_srcdir)/tmp && $(AR) x $(OUTDIR)/lib/libcrypto.a && cp $< . && $(AR) rcs $@ *.o
	cd $(OUTDIR)/$(sqlite_srcdir)/tmp && cp $< . && $(AR) rcs $(call PATH_CYGSAFE,$@) *.o
	rm -rf $(OUTDIR)/$(sqlite_srcdir)/tmp
	@echo "sqlite built"

$(sqlite_out_lib): $(sqlite_src_lib)
	$(CP) $< $@
	$(CP) $(OUTDIR)/$(sqlite_srcdir)/sqlite3*.h $(OUTDIR)/include/

