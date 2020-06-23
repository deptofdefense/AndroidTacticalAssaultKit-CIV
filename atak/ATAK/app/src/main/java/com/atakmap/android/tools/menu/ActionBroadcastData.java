
package com.atakmap.android.tools.menu;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;

import java.util.ArrayList;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;

/**
 * 
 */
public class ActionBroadcastData implements Parcelable {

    private static final String TAG = "ActionBroadcastData";

    @Element
    private String action;

    @ElementList(required = false, entry = "string")
    private ArrayList<ActionBroadcastExtraStringData> extras;

    public ActionBroadcastData() {
    }

    public ActionBroadcastData(String a,
            ArrayList<ActionBroadcastExtraStringData> e) {
        action = a;
        extras = e;
    }

    public ActionBroadcastData(ActionBroadcastData copy) {
        if (copy != null && copy.isValid()) {
            action = copy.action;
            if (copy.hasExtras()) {
                extras = new ArrayList<>();
                for (ActionBroadcastExtraStringData extra : copy.getExtras()) {
                    extras.add(new ActionBroadcastExtraStringData(extra));
                }
            }
        }
    }

    public List<ActionBroadcastExtraStringData> getExtras() {
        if (extras == null)
            extras = new ArrayList<>();

        return extras;
    }

    public String getExtra(String key) {
        for (ActionBroadcastExtraStringData extra : getExtras()) {
            if (FileSystemUtils.isEquals(extra.getKey(), key))
                return extra.getValue();
        }

        return null;
    }

    public String getAction() {
        return action;
    }

    public boolean hasExtras() {
        return extras != null && extras.size() > 0;
    }

    public boolean isValid() {
        if (FileSystemUtils.isEmpty(action))
            return false;

        if (extras != null) {
            for (ActionBroadcastExtraStringData extra : extras)
                if (extra == null || !extra.isValid())
                    return false;
        }

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ActionBroadcastData) {
            ActionBroadcastData c = (ActionBroadcastData) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(ActionBroadcastData c) {
        return !(!isValid() || !c.isValid())
                && FileSystemUtils.isEquals(getAction(), c.getAction())
                && FileSystemUtils.isEquals(getExtras(), c.getExtras());

    }

    @Override
    public int hashCode() {
        return 31 * ((getAction() == null ? 0 : getAction().hashCode()));
    }

    @Override
    public String toString() {
        return String.format(LocaleUtil.getCurrent(), "%s %s", getAction(),
                getExtras()
                        .size());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        if (isValid()) {
            parcel.writeString(action);
            int extraCount = hasExtras() ? extras.size() : 0;
            parcel.writeInt(extraCount);
            for (int i = 0; i < extraCount; i++) {
                parcel.writeParcelable(extras.get(i), flags);
            }
        }
    }

    private ActionBroadcastData(Parcel in) {
        readFromParcel(in);
    }

    private void readFromParcel(Parcel in) {
        action = in.readString();
        int extraCount = in.readInt();
        if (extraCount > 0) {
            for (int i = 0; i < extraCount; i++) {
                ActionBroadcastExtraStringData extra = in
                        .readParcelable(ActionBroadcastExtraStringData.class
                                .getClassLoader());
                if (extra != null && extra.isValid()) {
                    getExtras().add(extra);
                } else
                    Log.w(TAG, "Ignoring invalid extra parcel");
            }
        }
    }

    public static final Parcelable.Creator<ActionBroadcastData> CREATOR = new Parcelable.Creator<ActionBroadcastData>() {
        @Override
        public ActionBroadcastData createFromParcel(Parcel in) {
            return new ActionBroadcastData(in);
        }

        @Override
        public ActionBroadcastData[] newArray(int size) {
            return new ActionBroadcastData[size];
        }
    };

    public static void broadcast(ActionBroadcastData data) {
        if (data == null || !data.isValid()) {
            Log.w(TAG, "Cannot broadcast");
            return;
        }

        Intent intent = new Intent();
        intent.setAction(data.getAction());
        if (data.hasExtras()) {
            for (ActionBroadcastExtraStringData extra : data.getExtras()) {
                intent.putExtra(extra.getKey(), extra.getValue());
            }
        }

        Log.d(TAG, "Sending intent for: " + data.toString());
        AtakBroadcast.getInstance().sendBroadcast(intent);
    }
}
