
package com.atakmap.android.missionpackage.file;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

import java.util.ArrayList;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;

/**
 * An item (content) in a Mission Package, defined by a "zipEntry" path. May also contain optional
 * name/value parameters
 * 
 * 
 */
@Root
public class MissionPackageContent extends MissionPackageConfiguration {

    private static final String TAG = "MissionPackageContent";

    public final static String PARAMETER_NAME = "name";
    public final static String PARAMETER_LOCALPATH = "localpath";
    public final static String PARAMETER_LOCALISCOT = "isCoT";
    public final static String PARAMETER_CONTENT_TYPE = "contentType";
    public final static String PARAMETER_VISIBLE = "visible";

    /**
     * Allows a content parameter to reference another content/entry
     */
    public final static String PARAMETER_REFERENCECONTENT = "refContent";

    /**
     * Unique ID within the manifest
     * e.g. Zip entry in the mission package zip file
     * Labelled "zipEntry" in the Mission Package XML v2
     * e.g. also just the unique ID for other derivative manifests e.g. MissionManifest
     */
    @Attribute(name = "zipEntry", required = true)
    private String _manifestUid;

    @Attribute(name = "ignore", required = false)
    private boolean _ignore = false;

    // Compressed and uncompressed size of this content in the MP file
    private transient long _size, _compressedSize;

    public MissionPackageContent() {
        super();
        _manifestUid = null;
    }

    /**
     * ctor. e.g. Zip Entry set to <random UUID>/name for Mission Packages
     * 
     * @param manifestUid
     */
    public MissionPackageContent(String manifestUid) {
        super();
        _manifestUid = manifestUid;
    }

    public MissionPackageContent(MissionPackageContent content) {
        super(content);
        _manifestUid = content._manifestUid;
        _ignore = content.isIgnore();
    }

    @Override
    public boolean isValid() {
        // Note, _parameters are optional
        return !FileSystemUtils.isEmpty(_manifestUid);
    }

    public String getManifestUid() {
        return _manifestUid;
    }

    public void setManifestUid(String manifestUid) {
        this._manifestUid = manifestUid;
    }

    public void setIsCoT(boolean bIsCoT) {
        setParameter(new NameValuePair(PARAMETER_LOCALISCOT,
                Boolean.TRUE.toString()));
    }

    public boolean isCoT() {
        if (!isValid())
            return false;

        // set if local helper flag is set
        NameValuePair p = getParameter(PARAMETER_LOCALISCOT);
        if (p != null && p.isValid() && Boolean.parseBoolean(p.getValue()))
            return true;

        // Note .cot exists in zip and is validating as CoT/XML during extraction
        // not on filesystem during compression and UI/user manipulation

        // check filename
        return _manifestUid.toLowerCase(LocaleUtil.getCurrent()).endsWith(
                ".cot");
    }

    public boolean isIgnore() {
        return _ignore;
    }

    public void setIgnore(boolean b) {
        this._ignore = b;
    }

    /**
     * Set the uncompressed and compressed size of this content's data
     * @param uncomp Uncompressed size in bytes
     * @param comp Compressed size in bytes
     */
    void setSizes(long uncomp, long comp) {
        this._compressedSize = comp;
        this._size = uncomp;
    }

    /**
     * Get the uncompressed size of this content's data
     * @return Size in bytes
     */
    public long getSize() {
        return _size;
    }

    /**
     * Get the compressed size of this content's data
     * @return Size in bytes
     */
    public long getCompressedSize() {
        return _compressedSize;
    }

    @Override
    public String toString() {
        return String.format("%s", _manifestUid);
    }

    @Override
    public int hashCode() {
        int result = (_manifestUid == null) ? 0 : _manifestUid.hashCode();
        result = 31 * result + (_ignore ? 1 : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MissionPackageContent) {
            MissionPackageContent c = (MissionPackageContent) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(MissionPackageContent c) {

        if (!FileSystemUtils.isEquals(_manifestUid, c._manifestUid))
            return false;

        if (_ignore != c._ignore)
            return false;

        return super.equals(c);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        if (isValid()) {
            super.writeToParcel(parcel, flags);
            parcel.writeString(_manifestUid);
            parcel.writeInt(_ignore ? 1 : 0);
        } else
            Log.w(TAG, "cannot parcel invalid: " + this);
    }

    public static final Parcelable.Creator<MissionPackageContent> CREATOR = new Parcelable.Creator<MissionPackageContent>() {
        @Override
        public MissionPackageContent createFromParcel(Parcel in) {

            return new MissionPackageContent(in);
        }

        @Override
        public MissionPackageContent[] newArray(int size) {
            return new MissionPackageContent[size];
        }
    };

    protected MissionPackageContent(Parcel in) {
        super(in);
        readFromParcel(in);
    }

    private void readFromParcel(Parcel in) {
        _manifestUid = in.readString();
        _ignore = in.readInt() == 0 ? false : true;
    }

    public static MissionPackageContent fromJSON(JSONObject obj)
            throws JSONException {
        MissionPackageContent r = new MissionPackageContent();
        if (obj.has("manifestUid"))
            r._manifestUid = obj.getString("manifestUid");
        if (obj.has("ignore"))
            r._ignore = obj.getBoolean("ignore");

        if (obj.has("parameters")) {
            JSONArray array = obj.getJSONArray("parameters");

            List<NameValuePair> jcontents = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                NameValuePair c = NameValuePair
                        .fromJSON(array.getJSONObject(i));
                if (c == null || !c.isValid())
                    throw new JSONException("Invalid JSON child");
                jcontents.add(c);
            }

            r._parameters = jcontents;
        }

        return r;
    }

    public JSONObject toJSON() throws JSONException {
        if (!isValid())
            throw new JSONException("Invalid MissionPackageContent");

        JSONObject json = new JSONObject();
        if (!FileSystemUtils.isEmpty(_manifestUid))
            json.put("manifestUid", _manifestUid);
        json.put("ignore", _ignore);

        JSONArray jArray = new JSONArray();
        json.put("parameters", jArray);
        if (_parameters.size() > 0) {
            for (NameValuePair c : _parameters) {
                jArray.put(c.toJSON());
            }
        }

        return json;
    }
}
