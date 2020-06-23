# This is a pseudo target that does not build the packages
# in the traditional sense.  Rather, it builds nothing directly,
# instead recursively invoking make back upon this build system for
# all known IOS targets. Once complete, it rolls those outputs into
# one set of universal libraries.

# Override package list, setting it to nothing
packages_commoncommo-$(TARGET):=
packages_gdal-$(TARGET):=
packages_spatialite-$(TARGET):=
packages_pri-$(TARGET):=
packages-$(TARGET):=

# Our prep target
$(TARGET)_prep:=ios_universal_prep

# Our clean target
$(TARGET)_clean:=ios_universal_clean
$(TARGET)_veryclean:=ios_universal_veryclean

ios_rollup_targets:=ios-x86_64-simulator ios-armv7 ios-arm64
ios_rollup_do_pri=$(if $(findstring pri,$(BUILD_GOAL)),libpri.dylib,)
ios_rollup_do_gdal=$(if $(findstring gdal,$(BUILD_GOAL)),libgdal.dylib libproj.dylib,)
ios_rollup_do_commo=$(if $(findstring commo,$(BUILD_GOAL)),libcommoncommoobjc.dylib,)
ios_rollup_do_spatialite=$(if $(findstring spatialite,$(BUILD_GOAL)),libspatialite.dylib libproj.dylib,)
ios_rollup_do_all=libgdal.dylib libcommoncommoobjc.dylib libspatialite.dylib libproj.dylib libpri.dylib
ios_rollup_libs_direct=$(ios_rollup_do_pri) $(ios_rollup_do_commo) $(ios_rollup_do_gdal) $(ios_rollup_do_spatialite)
ios_rollup_libs=$(if $(ios_rollup_libs_direct),$(ios_rollup_libs_direct),$(ios_rollup_do_all))
ios_rollup_libs_full=$(foreach ilib,$(ios_rollup_libs),$(OUTDIR)/lib/$(ilib))


.PHONY: ios_universal_prep ios_universal_builds ios_universal_rollup ios_universal_headercopy
.PHONY: ios_universal_clean ios_universal_veryclean

# Arbitrarily chose first word in target list
ios_universal_headercopy:
	$(CP) -r $(OUTDIR)/../$(firstword $(ios_rollup_targets))-$(BUILD_TYPE)/include $(OUTDIR)/
	$(CP) -r $(OUTDIR)/../$(firstword $(ios_rollup_targets))-$(BUILD_TYPE)/libxml2 $(OUTDIR)/

ios_universal_builds:
	for tgt in $(ios_rollup_targets) ; do $(MAKE) TARGET=$$tgt BUILD_TYPE=$(BUILD_TYPE) $(BUILD_GOAL) ; done

ios_universal_rollup:
	for tgt in $(ios_rollup_libs_full) ; do \
            tbase=`basename $$tgt` ; \
            lipo -output $$tgt -create $(foreach itgt,$(ios_rollup_targets),$(OUTDIR)/../$(itgt)-$(BUILD_TYPE)/lib/$$tbase) ; \
	    install_name_tool -id "@executable_path/$$tbase" $$tgt ; \
        done


ios_universal_prep: ios_universal_builds ios_universal_rollup ios_universal_headercopy


ios_universal_clean:
	for tgt in $(ios_rollup_targets) ; do $(MAKE) TARGET=$$tgt BUILD_TYPE=$(BUILD_TYPE) clean ; done

ios_universal_veryclean:
	for tgt in $(ios_rollup_targets) ; do $(MAKE) TARGET=$$tgt BUILD_TYPE=$(BUILD_TYPE) veryclean ; done
