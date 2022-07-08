
package com.atakmap.android.http.rest;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Parcelable username, and password
 */
public class UserCredentials implements Parcelable {

    private static final String TAG = "UserCredentials";

    private final String mUser;
    private final String mPasswd;

    public UserCredentials(final String user, final String passwd) {
        mUser = user;
        mPasswd = passwd;
    }

    /**
     * Returns the username associated with the user credential object
     * @return the username
     */
    public String getUser() {
        return mUser;
    }

    /**
     * Returns the pass associated with the user credential object
     * @return the pass
     */
    public String getPasswd() {
        return mPasswd;
    }

    /**
     * Determines if the user credentials are considered valid.
     * @return true if the username and pass are not empty.
     */
    public boolean isValid() {
        return !FileSystemUtils.isEmpty(mUser)
                && !FileSystemUtils.isEmpty(mPasswd);
    }

    @NonNull
    public String toString() {
        return String.format("%s", mUser);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mUser);
        dest.writeString(mPasswd);
    }

    public static final Parcelable.Creator<UserCredentials> CREATOR = new Parcelable.Creator<UserCredentials>() {
        @Override
        public UserCredentials createFromParcel(Parcel in) {
            return new UserCredentials(in);
        }

        @Override
        public UserCredentials[] newArray(int size) {
            return new UserCredentials[size];
        }
    };

    private UserCredentials(Parcel in) {
        mUser = in.readString();
        mPasswd = in.readString();
    }
}
