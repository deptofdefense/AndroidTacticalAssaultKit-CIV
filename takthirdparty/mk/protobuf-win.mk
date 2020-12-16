protobuf_libfile=$(LIB_PREFIX)protobuf-lite.$(LIB_STATICSUFFIX)

include mk/protobuf-common.mk

# This is phony because we always want to be invoking protobuf make to be sure
# the files are up to date;  it knows if anything needs to be done
.PHONY: protobuf_build
protobuf_build: protobuf_win32_build_cmdir=$(OUTDIR)/$(protobuf_srcdir)/cmake
protobuf_build: protobuf_win32_build_cmextraargs=-Dprotobuf_BUILD_SHARED_LIBS=ON
protobuf_build: protobuf_win32_build_vssetup=$(VS_SETUP)
protobuf_build: protobuf_cmake_check $(protobuf_hosttools_builttouchfile) $(protobuf_srctouchfile)
	$(protobuf_win32_build)

$(protobuf_out_lib): protobuf_build
