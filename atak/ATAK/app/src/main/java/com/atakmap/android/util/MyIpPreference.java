
package com.atakmap.android.util;

import android.app.Activity;
import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;

import com.atakmap.android.tools.menu.AtakActionBarListData;
import com.atakmap.android.tools.menu.AtakActionBarMenuData;
import com.atakmap.app.R;
import com.atakmap.comms.NetworkUtils;
import com.atakmap.comms.NetworkUtils.NetRestartNotification;

public class MyIpPreference extends Preference {

    private final Context ctx;
    private final String title;

    public MyIpPreference(Context ctx) {
        super(ctx);
        this.ctx = ctx;
        title = ctx.getString(R.string.my_ip);

        init();
    }

    public MyIpPreference(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        this.ctx = ctx;
        title = ctx.getString(R.string.my_ip);

        init();
    }

    // Initializer block
    public void init() {
        if (AtakActionBarListData.getOrientation(
                ctx) == AtakActionBarMenuData.Orientation.portrait) {
            setTitle(title);
            setSummary(NetworkUtils.getIP());
        } else
            setTitle(title + ": " + NetworkUtils.getIP());

        NetworkUtils.registerNetRestartNotification(
                NetworkUtils.ALL_CONNECTIONS, new NetRestartNotification() {
                    @Override
                    public void onNetRestartNotification(
                            final String newAddress, int newMask) {
                        ((Activity) ctx).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (AtakActionBarListData
                                        .getOrientation(
                                                ctx) == AtakActionBarMenuData.Orientation.portrait) {
                                    setTitle(title);
                                    setSummary(NetworkUtils.getIP());
                                } else
                                    setTitle(title + ": "
                                            + NetworkUtils.getIP());
                            }
                        });
                    }
                });
    }
}
