
package com.atakmap.android.missionpackage.http.datamodel;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * JSON format used by TAK Server
 */
public class MissionPackageQueryResult implements Parcelable {

    private static final String TAG = "MissionPackageQueryResult";

    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            LocaleUtil.getCurrent());

    private String UID;
    private String Name;
    private String Hash;
    private int PrimaryKey;
    private String SubmissionDateTime;
    private long SubmissionDateTimeLong = -1;
    private String SubmissionUser; //e.g. SSL cert identity
    private String CreatorUid; //e.g. ATAK device UID
    private String Keywords;
    private String MIMEType;
    private long Size;

    public MissionPackageQueryResult(String UID, String name, String hash,
            int primaryKey, String submissionDateTime) {
        this.UID = UID;
        Name = name;
        Hash = hash;
        PrimaryKey = primaryKey;
        setSubmissionDateTime(submissionDateTime);
    }

    public MissionPackageQueryResult(MissionPackageQueryResult other) {
        this.UID = other.UID;
        this.Name = other.Name;
        this.Hash = other.Hash;
        this.PrimaryKey = other.PrimaryKey;
        setSubmissionDateTime(other.SubmissionDateTime);
        this.SubmissionUser = other.SubmissionUser;
        this.CreatorUid = other.CreatorUid;
        this.Keywords = other.Keywords;
        this.MIMEType = other.MIMEType;
        this.Size = other.Size;
    }

    public boolean isValid() {
        return !(FileSystemUtils.isEmpty(UID) ||
                FileSystemUtils.isEmpty(Name) ||
                FileSystemUtils.isEmpty(Hash) ||
                FileSystemUtils.isEmpty(SubmissionDateTime) ||
                PrimaryKey < 0);
    }

    @Override
    public String toString() {
        if (!isValid())
            return "";

        return UID + ", " + Name + ", " + Hash + ", " + SubmissionDateTime;
    }

    public String getUID() {
        return UID;
    }

    public void setUID(String UID) {
        this.UID = UID;
    }

    public String getName() {
        return Name;
    }

    public void setName(String name) {
        Name = name;
    }

    public String getHash() {
        return Hash;
    }

    public void setHash(String hash) {
        Hash = hash;
    }

    public String getSubmissionDateTime() {
        return SubmissionDateTime;
    }

    public boolean isNewerThan(MissionPackageQueryResult other) {
        if (SubmissionDateTimeLong != -1 &&
                other.SubmissionDateTimeLong != -1)
            return SubmissionDateTimeLong > other.SubmissionDateTimeLong;
        return SubmissionDateTimeLong != -1 || SubmissionDateTime.compareTo(
                other.SubmissionDateTime) < 0;
    }

    public String getSubmissionUser() {
        return SubmissionUser;
    }

    public String getCreatorUid() {
        return CreatorUid;
    }

    public void setCreatorUid(String creatorUid) {
        CreatorUid = creatorUid;
    }

    public void setSubmissionDateTime(String submissionDateTime) {
        SubmissionDateTime = submissionDateTime;
        long timeLong = -1;
        try {
            synchronized (TIMESTAMP_FORMAT) {
                Date d = TIMESTAMP_FORMAT.parse(submissionDateTime);
                if (d != null)
                    timeLong = d.getTime();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse submission timestamp: "
                    + submissionDateTime, e);
        }
        SubmissionDateTimeLong = timeLong;
    }

    public void setSubmissionUser(String submissionUser) {
        SubmissionUser = submissionUser;
    }

    public String getKeywords() {
        return Keywords;
    }

    public void setKeywords(String keywords) {
        Keywords = keywords;
    }

    public String getMIMEType() {
        return MIMEType;
    }

    public void setMIMEType(String MIMEType) {
        this.MIMEType = MIMEType;
    }

    public long getSize() {
        return Size;
    }

    public void setSize(long size) {
        Size = size;
    }

    private static MissionPackageQueryResult fromJSON(JSONObject json)
            throws JSONException {
        if (json == null)
            throw new JSONException("Empty JSON");

        //get required params
        MissionPackageQueryResult r = new MissionPackageQueryResult(
                json.getString("UID"),
                json.getString("Name"),
                json.getString("Hash"),
                json.getInt("PrimaryKey"),
                json.getString("SubmissionDateTime"));

        //now get SubmissionUser params
        if (json.has("SubmissionUser"))
            r.setSubmissionUser(json.getString("SubmissionUser"));
        if (json.has("CreatorUid"))
            r.setCreatorUid(json.getString("CreatorUid"));
        if (json.has("Keywords"))
            r.setKeywords(json.getString("Keywords"));
        if (json.has("MIMEType"))
            r.setMIMEType(json.getString("MIMEType"));
        if (json.has("Size"))
            r.setSize(json.getLong("Size"));

        //Log.d(TAG, "Convert JSON: " + r.toString());
        return r;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            dest.writeString(UID);
            dest.writeString(Name);
            dest.writeString(Hash);
            dest.writeString(SubmissionDateTime);
            dest.writeString(SubmissionUser);
            dest.writeString(CreatorUid);
            dest.writeString(Keywords);
            dest.writeString(MIMEType);
            dest.writeInt(PrimaryKey);
            dest.writeLong(Size);
        }
    }

    public static final Parcelable.Creator<MissionPackageQueryResult> CREATOR = new Parcelable.Creator<MissionPackageQueryResult>() {
        @Override
        public MissionPackageQueryResult createFromParcel(Parcel in) {
            return new MissionPackageQueryResult(in);
        }

        @Override
        public MissionPackageQueryResult[] newArray(int size) {
            return new MissionPackageQueryResult[size];
        }
    };

    protected MissionPackageQueryResult(Parcel in) {
        UID = in.readString();
        Name = in.readString();
        Hash = in.readString();
        setSubmissionDateTime(in.readString());
        SubmissionUser = in.readString();
        CreatorUid = in.readString();
        Keywords = in.readString();
        MIMEType = in.readString();
        PrimaryKey = in.readInt();
        Size = in.readLong();
    }

    /**
     * Convert JSON to list of results
     * @param json
     * @return
     * @throws JSONException
     */
    public static List<MissionPackageQueryResult> fromResultJSON(
            JSONObject json)
            throws JSONException {
        JSONArray array = json.getJSONArray("results");
        if (array == null)
            throw new JSONException("No JSON results");

        List<MissionPackageQueryResult> results = new ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            MissionPackageQueryResult r = MissionPackageQueryResult.fromJSON(
                    array.getJSONObject(i));
            if (r == null || !r.isValid())
                throw new JSONException("Invalid JSON child");
            results.add(r);
        }

        return results;
    }
}
