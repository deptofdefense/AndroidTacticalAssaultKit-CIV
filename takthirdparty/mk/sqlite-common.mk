ifeq ($(and $(sqlite_libfile)),)
    $(error Required var from sqlite mk not set)
endif

.PHONY: sqlite sqlite_clean

sqlite_src=$(DISTFILESDIR)/sqlcipher.tar.gz
sqlite_srcdir=sqlite
sqlite_srctouchfile=$(OUTDIR)/$(sqlite_srcdir)/.unpacked
sqlite_out_lib=$(OUTDIR)/lib/$(sqlite_libfile)
sqlite_src_lib=$(OUTDIR)/$(sqlite_srcdir)/$(sqlite_libfile)
sqlite_patch=$(DISTFILESDIR)/sqlcipher-pgsc.patch


# Note that even though SqlCipher docs want TEMP_STORE=2, =3 is fine
# and was our preferred older configuration.
# See https://github.com/sqlite/sqlite/issues/108 for discussion
# on where some developers are actually pushing for recommending =3 instead
# of 2
sqlite_fixedflags_common :=                                           \
    -DSQLITE_THREADSAFE=1                                             \
    -DNDEBUG=1                                                        \
    -DSQLITE_ENABLE_MEMORY_MANAGEMENT=1                               \
    -DSQLITE_DEFAULT_JOURNAL_SIZE_LIMIT=1048576                       \
    -DSQLITE_ENABLE_FTS3                                              \
    -DSQLITE_ENABLE_FTS3_PARENTHESIS                                  \
    -DSQLITE_ENABLE_RTREE=1                                           \
    -DSQLITE_TEMP_STORE=3                                             \
    -DSQLITE_HAS_CODEC



$(sqlite_srctouchfile): $(sqlite_src) $(sqlite_patch)
	rm -rf $(OUTDIR)/$(sqlite_srcdir)
	cd $(OUTDIR) && tar xfz $<
	cd $(OUTDIR) && mv sqlcipher-* $(sqlite_srcdir)
	patch -p0 -d $(OUTDIR) < $(sqlite_patch)
	cd $(OUTDIR)/$(sqlite_srcdir) && chmod 755 configure
	touch $@

sqlite: $(sqlite_out_lib)

sqlite_clean:
	rm -rf $(OUTDIR)/$(sqlite_srcdir)
	rm -f $(sqlite_out_lib)
	rm -f $(OUTDIR)/include/sqlite3*.h

