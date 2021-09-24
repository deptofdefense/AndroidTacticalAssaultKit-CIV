
package com.atakmap.coremap.cot.event;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * A Cursor on Target root detail tag or sub tag
 */
public class CotDetail implements Parcelable {

    private final Map<String, String> _attrs = new HashMap<>();
    private final List<CotDetail> _children = new ArrayList<>();
    private final Map<String, CotDetail> firstNode = new HashMap<>();

    private String _elemName;
    private String _innerText;

    /**
     * Create a default detail tag
     */
    public CotDetail() {
        this("detail");
    }

    /**
     * Copy Constructor.
     */
    public CotDetail(final CotDetail d) {
        _elemName = d.getElementName();
        _innerText = d.getInnerText();
        d.copyAttributesInternal(_attrs);
        d.copyChildrenInternal(_children);
    }

    /**
     * Get the tag element name
     * 
     * @return the element name
     */
    public String getElementName() {
        return _elemName;
    }

    /**
     * Get an attribute by name
     * 
     * @param name attribute name
     * @return the attribute
     */
    public String getAttribute(final String name) {
        return _attrs.get(name);
    }

    /**
     * Internal copy _attrs -- only used in the copy contructor
     */
    private void copyAttributesInternal(Map<String, String> destination) {
        destination.putAll(_attrs);
    }

    /**
     * Internal copy _children -- only used in the copy contructor
     */
    private void copyChildrenInternal(List<CotDetail> destination) {
        synchronized (_children) {
            destination.addAll(_children);
        }
    }

    /**
     * Get the inner text of the tag if any. This does not return XML representation of sub tags.
     * Inner-text and sub-tags are mutually exclusive.
     * 
     * @return the inner text of the tag
     */
    public String getInnerText() {
        return _innerText;
    }

    /**
     * Create a detail tag with a element name
     * 
     * @param elementName the element name
     */
    public CotDetail(String elementName) {
        if (!_validateName(elementName)) {
            throw new IllegalArgumentException("invalid element name");
        }
        _elemName = elementName;
    }

