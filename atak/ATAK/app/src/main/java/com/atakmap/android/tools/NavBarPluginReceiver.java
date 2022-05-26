
package com.atakmap.android.tools;

import java.util.ArrayList;
import java.util.List;

import com.atakmap.android.navigation.NavButtonManager;
import com.atakmap.android.navigation.models.NavButtonModel;
import com.atakmap.android.tools.menu.ActionBroadcastData;
import com.atakmap.android.tools.menu.ActionClickData;
import com.atakmap.android.tools.menu.ActionMenuData;
import com.atakmap.coremap.log.Log;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;

public class NavBarPluginReceiver extends BroadcastReceiver {
    private static final String TAG = "NavBarPluginReceiver";

    /**
     * Add menus for the specified plugins
     *
     * @param menus plugin menu data
     */
    private static void addPlugins(List<ActionMenuData> menus) {
        if (menus == null) {
            Log.w(TAG, "adding plugins invalid");
            return;
        }

        Log.d(TAG, "adding plugins: " + menus.size());
        List<ActionMenuData> plugins = new ArrayList<>();

        for (ActionMenuData plugin : menus) {
            if (!plugins.contains(plugin)) {
                plugins.add(plugin);
                NavButtonManager.getInstance().addButtonModel(plugin);
            }
        }
    }

    /**
     * Remove menus for the specified plugins
     *
     * @param menus plugin menu data
     */
    private static void removePlugins(List<ActionMenuData> menus) {
        for (ActionMenuData plugin : menus) {
            NavButtonManager.getInstance().removeButtonModel(plugin);
        }
    }

    public NavBarPluginReceiver() {
    }

    public void dispose() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action == null)
            return;

        Log.d(TAG, "Received intent: " + action);
        switch (action) {
            case ActionBarReceiver.ADD_NEW_TOOL: {
                ActionMenuData actionMenuData = null;
                NavButtonModel buttonModel;
                try {
                    String actionToBroadcast = intent
                            .getStringExtra("actionToBroadcast");
                    ArrayList<ActionClickData> temp = new ArrayList<>();
                    temp.add(new ActionClickData(new ActionBroadcastData(
                            actionToBroadcast, null), ActionClickData.CLICK));
                    buttonModel = NavButtonModel.NONE;
                    NavButtonManager.getInstance().addButtonModel(buttonModel);
                } catch (Exception e) {
                    Log.w(TAG, "Unable to decode intent into ActionMenuData "
                            + intent);
                }

                if (actionMenuData == null || !actionMenuData.isValid()) {
                    Log.w(TAG,
                            "Ignoring invalid plugin menu: "
                                    + (actionMenuData == null ? "null"
                                            : actionMenuData.toString()));
                    return;
                }
                break;
            }

            case ActionBarReceiver.ADD_NEW_TOOLS: {
                Parcelable[] pl = intent.getParcelableArrayExtra("menus");

                List<ActionMenuData> list = new ArrayList<>();
                if (pl != null && pl.length > 0) {
                    for (Parcelable p : pl) {
                        if (p instanceof ActionMenuData) {
                            list.add((ActionMenuData) p);
                        } else {
                            Log.w(TAG,
                                    "Ignoring invalid plugin menu of type");
                        }
                    }
                }

                addPlugins(list);
                break;
            }

            case ActionBarReceiver.REMOVE_TOOLS: {
                Parcelable[] pl = intent.getParcelableArrayExtra("menus");

                List<ActionMenuData> list = new ArrayList<>();
                if (pl != null && pl.length > 0) {
                    for (Parcelable p : pl) {
                        if (p instanceof ActionMenuData) {
                            list.add((ActionMenuData) p);
                        } else {
                            Log.w(TAG,
                                    "Ignoring invalid plugin menu of type");
                        }
                    }
                }

                removePlugins(list);
                break;
            }
        }
    }
}
