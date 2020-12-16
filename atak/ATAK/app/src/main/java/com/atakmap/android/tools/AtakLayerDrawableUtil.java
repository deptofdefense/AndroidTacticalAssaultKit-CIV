
package com.atakmap.android.tools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.HashMap;

/**
 *
 * Thanks to http://stackoverflow.com/questions/18156477/how-to-make-an-icon-in-the-action-bar-with-the-number-of-notification
 * TODO:
 *    Remove the context passing in as part of initialization.
 *    Figure out a better way to not blindly call MapView.getMapView().getContext();
 *    Remove the code marked this is stupid do not use.
 */
public class AtakLayerDrawableUtil {

    private final static String TAG = "AtakLayerDrawableUtil";

    private static AtakLayerDrawableUtil instance;

    // refactor me
    private final HashMap<String, String> atakLayerBroadcastMap = new HashMap<>();
    private final DocumentedIntentFilter inf = new DocumentedIntentFilter();

    private final Context context;

    synchronized public static AtakLayerDrawableUtil getInstance(
            Context context) {
        if (instance == null) {
            instance = new AtakLayerDrawableUtil();
        }
        return instance;
    }

    private AtakLayerDrawableUtil() {
        this.context = MapView.getMapView().getContext();

        AtakBroadcast.getInstance().registerReceiver(br, inf);
    }

    final BroadcastReceiver br = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (atakLayerBroadcastMap != null) {
                String badgeString = intent.getStringExtra("badge_string");
                if (badgeString != null) {
                    atakLayerBroadcastMap.put(intent.getAction(), badgeString);
                }
            }
        }
    };

    public void dispose() {
        try {
            AtakBroadcast.getInstance().unregisterReceiver(br);
        } catch (Exception ignored) {
        }
    }

    // this is stupid do not use
    synchronized public void addLayerDrawableBroadcastString(
            String broadcastString) {
        if (!inf.hasAction(broadcastString)) {
            AtakBroadcast.getInstance().unregisterReceiver(br);
            inf.addAction(broadcastString);
            AtakBroadcast.getInstance().registerReceiver(br, inf);
        }
    }

    // this is stupid do not use
    public int getBadgeInt(String iconPath) {
        int ret = 0;
        if (atakLayerBroadcastMap != null) {
            String str = atakLayerBroadcastMap.get(iconPath);
            if (str != null && !str.isEmpty()) {
                ret = Integer.parseInt(str);
            }

        }
        return ret;
    }

    /**
     * Turns a normal drawable into a badgeable icon.
     * @param d the normal drawable
     * @return the LayerDrawable capable of badging.
     */
    public LayerDrawable getBadgeableIcon(Drawable d) {

        final LayerDrawable retval = (LayerDrawable) context.getResources()
                .getDrawable(R.drawable.details_badge,
                        context.getTheme());
        if (retval != null) {
            retval.setDrawableByLayerId(R.id.details_base, d);
            retval.setDrawableByLayerId(R.id.ic_badge, d);
        }

        return retval;
    }

    /**
     * Set the badge count on a LayerDrawable that has been built from getBadgeableIcon.
     *
     * @param icon the layer drawable built from getBadgeableIcon
     * @param count the count.
     */
    public void setBadgeCount(LayerDrawable icon, int count) {
        setBadgeCount(icon, null, count, null, null);
    }

    /**
     * Set the badge count on a LayerDrawable that has been built from getBadgeableIcon.
     *
     * @param icon the layer drawable built from getBadgeableIcon
     * @param count the count.
     * @param color the presence color.
     */
    public void setBadgeCount(LayerDrawable icon, int count, Integer color) {
        setBadgeCount(icon, null, count, color, null);
    }

    /**
     * Set the badge count on a LayerDrawable that has been built from getBadgeableIcon.
     *
     * @param icon the layer drawable built from getBadgeableIcon
     * @param baseDrawable the base icon.
     * @param count the count.
     * @param color the presence color.
     */
    public void setBadgeCount(LayerDrawable icon, Drawable baseDrawable,
            int count, Integer color) {
        setBadgeCount(icon, baseDrawable, count, color, null);
    }

    /**
     * Sets the icons count given a base drawable and a presence color.
     * @param icon the icon to set the count on
     * @param baseDrawable the base drawable to use for the graphic, can be null to retain the base
     *                     graphic.
     * @param count the count
     * @param presenceColor the presence color, can be null.    If null the default of red is used.
     * @param textSize the size of the text, can be null.   If null, the default size is used.
     */
    public void setBadgeCount(LayerDrawable icon, Drawable baseDrawable,
            int count, Integer presenceColor, Float textSize) {
        BadgeDrawable badge;

        // Reuse drawable if possible
        Drawable reuse = icon.findDrawableByLayerId(R.id.ic_badge);
        if (reuse instanceof BadgeDrawable) {
            badge = (BadgeDrawable) reuse;
        } else {
            badge = new BadgeDrawable(context);
        }

        // should allow for setting the textSize and then the color
        badge.setColor(presenceColor);
        badge.setCount(count);

        icon.mutate();
        if (!icon.setDrawableByLayerId(R.id.ic_badge, badge)) {
            Log.d(TAG, "error occurred replacing the badge on a layer");
        }

        if (baseDrawable != null) {
            if (!icon.setDrawableByLayerId(R.id.details_base, baseDrawable)) {
                Log.d(TAG, "error occurred replacing the base on a layer");
            }
        }
    }
}
