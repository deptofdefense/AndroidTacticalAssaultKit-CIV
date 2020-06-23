
package com.atakmap.android.missionpackage.file;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.filesystem.FileSystemUtils;

import org.json.JSONException;
import org.json.JSONObject;
import org.simpleframework.xml.Attribute;

/**
 * Simple Parcelable Name/Value pair
 * 
 * 
 */
public class NameValuePair implements Parcelable {

    private static final String TAG = "NameValuePair";

    @Attribute(name = "name", required = true)
    private String mName;

    @Attribute(name = "value", required = true)
    private String mValue;

    public NameValuePair() {
    }

    public NameValuePair(String name, String value) {
        mName = name;
        mValue = value;
    }

    public NameValuePair(NameValuePair pair) {
        mName = pair.getName();
        mValue = pair.getValue();
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public String getValue() {
        return mValue;
    }

    public void setValue(String value) {
        this.mValue = value;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(mName)
                && !FileSystemUtils.isEmpty(mValue);
    }

    public static final Creator<NameValuePair> CREATOR = new Creator<NameValuePair>() {
        @Override
        public NameValuePair createFromParcel(Parcel in) {
            return new NameValuePair(in);
        }

        @Override
        public NameValuePair[] newArray(int size) {
            return new NameValuePair[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeString(mValue);
    }

    private NameValuePair(Parcel in) {
        readFromParcel(in);
    }

    private void readFromParcel(Parcel in) {
        mName = in.readString();
        mValue = in.readString();
    }

    @Override
    public int hashCode() {
        int result = (mName == null) ? 0 : mName.hashCode();
        result = 31 * result + ((mValue == null) ? 0 : mValue.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof NameValuePair) {
            NameValuePair c = (NameValuePair) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(NameValuePair rhsc) {
        // technically they could be invalid and equal, but we interested in valid ones
        if (!isValid() || !rhsc.isValid())
            return false;

        if (!FileSystemUtils.isEquals(mName, rhsc.mName))
            return false;

        if (!FileSystemUtils.isEquals(mValue, rhsc.mValue))
            return false;

        return true;
    }

    @Override
    public String toString() {
        return mName + "=" + mValue;
    }

    public static NameValuePair fromJSON(JSONObject obj) throws JSONException {

        NameValuePair r = new NameValuePair();
        if (obj.has("Name"))
            r.mName = obj.getString("Name");
        if (obj.has("Value"))
            r.mValue = obj.getString("Value");

        return r;
    }

    public JSONObject toJSON() throws JSONException {
        if (!isValid())
            throw new JSONException("Invalid NameValuePair");

        JSONObject json = new JSONObject();
        if (!FileSystemUtils.isEmpty(mName))
            json.put("Name", mName);
        if (!FileSystemUtils.isEmpty(mValue))
            json.put("Value", mValue);

        return json;
    }
}
