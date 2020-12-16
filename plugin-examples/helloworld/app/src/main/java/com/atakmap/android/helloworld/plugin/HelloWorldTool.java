
package com.atakmap.android.helloworld.plugin;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.BadgeDrawable;


import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import transapps.mapi.MapView;
import transapps.maps.plugin.tool.Group;
import transapps.maps.plugin.tool.Tool;
import transapps.maps.plugin.tool.ToolDescriptor;

/**
 * The Tool implementation within ATAK is just an ActionBar
 * Button that can be selected.    In most implementations a 
 * tool just launches the DropDown Receiver.   If the plugin
 * has not forward facing user drop down, this can be omitted.
 */
public class HelloWorldTool extends Tool implements ToolDescriptor {

    public static final String TAG = "HelloWorldTool";

    private final Context context;
    private final LayerDrawable _icon;


    public HelloWorldTool(final Context context) {
        this.context = context;

        // currently broken
        //_icon = AtakLayerDrawableUtil.getInstance(context).getBadgeableIcon(
        //        context.getDrawable(R.drawable.ic_launcher));


        _icon = (LayerDrawable) context.getResources().getDrawable(R.drawable.ic_launcher_badge, context.getTheme());


        AtakBroadcast.getInstance().registerReceiver(br, new AtakBroadcast.DocumentedIntentFilter("com.atakmap.android.helloworld.plugin.iconcount"));

    }


    private static void setBadgeCount(Context context, LayerDrawable icon,
                                      int count) {
        BadgeDrawable badge;

        // Reuse drawable if possible
        Drawable reuse = icon.findDrawableByLayerId(R.id.ic_badge);
        if (reuse instanceof BadgeDrawable) {
            badge = (BadgeDrawable) reuse;
        } else {
            badge = new BadgeDrawable(context);
        }

        badge.setCount(count);
        icon.mutate();
        icon.setDrawableByLayerId(R.id.ic_badge, badge);
    }




    private final BroadcastReceiver br = new BroadcastReceiver() {
        private int count = 0;
        @Override
        public void onReceive(Context c, Intent intent) {
            // currently broken
            //AtakLayerDrawableUtil.getInstance(context).setBadgeCount(_icon, count++);

            setBadgeCount(context, _icon, count++);

            Log.d(TAG, "increment visual count to: " + count);
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(ActionBarReceiver.REFRESH_ACTION_BAR));
        }
    };



    @Override
    public String getDescription() {
        return context.getString(R.string.app_name);
    }

    @Override
    public Drawable getIcon() {
         return _icon;
    }

    @Override
    public Group[] getGroups() {
        return new Group[] {
                Group.GENERAL
        };
    }

    @Override
    public String getShortDescription() {
        // remember to internationalize your code
        return context.getString(R.string.app_name);
    }

    @Override
    public Tool getTool() {
        return this;
    }

    @Override
    public void onActivate(Activity arg0, MapView arg1, ViewGroup arg2,
            Bundle arg3,
            ToolCallback arg4) {

        // Hack to close the dropdown that automatically opens when a tool
        // plugin is activated.
        if (arg4 != null) {
            arg4.onToolDeactivated(this);
        }

        // Intent to launch the dropdown or tool

        //arg2.setVisibility(ViewGroup.INVISIBLE);
        Intent i = new Intent(
                "com.atakmap.android.helloworld.SHOW_HELLO_WORLD");
        AtakBroadcast.getInstance().sendBroadcast(i);

    }

    @Override
    public void onDeactivate(ToolCallback arg0) {
        AtakBroadcast.getInstance().unregisterReceiver(br);

    }

}
