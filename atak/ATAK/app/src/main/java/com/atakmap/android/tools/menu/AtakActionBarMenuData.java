
package com.atakmap.android.tools.menu;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.Root;

import java.io.StringWriter;

import com.atakmap.coremap.locale.LocaleUtil;

/**
 * Represents a configured action bar menu i.e. landscape or portrait
 * 
 * 
 */

@Root(name = "AtakActionBar")
public class AtakActionBarMenuData extends ActionMenuListData {

    private static final String TAG = "AtakActionBarMenuData";

    public enum Orientation {
        landscape,
        portrait
    }

    @Attribute
    private String label;

    @Attribute
    private String orientation;

    public AtakActionBarMenuData() {
    }

    public AtakActionBarMenuData(AtakActionBarMenuData copy) {
        super(copy);

        if (copy != null && copy.isValid()) {
            label = copy.getLabel();
            orientation = copy.orientation;
        }
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String l) {
        label = l;
    }

    public Orientation getOrientation() {
        return Orientation.valueOf(orientation);
    }

    public void setOrientation(String o) {
        orientation = o;
    }

    @Override
    public boolean isValid() {
        if (FileSystemUtils.isEmpty(actions))
            return false;

        if (FileSystemUtils.isEmpty(label))
            return false;

        return super.isValid();
    }

    public int getItemId() {
        if (!isValid())
            return 0;

        return label.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AtakActionBarMenuData) {
            AtakActionBarMenuData c = (AtakActionBarMenuData) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(AtakActionBarMenuData c) {

        if (!FileSystemUtils.isEquals(label, c.getLabel())) {
            return false;
        }

        if (!FileSystemUtils.isEquals(orientation, c.orientation)) {
            return false;
        }

        return super.equals(c);
    }

    @Override
    public int hashCode() {
        return 31 * ((getActions() == null ? 0 : getActions().hashCode()));
    }

    @Override
    public String toString() {
        return String.format(LocaleUtil.getCurrent(), "%s %s %d", label,
                orientation,
                (getActions() == null ? 0
                        : getActions().size()));
    }

    private String toXml() {
        Log.d(TAG, "Saving AtakActionBarMenuData to xml");
        Serializer serializer = new Persister();
        try {
            StringWriter sw = new StringWriter();
            serializer.write(this, sw);
            return sw.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to serialize action bar: " + toString(), e);
            return "";
        }
    }

    /**
     * Parse the specified action bar string
     * 
     * @param xml
     * @return
     */
    public static AtakActionBarMenuData fromXml(String xml) {
        Log.d(TAG, "Loading AtakActionBarMenuData from xml");

        Serializer serializer = new Persister();
        try {
            AtakActionBarMenuData retval = serializer
                    .read(AtakActionBarMenuData.class, xml);
            for (ActionMenuData amd : retval.getActions()) {
                amd.deferredLoad();
            }
            return retval;

        } catch (Exception e) {
            Log.e(TAG, "Failed to load action bar: " + xml, e);
            return null;
        }
    }
}
