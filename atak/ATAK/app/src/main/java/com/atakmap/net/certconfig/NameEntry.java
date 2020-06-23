
package com.atakmap.net.certconfig;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Namespace;

@Element(name = "nameEntry")
@Namespace(reference = "http://bbn.com/marti/xml/config")
public class NameEntry {

    @Attribute(name = "value", required = false)
    private String value;
    @Attribute(name = "name", required = false)
    private String name;

    public NameEntry() {
    }

    /**
     * Returns the value associated with the NameValue object.
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the value associated with the NameValue object.
     * @param value the value as a string.
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Returns the name associated with the NameValue object.
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name associated with the NameValue object.
     * @param name the value as a string.
     */
    public void setName(String name) {
        this.name = name;
    }

}
