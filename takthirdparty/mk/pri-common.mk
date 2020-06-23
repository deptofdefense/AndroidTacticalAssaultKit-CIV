.PHONY: pri_clean

ifeq ($(and $(pri_out_lib)),)
    $(error Required var not set)
endif


pri_src=../pri
pri_local_srcdir=$(OUTDIR)/pri
pri_incdir=$(OUTDIR)/include/pri

# Check that the outer source location is clean
ifneq ($(or $(wildcard $(pri_src)/prijni/build/classes/main/com/iai/pri/*.class),$(wildcard $(pri_src)/pricpp/src/pri/obj/local/*/*.so),$(wildcard $(pri_src)/src/pricpp/Release/*.obj),$(wildcard $(pri_src)/src/pricpp/Debug/*.obj)),)
    $(error PRI source dir $(pri_src) not clean - go there and clean it out)
endif


.PHONY: $(pri_local_srcdir)
$(pri_local_srcdir):
	$(CP) -r $(pri_src) $(OUTDIR)
	chmod +x $(pri_local_srcdir)/gradlew*
	rm -rf $(pri_local_srcdir)/.git


$(pri_incdir):
	mkdir -p $(pri_incdir)

.PHONY: pri_install_headers
pri_install_headers: $(pri_incdir)
	cp -r $(pri_local_srcdir)/pricpp/src/pri/include/* $(pri_incdir)/
	mkdir -p $(pri_incdir)/newmat
	cp $(pri_local_srcdir)/pricpp/src/pri/source/newmat/*.h $(pri_incdir)/newmat/

pri_clean:
	rm -f $(pri_out_lib)
	rm -rf $(pri_local_srcdir)
	rm -rf $(pri_incdir)
