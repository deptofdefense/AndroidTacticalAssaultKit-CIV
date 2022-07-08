
package com.atakmap.android.http.rest;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Parcelable username, and password
 * 
 * 
 */
public class BasicUserCredentials implements Parcelable {

    private static final String TAG = "BasicUserCredentials";

    private final String mBase64;

    /**
     * Constructs a Basic User Credential from a base64 encoded string
     * @param base64 the base 64 encoded string representing basic user credentials
     */
    public BasicUserCredentials(String base64) {
        mBase64 = base64;
    }

    /**
     * Return the base 64 encoded basic user credentials
     * @return the basic user credentials in base64 encoding.
     */
    public String getBase64() {
        return mBase64;
    }

    /**
     * Simple validity check verifing that the basic encoding is not empty
     * @return true if the base64 encoding is not empty
     */
    public boolean isValid() {
        return !FileSystemUtils.isEmpty(mBase64);
    }

    @NonNull
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
