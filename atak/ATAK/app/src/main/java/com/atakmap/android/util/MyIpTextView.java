
package com.atakmap.android.util;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import com.atakmap.app.R;
import com.atakmap.comms.NetworkUtils;
import com.atakmap.comms.NetworkUtils.NetRestartNotification;

public class MyIpTextView extends TextView {

    private final Context ctx;

    public MyIpTextView(Context ctx) {
        super(ctx);
        this.ctx = ctx;
        init();
    }

    public MyIpTextView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        this.ctx = ctx;
        init();
    }

    private void init() {

        final String PREFACE = ctx.getString(R.string.my_ip) + ": ";
        setText(PREFACE + NetworkUtils.getIP());
        NetworkUtils.registerNetRestartNotification(
                NetworkUtils.ALL_CONNECTIONS, new NetRestartNotification() {
                    @Override
                    public void onNetRestartNotification(
                            final String newAddress, int newMask) {
                        ((Activity) ctx).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setText(PREFACE + newAddress);
                            }
                        });
                    }
                });
    }

}
