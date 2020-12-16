.PHONY: protobuf protobuf_clean

ifeq ($(and $(protobuf_libfile)),)
    $(error Required var not set)
endif

# Used on Windows build hosts only
PB_CMAKE="/cygdrive/c/Program Files (x86)/CMake/bin/cmake.exe"

protobuf_src=$(DISTFILESDIR)/protobuf.tar.gz
protobuf_patch=$(DISTFILESDIR)/protobuf-pgsc.patch
protobuf_srcdir=protobuf
protobuf_srctouchfile=$(OUTDIR)/$(protobuf_srcdir)/.unpacked
protobuf_out_lib=$(OUTDIR)/lib/$(protobuf_libfile)
protobuf_src_lib=$(OUTDIR)/$(protobuf_srcdir)/$(protobuf_libfile)

protobuf_hosttools_dir=$(OUTDIR)/host-protobuf
protobuf_hosttools_srcdir=$(protobuf_hosttools_dir)
protobuf_hosttools_srctouchfile=$(protobuf_hosttools_srcdir)/protobuf/.unpacked
protobuf_hosttools_builttouchfile=$(protobuf_hosttools_dir)/.built

$(protobuf_hosttools_srctouchfile): $(protobuf_src)
	rm -rf $(protobuf_hosttools_srcdir)
	mkdir -p $(protobuf_hosttools_srcdir)
	tar -x -z -f $< -C $(protobuf_hosttools_srcdir)
	cd $(protobuf_hosttools_srcdir) && mv protobuf-* protobuf
	cd $(protobuf_hosttools_srcdir)/protobuf && chmod 755 configure
	touch $@

$(protobuf_srctouchfile): $(protobuf_src) $(protobuf_patch)
	rm -rf $(OUTDIR)/$(protobuf_srcdir)
	tar -x -z -f $< -C $(OUTDIR)
	cd $(OUTDIR) && mv protobuf-* $(protobuf_srcdir)
	patch -p0 -d $(OUTDIR) < $(protobuf_patch)
	cd $(OUTDIR)/$(protobuf_srcdir) && chmod 755 configure
	touch $@



define protobuf_win32_build =
	cd $(protobuf_win32_build_cmdir) && mkdir -p $(BUILD_TYPE)
	cd $(protobuf_win32_build_cmdir)/$(BUILD_TYPE)                   && \
	    $(protobuf_win32_build_vssetup) \"`cygpath -m $(PB_CMAKE)`\" -G \"NMake Makefiles\" \
	          -DCMAKE_BUILD_TYPE=Release                                \
                  -DCMAKE_INSTALL_PREFIX=../../../                          \
                  $(protobuf_win32_build_cmextraargs)                       \
                  -Dprotobuf_BUILD_TESTS=off    ../                      && \
	    $(protobuf_win32_build_vssetup) nmake libprotobuf-lite &&       \
	    $(protobuf_win32_build_vssetup) nmake install
endef


ifeq ($(PLATFORM),win32)
$(protobuf_hosttools_builttouchfile): protobuf_win32_build_cmdir=$(protobuf_hosttools_srcdir)/protobuf/cmake
$(protobuf_hosttools_builttouchfile): protobuf_win32_build_vssetup=$(VS_SETUP_$(PLATFORM))
$(protobuf_hosttools_builttouchfile): $(protobuf_hosttools_srctouchfile)
	# build protobuf for host
	$(protobuf_win32_build)
	touch $@
protobuf_cmake_check:
	@[ -x $(PB_CMAKE) ] || ( echo "ERROR - CMake not installed or incorrect version" >&2 ; false )

else

$(protobuf_hosttools_builttouchfile): $(protobuf_hosttools_srctouchfile)
	cd $(protobuf_hosttools_srcdir)/protobuf && ./configure --prefix="$(protobuf_hosttools_dir)"
	cd $(protobuf_hosttools_srcdir)/protobuf && make && make install
	touch $@
endif

protobuf: $(protobuf_out_lib)

protobuf_clean:
	rm -rf $(protobuf_hosttools_dir)
	rm -rf $(OUTDIR)/$(protobuf_srcdir)
	rm -f $(protobuf_out_lib)
