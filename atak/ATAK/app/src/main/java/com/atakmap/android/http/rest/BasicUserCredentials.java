
package com.atakmap.android.http.rest;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Parcelable username, and password
 * 
 * 
 */
public class BasicUserCredentials implements Parcelable {

    private static final String TAG = "BasicUserCredentials";

    private final String mBase64;

    public BasicUserCredentials(String base64) {
        mBase64 = base64;
    }

    public String getBase64() {
        return mBase64;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(mBase64);
    }

    public String toString() {
        return String.format("%s", mBase64);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mBase64);
    }

    public static final Parcelable.Creator<BasicUserCredentials> CREATOR = new Parcelable.Creator<BasicUserCredentials>() {
        @Override
        public BasicUserCredentials createFromParcel(Parcel in) {
            return new BasicUserCredentials(in);
        }

        @Override
        public BasicUserCredentials[] newArray(int size) {
            return new BasicUserCredentials[size];
        }
    };

    private BasicUserCredentials(Parcel in) {
        mBase64 = in.readString();
    }
}
