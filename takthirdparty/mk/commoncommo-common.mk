commoncommo_src=../commoncommo
commoncommo_local_srcdir=commoncommo

.PHONY: $(OUTDIR)/$(commoncommo_local_srcdir)
$(OUTDIR)/$(commoncommo_local_srcdir):
	rm -rf $(OUTDIR)/$(commoncommo_local_srcdir)/.git
	$(CP) -r $(commoncommo_src) $(OUTDIR)
	cd $(OUTDIR)/$(commoncommo_local_srcdir)

