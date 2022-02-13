
package com.atakmap.android.tools.menu;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.log.Log;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;

import com.atakmap.coremap.locale.LocaleUtil;

/**
 * 
 */
public class ActionClickData implements Parcelable {

    private static final String TAG = "ActionClickData";
    public static final String CLICK = "click";
    public static final String LONG_CLICK = "longClick";

    @Attribute(required = true, name = "actionType")
    private String actionType = "";

    /**
     * Whether to dismiss the tool menu when clicked
     */
    @Attribute(required = false)
    private boolean dismissMenu = true;

    /**
     * Action to take when the menu is launched
     */
    @Element
    private ActionBroadcastData broadcast;

    public ActionClickData() {
    }

    public ActionClickData(ActionBroadcastData broadcast, String actionType) {
        this.actionType = actionType;
        this.broadcast = broadcast;
    }

    public ActionClickData(ActionClickData copy) {
        if (copy != null && copy.isValid()) {
            broadcast = new ActionBroadcastData(copy.getBroadcast());
        } else {
            Log.w(TAG,
                    "Invalid copy: "
                            + (copy == null ? "null" : copy.toString()));
        }
    }

    /**
     * Get the type of click action
     * @return Click action
     */
    public String getActionType() {
        return actionType;
    }

    /**
     * Get whether or not this action should dismiss the menu it's part of
     * if applicable
     * @return True to dismiss menu
     */
    public boolean shouldDismissMenu() {
        return dismissMenu;
    }

    public ActionBroadcastData getBroadcast() {
        return broadcast;
    }

    public boolean hasBroadcast() {
        return broadcast != null && broadcast.isValid();
    }

    public boolean isValid() {
        return !actionType.isEmpty() && hasBroadcast();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ActionClickData) {
            ActionClickData c = (ActionClickData) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(ActionClickData c) {
        if (!isValid() || !c.isValid()) {
            return false;
        }

        if (!getActionType().equals(c.getActionType())) {
            return false;
        }

        if (!getBroadcast().equals(c.getBroadcast())) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return getId();
    }

    @Override
    public String toString() {
        return String.format(LocaleUtil.getCurrent(), "%s %s",
                actionType,
                (hasBroadcast() ? broadcast.toString() : ""));
    }

    public int getId() {
        if (!isValid()) {
            return 0;
        }

        int hashCode = 0;
        if (hasBroadcast())
            hashCode += broadcast.getAction().hashCode();

        return 31 * hashCode;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {

        if (isValid()) {
            parcel.writeString(actionType);
            parcel.writeParcelable(broadcast, flags);
        }
    }

    private ActionClickData(Parcel in) {
        readFromParcel(in);
    }

    private void readFromParcel(Parcel in) {
        actionType = in.readString();
        broadcast = in.readParcelable(ActionBroadcastData.class
                .getClassLoader());
    }

    public static final Creator<ActionClickData> CREATOR = new Creator<ActionClickData>() {
        @Override
        public ActionClickData createFromParcel(Parcel in) {
            return new ActionClickData(in);
        }

        @Override
        public ActionClickData[] newArray(int size) {
            return new ActionClickData[size];
        }
    };
}
