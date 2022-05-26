
package com.atakmap.android.tools.menu;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.filesystem.FileSystemUtils;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Text;

import com.atakmap.coremap.locale.LocaleUtil;

/**
 * 
 */
public class ActionBroadcastExtraStringData implements Parcelable {

    private static final String TAG = "ActionBroadcastExtraStringData";

    @Attribute
    private String key;

    @Text
    private String value;

    public ActionBroadcastExtraStringData() {
    }

    public ActionBroadcastExtraStringData(String k, String v) {
        this.key = k;
        this.value = v;
    }

    public ActionBroadcastExtraStringData(ActionBroadcastExtraStringData copy) {
        if (copy != null && copy.isValid()) {
            key = copy.key;
            value = copy.value;
        }
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(key) &&
                !FileSystemUtils.isEmpty(value);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ActionBroadcastExtraStringData) {
            ActionBroadcastExtraStringData c = (ActionBroadcastExtraStringData) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(ActionBroadcastExtraStringData c) {
        if (!FileSystemUtils.isEquals(getKey(), c.getKey()))
            return false;

        return FileSystemUtils.isEquals(getValue(), c.getValue());
    }

    @Override
    public int hashCode() {
        return 31 * ((getKey() == null ? 0 : getKey().hashCode())
                + (getValue() == null ? 0
                        : getValue().hashCode()));
    }

    @Override
    public String toString() {
        return String.format(LocaleUtil.getCurrent(), "%s %s", getKey(),
                getValue());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        if (isValid()) {
            parcel.writeString(key);
            parcel.writeString(value);
        }
    }

    private ActionBroadcastExtraStringData(Parcel in) {
        readFromParcel(in);
    }

    private void readFromParcel(Parcel in) {
        key = in.readString();
        value = in.readString();
    }

    public static final Parcelable.Creator<ActionBroadcastExtraStringData> CREATOR = new Parcelable.Creator<ActionBroadcastExtraStringData>() {
        @Override
        public ActionBroadcastExtraStringData createFromParcel(Parcel in) {
            return new ActionBroadcastExtraStringData(in);
        }

        @Override
        public ActionBroadcastExtraStringData[] newArray(int size) {
            return new ActionBroadcastExtraStringData[size];
        }
    };
}
