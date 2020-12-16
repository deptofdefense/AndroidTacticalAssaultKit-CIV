include host-detect.mk

# no default target - force to specify
TARGET=

# default to release build
BUILD_TYPE=release

# Default off, force opt in
GDAL_USE_KDU=no
gdal_kdu_pkg_yes=kdu
gdal_kdu_pkg=$(gdal_kdu_pkg_$(GDAL_USE_KDU))

GDAL_USE_MRSID=no
gdal_mrsid_pkg_yes=mrsid
gdal_mrsid_pkg=$(gdal_mrsid_pkg_$(GDAL_USE_MRSID))

# Dist file directory
DISTFILESDIR:=$(shell pwd)/distfiles

# Target output directory
TARGET_OUTDIR:=$(shell pwd)/builds/$(TARGET)-$(BUILD_TYPE)
OUTDIR=$(TARGET_OUTDIR)
# Cygwin-safe variant. This is cygwin "mixed" form suitable for use
# by most Win32 native apps that don't get Cygwin paths
OUTDIR_CYGSAFE:=$(call PATH_CYGSAFE,$(TARGET_OUTDIR))
OUTDIR_WIN:=$(call PATH_WIN,$(TARGET_OUTDIR))

# MS VC++ build environment setup script
# VS2013
#VS_SETUP=$(shell pwd)/mk/vs12_x86.sh
# VS2015
VS_SETUP_win32=$(shell pwd)/mk/vs14_x86.sh
VS_SETUP_win64=$(shell pwd)/mk/vs14_x64.sh
VS_SETUP=$(VS_SETUP_$(TARGET))

# MS VC++ platform toolset version string
# Use below for VS2013
#VS_VER_MSB=v120
# Same, but for VS2015
# Note also that some packages (gdal at least) have their own settings
# keyed off of this.  2015 and 2013 are supported but new ones will need
# additional work
VS_VER_MSB=v140

# All packages shared across gdal and spatialite
ALL_PACKAGES_gspatial_shared=geos proj sqlite
# All packages shared across commo and gdal
ALL_PACKAGES_cshared=curl
# All packages shared across all top level builds
ALL_PACKAGES_allshared=zlib
# Packages used by everything except pri...
ALL_PACKAGES_shared=libiconv libxml2 openssl
# All the packages, in build order
ALL_PACKAGES_commoncommo=protobuf pgscthread libmicrohttpd commoncommo
packages_commoncommo-$(TARGET):=$(ALL_PACKAGES_allshared) $(ALL_PACKAGES_shared) $(ALL_PACKAGES_cshared) $(ALL_PACKAGES_commoncommo)
ALL_PACKAGES_gdal=$(gdal_kdu_pkg) pdfium $(gdal_mrsid_pkg) expat ogdi libkml gdal
packages_gdal-$(TARGET):=$(ALL_PACKAGES_allshared) $(ALL_PACKAGES_shared) $(ALL_PACKAGES_cshared) $(ALL_PACKAGES_gspatial_shared) $(ALL_PACKAGES_gdal)
ALL_PACKAGES_spatialite=libspatialite
packages_spatialite-$(TARGET):=$(ALL_PACKAGES_allshared) $(ALL_PACKAGES_shared) $(ALL_PACKAGES_gspatial_shared) $(ALL_PACKAGES_spatialite)
ALL_PACKAGES_pri=pri
packages_pri-$(TARGET):=$(ALL_PACKAGES_allshared) $(ALL_PACKAGES_pri)
ALL_PACKAGES_assimp=assimp
packages_assimp-$(TARGET):=$(ALL_PACKAGES_allshared) $(ALL_PACKAGES_assimp)
ALL_PACKAGES=$(ALL_PACKAGES_shared) $(ALL_PACKAGES_allshared) $(ALL_PACKAGES_gspatial_shared) $(ALL_PACKAGES_cshared) $(ALL_PACKAGES_commoncommo) $(ALL_PACKAGES_gdal) $(ALL_PACKAGES_spatialite) $(ALL_PACKAGES_pri) $(ALL_PACKAGES_assimp)
packages-$(TARGET):=$(ALL_PACKAGES)

