# Library naming
LIB_PREFIX=
LIB_SHAREDSUFFIX=dll
LIB_STATICSUFFIX=lib

# Object file naming
OBJ_SUFFIX=obj

# Debug yes/no flag used by a number of win32 build scripts
win_debug_yesno_debug=yes
win_debug_yesno_release=no
win_debug_yesno=$(win_debug_yesno_$(BUILD_TYPE))
win32_debug_en_debug=yes
win32_debug_en=$(win32_debug_en_$(BUILD_TYPE))

openssl_CONFIG_debug=./Configure debug-VC-WIN32 no-asm --prefix=$(OUTDIR_CYGSAFE)
openssl_CONFIG_release=./Configure VC-WIN32 no-asm --prefix=$(OUTDIR_CYGSAFE)
openssl_CONFIG=$(openssl_CONFIG_$(BUILD_TYPE))

# Target-specific patches for libkml, space separated
libkml_EXTRAPATCHES=
# Target-specific patches to be applied before libkml's autoconf is run
libkml_EXTRAPATCHES_PREAC=

# Mr. SID binary bundle file path
# binbundle has mrsid/ top level directory with include/ under that
mrsid_BINBUNDLE_v120=$(DISTFILESDIR)/mrsid/win32.zip
mrsid_BINBUNDLE_v140=$(DISTFILESDIR)/mrsid/win32-v140.zip
mrsid_BINBUNDLE=$(mrsid_BINBUNDLE_$(VS_VER_MSB))
# Path within expansion of above where the Mr.Sid library can be found
# omit mrsid/ initial directory
mrsid_BINLIBPATH=lib/

libxml2_installtargets=install
