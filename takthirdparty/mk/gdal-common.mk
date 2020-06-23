.PHONY: gdal gdal_clean

ifeq ($(and $(gdal_out_lib),$(gdal_out_javalibs)),)
    $(error Required var not set)
endif

gdal_src=../gdal
gdal_local_srcdir=gdal
gdal_srctouchfile=$(OUTDIR)/$(gdal_local_srcdir)/.unpacked


# Check that the outer source location is clean
ifneq ($(or $(wildcard $(gdal_src)/config.log),$(wildcard $(gdal_src)/gcore/*.$(OBJ_SUFFIX))),)
    $(error gdal source dir $(gdal_src) not clean - cd there and clean it out)
endif

.PHONY: $(OUTDIR)/$(gdal_local_srcdir)
$(OUTDIR)/$(gdal_local_srcdir):
	$(CP) -r $(gdal_src) $(OUTDIR)
	rm -rf $(OUTDIR)/$(gdal_local_srcdir)/.git
	cd $(OUTDIR)/$(gdal_local_srcdir) && chmod 755 configure

gdal: $(gdal_out_lib) $(if $(gdal_NO_JAVA),,$(gdal_out_javalibs))

gdal_clean:
	rm -rf $(OUTDIR)/$(gdal_local_srcdir)
	rm -f $(gdal_out_lib)
