
package com.atakmap.net.certconfig;

import org.simpleframework.xml.*;

import java.util.List;

@Root(name = "certificateConfig")
@Namespace(reference = "http://bbn.com/marti/xml/config")
public class CertificateConfig {

    @ElementList(name = "nameEntries", required = false)
    private List<NameEntry> nameEntries;

    @Attribute(name = "validityDays", required = false)
    private Integer validityDays;

    public CertificateConfig() {
    }

    public List<NameEntry> getNameEntries() {
        return nameEntries;
    }

    public void setNameEntries(List<NameEntry> nameEntries) {
        this.nameEntries = nameEntries;
    }

    public Integer getValidityDays() {
        return validityDays;
    }

    public void setValidityDays(Integer validityDays) {
        this.validityDays = validityDays;
    }
}