    /**
     * Get an array of the immutable attributes of the detail tag. The order is the same as the add
     * order.
     * 
     * @return get a copy of the current attributes.
     */
    public CotAttribute[] getAttributes() {
        CotAttribute[] attrs = new CotAttribute[_attrs.size()];
        Set<Map.Entry<String, String>> entries = _attrs.entrySet();
        Iterator<Map.Entry<String, String>> it = entries.iterator();
        int index = 0;
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            attrs[index++] = new CotAttribute(entry.getKey(), entry.getValue());
        }
        return attrs;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        buildXml(sb);
        return sb.toString();
    }

    /**
     * The number of sub tags in this detail tag
     * 
     * @return the number of sub tags
     */
    public int childCount() {
        synchronized (_children) {
            return _children.size();
        }
    }

    /**
     * Get a child detail tag at a given index or null if the index is invalid at the time of the
     * call.
     * 
     * @param index
     * @return the child  at the provided index
     */
    public CotDetail getChild(final int index) {
        synchronized (_children) {
            try {
                return _children.get(index);
            } catch (IndexOutOfBoundsException oobe) {
                return null;
            }
        }
    }

    /**
     * Get the first child detail with the given name
     * This is a shortcut method for {@link #getFirstChildByName(int, String)}
     * which is more verbose
     *
     * @param name Detail name
     * @return CoT detail or null if not found
     */
    public CotDetail getChild(String name) {
        return getFirstChildByName(0, name);
    }

    /**
     * Get a list of this CoT's children
     * This is much faster than calling {@link #getChild(int)} in a loop
     *
     * @return List of child nodes
     */
    public List<CotDetail> getChildren() {
        synchronized (_children) {
            return new ArrayList<>(_children);
        }
    }

    /**
     * Get all children details with a matching element name
     * @param name Element name
     * @return List of child nodes
     */
    public List<CotDetail> getChildrenByName(String name) {
        List<CotDetail> ret = new ArrayList<>();
        synchronized (_children) {
            for (CotDetail d : _children) {
                if (d != null && name.equals(d.getElementName()))
                    ret.add(d);
            }
        }
        return ret;
    }

    /**
     * Set an attribute. There is no need to do any special escaping of the value. When an XML
     * string is built, escaping of illegal characters is done automatically.
     * 
     * @param name the attribute name
     * @param value the value
     */
    public void setAttribute(String name, String value) {
        //XXX-- contract violation (does not check for name being legal XML
        if (value != null)
            _attrs.put(name, value);
    }

    /**
     * Remove an attribute.
     * 
     * @param name the attribute name (can be anything)
     * @return get the value removed if any
     */
    public String removeAttribute(final String name) {
        return _attrs.remove(name);
    }

    /**
     * Remove all attributes
     */
    public void clearAttributes() {
        _attrs.clear();
    }

    /**
     * Set the element name of the tag
     * 
     * @throws IllegalArgumentException if the attribute name is not a legal XML name
     * @param name
     */
    public void setElementName(String name) {
        if (!_validateName(name)) {
            throw new IllegalArgumentException("attribute name is invalid ('"
                    + name + "')");
        }
        _elemName = name;
    }

    /**
     * Add a sub detail tag. Since sub tags and inner text are mutually exclusive, a successful
     * calling will clear any inner text.
     * 
     * @throws IllegalArgumentException if detail is null
     * @param detail
     */
    public void addChild(CotDetail detail) {

        synchronized (_children) {
            if (detail == null) {
                throw new IllegalArgumentException("detail is null");
            }
            _innerText = null;
            _children.add(detail);
        }
    }

    /**
     * Set a sub detail tag
     * 
     * @throws IllegalArgumentException if detail is null
     * @throws ArrayIndexOutOfBoundsException if index is out of bounds
     * @param index
     * @param detail
     */
    public void setChild(final int index, final CotDetail detail) {
        synchronized (_children) {
            firstNode.clear(); // children are changing, clear the cache.
            if (detail == null) {
                throw new IllegalArgumentException("detail cannot be null");
            }
            _children.set(index, detail);
        }
    }

    /**
     * Remove a child.
     */
    public void removeChild(final CotDetail detail) {
        synchronized (_children) {
            firstNode.clear(); // children are changing, clear the cache.
            _children.remove(detail);
        }
    }

    /**
     * Set the inner text of this tag. Since sub tags and inner text are mutually exclusive, calling
     * this removes any sub tags
     * 
     * @param text
     */
    public void setInnerText(final String text) {
        synchronized (_children) {
            firstNode.clear(); // children are changing, clear the cache.
            _children.clear();
            _innerText = text;
        }
    }

    /**
     * Get the first sub tag of a certain name starting at some index or null if the childElement is
     * not found or the startIndex is out of bounds.
     * 
     * @throws
     * @param startIndex the start index
     * @param childElementName the element name
     * @return the first child with the child element name
     */
    public CotDetail getFirstChildByName(final int startIndex,
            final String childElementName) {
        CotDetail cd;

        synchronized (_children) {
            if (startIndex == 0) {
                // check the cache for the first node
                cd = firstNode.get(childElementName);
                if (cd != null) {
                    return cd;
                }
            }
            for (int i = startIndex; i < _children.size(); ++i) {
                cd = getChild(i); // includes protection for an index out of bound exception
                if (cd != null
                        && cd.getElementName().equals(childElementName)) {
                    if (startIndex == 0) {
                        // populate the cache for the first found node.
                        firstNode.put(childElementName, cd);
                    }
                    return cd;
                }
            }
        }
        return null;
    }

    public void buildXml(final StringBuffer b) {
        try {
            this.buildXmlImpl(b);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public void buildXml(final StringBuilder b) {
        try {
            this.buildXmlImpl(b);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Build the XML representation of the detail tag
     * 
     * @param b the appendable to use when generating the xml.
     */
    public void buildXml(final Appendable b) throws IOException {
        this.buildXmlImpl(b);
    }

    private void buildXmlImpl(final Appendable b) throws IOException {
        b.append("<");
        b.append(_elemName);
        Set<Map.Entry<String, String>> entries = _attrs.entrySet();
        for (Map.Entry<String, String> entry : entries) {
            b.append(" ");
            b.append(entry.getKey());
            b.append("='");
            String valueText = entry.getValue();
            if (valueText != null) {
                b.append(CotEvent.escapeXmlText(entry.getValue()));
            }
            b.append("'");
        }
        if (_innerText != null) {
            b.append(">");
            b.append(CotEvent.escapeXmlText(_innerText));
            b.append("</");
            b.append(_elemName);
            b.append(">");
        } else {
            synchronized (_children) {
                if (_children.size() > 0) {
                    b.append(">");
                    for (int i = 0; i < _children.size(); ++i) {
                        CotDetail child = getChild(i);
                        if (child != null)
                            child.buildXml(b);
                    }
                    b.append("</");
                    b.append(_elemName);
                    b.append(">");
                } else {
                    b.append("/>");
                }
            }
        }
    }

    /**
     * Get the number of attributes in the tag
     * 
     * @return the number of attributes in a tag
     */
    public int getAttributeCount() {
        return _attrs.size();
    }

    /**
     * Create a detail tag from its Parcel representation
     * 
     * @param source the parcel to extract out the CoT detail.
     */
    public CotDetail(final Parcel source) {
        _elemName = source.readString();

        int childCount = source.readInt();
        if (childCount > 0) {
            do {
                _children.add(CREATOR.createFromParcel(source));
            } while (--childCount > 0);
        } else {
            _innerText = source.readString();
        }

        int attrCount = source.readInt();
        while (attrCount-- > 0) {
            String name = source.readString();
            String value = source.readString();
            _attrs.put(name, value);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        // name and inner text
        dest.writeString(_elemName);
        synchronized (_children) {
            dest.writeInt(_children.size());
            if (_children.size() > 0) {
                for (CotDetail child : _children) {
                    child.writeToParcel(dest, flags);
                }
            } else {
                dest.writeString(_innerText);
            }
        }

        // attributes
        Set<Map.Entry<String, String>> entries = _attrs.entrySet();
        dest.writeInt(entries.size());
        for (Map.Entry<String, String> entry : entries) {
            dest.writeString(entry.getKey());
            dest.writeString(entry.getValue());
        }
    }

    public final static Parcelable.Creator<CotDetail> CREATOR = new Parcelable.Creator<CotDetail>() {
        @Override
        public CotDetail createFromParcel(Parcel source) {
            return new CotDetail(source);
        }

        @Override
        public CotDetail[] newArray(int size) {
            return new CotDetail[size];
        }
    };

    /**
     * This should be used to validate if a detail tag follows the rules, if the tag is in the standard
     * it should be defined by a CoT Schema, if not it should start out with a __.   Should print a
     * warning if it does not fall into these categories.
     * @param name
     * @return true if the name is valid.
     */
    private static boolean _validateName(final String name) {
        return true;
    }
}