.PHONY: help usage prebuild build build_pri build_gdal build_commoncommo build_spatialite build_assimp veryclean clean
help:
	@echo "Usage: make TARGET=targetname [ BUILD_TYPE=debug | release ] [ GDAL_USE_KDU=no | yes ] [GDAL_USE_MRSID=no | yes ] < build | build_gdal | build_commoncommo | build_spatialite | build_pri | build_assimp | clean | veryclean >"
	@echo "Option defaults:"
	@echo "    BUILD_TYPE:   release"
	@echo "    GDAL_USE_KDU: no"
	@echo "    GDAL_USE_MRSID: no"
	@echo "Known targets:"
	@for i in $(sort $(basename $(notdir $(wildcard target-config/*.mk)))) ; do echo "    $$i" ; done
usage: help

ifeq ($(TARGET),)

build veryclean clean:
	@echo "Error: TARGET not set. Set TARGET"
	$(MAKE) help

else
include target-config/$(TARGET).mk

#######################################
# Common tools

# cp that preserves timestamping
CP=cp -p

# Combine base flags
CFLAGS_COMMON=$(strip $(CFLAGS_generic) $(CFLAGS_$(BUILD_TYPE)))
CXXFLAGS_COMMON=$(strip $(CXXFLAGS_generic) $(CXXFLAGS_$(BUILD_TYPE)))
LDFLAGS_COMMON=$(strip $(LDFLAGS_generic) $(LDFLAGS_$(BUILD_TYPE)))
# Combine flags and things for each package
$(foreach pkg,$(packages-$(TARGET)),$(eval $(pkg)_CFLAGS=$(strip $(CFLAGS_COMMON) $($(pkg)_CFLAGS_generic) $($(pkg)_CFLAGS_$(BUILD_TYPE)))))
$(foreach pkg,$(packages-$(TARGET)),$(eval $(pkg)_CXXFLAGS=$(strip $(CXXFLAGS_COMMON) $($(pkg)_CXXFLAGS_generic) $($(pkg)_CXXFLAGS_$(BUILD_TYPE)))))
$(foreach pkg,$(packages-$(TARGET)),$(eval $(pkg)_LDFLAGS=$(strip $(LDFLAGS_COMMON) $($(pkg)_LDFLAGS_generic) $($(pkg)_LDFLAGS_$(BUILD_TYPE)))))

include $(foreach pkg,$(packages-$(TARGET)),$(or $(wildcard mk/$(pkg)-$(TARGET).mk),mk/$(pkg).mk))


$(TARGET_OUTDIR):
	@mkdir -p $(TARGET_OUTDIR)
	@mkdir -p $(TARGET_OUTDIR)/bin
	@mkdir -p $(TARGET_OUTDIR)/include
	@mkdir -p $(TARGET_OUTDIR)/lib
	@mkdir -p $(TARGET_OUTDIR)/java

# Fake target for just prepping output directories and toolchains
prebuild: $(TARGET_OUTDIR) $($(TARGET)_prep)

build: BUILD_GOAL = build
build: $(TARGET_OUTDIR) $($(TARGET)_prep) $(foreach pkg,$(packages-$(TARGET)),$(pkg))
	@echo "Done! Debug: $(BUILD_DEBUG)"

build_gdal: BUILD_GOAL = build_gdal
build_gdal: $(TARGET_OUTDIR) $($(TARGET)_prep) $(foreach pkg,$(packages_gdal-$(TARGET)),$(pkg))
	@echo "Done! Debug: $(BUILD_DEBUG)"

build_spatialite: BUILD_GOAL = build_spatialite
build_spatialite: $(TARGET_OUTDIR) $($(TARGET)_prep) $(foreach pkg,$(packages_spatialite-$(TARGET)),$(pkg))
	@echo "Done! Debug: $(BUILD_DEBUG)"

build_commoncommo: BUILD_GOAL = build_commoncommo
build_commoncommo: $(TARGET_OUTDIR) $($(TARGET)_prep) $(foreach pkg,$(packages_commoncommo-$(TARGET)),$(pkg))
	@echo "Done! Debug: $(BUILD_DEBUG)"

build_pri: BUILD_GOAL = build_pri
build_pri: $(TARGET_OUTDIR) $($(TARGET)_prep) $(foreach pkg,$(packages_pri-$(TARGET)),$(pkg))
	@echo "Done! Debug: $(BUILD_DEBUG)"
  
build_assimp: BUILD_GOAL = build_assimp
build_assimp: $(TARGET_OUTDIR) $($(TARGET)_prep) $(foreach pkg,$(packages_assimp-$(TARGET)),$(pkg))
	@echo "Done! Debug: $(BUILD_DEBUG)"

veryclean: $($(TARGET)_veryclean)
	rm -rf $(TARGET_OUTDIR)

clean: $($(TARGET)_clean) $(foreach pkg,$(packages-$(TARGET)),$(pkg)_clean)
	

endif

.PHONY: tag
tag:
	@if [ "$(TTP_TAG)" = "" ] ; then echo "Specify TTP_TAG=<sometaghere>"; exit 1 ; fi
	git tag "$(TTP_TAG)"
	cd ../gdal && git tag "$(TTP_TAG)"
	cd ../commoncommo && git tag "$(TTP_TAG)"
	cd ../pgscthread && git tag "$(TTP_TAG)"
	cd ../pri && git tag "$(TTP_TAG)"
	if [ "$(GDAL_USE_KDU)" = "yes" ] ; then cd ../kdu && cvs tag "$(TTP_TAG)" ; fi
	@echo "Repositories tagged.  For git repos, be sure to go do a push now!"


