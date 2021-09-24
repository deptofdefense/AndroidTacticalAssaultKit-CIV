commoncommo_src=../commoncommo
commoncommo_local_srcdir=commoncommo

.PHONY: $(OUTDIR)/$(commoncommo_local_srcdir)
$(OUTDIR)/$(commoncommo_local_srcdir):
	$(CP) -r $(commoncommo_src) $(OUTDIR)
	rm -rf $(OUTDIR)/$(commoncommo_local_srcdir)/.git
	cd $(OUTDIR)/$(commoncommo_local_srcdir)

