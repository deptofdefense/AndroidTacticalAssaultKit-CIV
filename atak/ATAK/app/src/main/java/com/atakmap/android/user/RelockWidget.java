
package com.atakmap.android.user;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MarkerIconWidget;
import com.atakmap.android.widgets.ProgressWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.widgets.TextWidget;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;

public class RelockWidget extends LinearLayoutWidget implements
        MapWidget.OnClickListener {

    private static final String TAG = "RelockWidget";

    /**
     * Millis to display Relock Widget buttons. 5 seconds
     */
    private static final int RELOCK_WIDGET_MILLIS = 5000;

    private final MapView _mapView;

    private final MarkerIconWidget _relockWidget;
    private final MarkerIconWidget _cancelWidget;

    private String _uid;
    private long _visibleTime = -1;
    private final ProgressWidget timeBar;

    RelockWidget(MapView mapView) {
        _mapView = mapView;
        Context _context = mapView.getContext();

        RootLayoutWidget root = (RootLayoutWidget) _mapView
                .getComponentExtra("rootLayoutWidget");
        LinearLayoutWidget leftEdgeV = root
                .getLayout(RootLayoutWidget.LEFT_EDGE)
                .getOrCreateLayout("LE_RL");
        leftEdgeV.setGravity(Gravity.TOP);
        leftEdgeV.setLayoutParams(LinearLayoutWidget.WRAP_CONTENT,
                LinearLayoutWidget.MATCH_PARENT);

        setName("RelockWidget");
        //setGravity(Gravity.CENTER_HORIZONTAL);
        setOrientation(LinearLayoutWidget.VERTICAL);
        setPadding(0, toDx(8), 0, 0);
        setMargins(0f, toDx(4), 0f, 0f);
        setBackingColor(0x99000000);
        setNinePatchBG(true);
        setVisible(false);

        LinearLayoutWidget titleLayout = new LinearLayoutWidget();
        titleLayout.setMargins(0f, 0f, 0f, toDx(4));
        titleLayout.setOrientation(LinearLayoutWidget.VERTICAL);
        titleLayout.setGravity(Gravity.CENTER_VERTICAL);
        addWidget(titleLayout);

        TextWidget _reopenWidgetTitle = new TextWidget(
                _context.getString(R.string.relock), 2);
        _reopenWidgetTitle.setBackground(TextWidget.TRANSLUCENT);
        titleLayout.addWidget(_reopenWidgetTitle);

        LinearLayoutWidget buttonLayout = new LinearLayoutWidget();
        buttonLayout.setOrientation(LinearLayoutWidget.HORIZONTAL);
        buttonLayout.setGravity(Gravity.CENTER);
        addWidget(buttonLayout);

        _relockWidget = new MarkerIconWidget();
        _relockWidget.setIcon(createIcon(R.drawable.ic_menu_lock_unlit));
        _relockWidget.setSize(42, 42);
        _relockWidget.addOnClickListener(this);
        buttonLayout.addWidget(_relockWidget);

        _cancelWidget = new MarkerIconWidget();
        _cancelWidget.setIcon(createIcon(R.drawable.whitex));
        _cancelWidget.setPadding(toDx(12), toDx(12), toDx(12), toDx(4));
        _cancelWidget.setSize(42, 42);
        _cancelWidget.addOnClickListener(this);
        buttonLayout.addWidget(_cancelWidget);

        timeBar = new ProgressWidget();
        timeBar.setMax(RELOCK_WIDGET_MILLIS);
        timeBar.setHeight(toDx(14));
        timeBar.setMargins(0f, toDx(14f), 0f, toDx(-6f));
        timeBar.setWidth(getWidth());
        addWidget(timeBar);

        leftEdgeV.addChildWidgetAt(0, this);
    }

    private float toDx(float val) {
        return MapView.DENSITY * val;
    }

    void setUid(final String uid) {
        _uid = uid;
    }

    @Override
    public boolean setVisible(final boolean visible) {
        if (visible)
            setAlpha(255);
        _visibleTime = visible ? SystemClock.elapsedRealtime() : -1;

        if (_relockWidget != null)
            _relockWidget.setIcon(createIcon(R.drawable.ic_menu_lock_unlit));
        if (timeBar != null)
            timeBar.setProgress(RELOCK_WIDGET_MILLIS);
        return super.setVisible(visible);
    }

    void onTick() {
        if (!isVisible())
            return;
        if (FileSystemUtils.isEmpty(_uid)) {
            setVisible(false);
            return;
        }

        // t is the time in millis that the widget has been displayed
        long t = SystemClock.elapsedRealtime() - _visibleTime;

        if (t < RELOCK_WIDGET_MILLIS) {
            timeBar.setProgress((int) (RELOCK_WIDGET_MILLIS - t));
        }

        if (_visibleTime > 0 && t > RELOCK_WIDGET_MILLIS) {
            _visibleTime = -1;
            fadeAlpha(255, 0, 1000);
            _mapView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (_visibleTime == -1)
                        setVisible(false);
                }
            }, 1000);
        }
    }

    @Override
    public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
        if (_visibleTime == -1 && isVisible()) {
            // Widget clicked while fading out
            _visibleTime = SystemClock.elapsedRealtime();
            setAlpha(255);
        }
        if (widget == _relockWidget) {

            _relockWidget.setIcon(createIcon(R.drawable.ic_menu_lock_lit));
            _mapView.postDelayed(new Runnable() {
                public void run() {
                    setVisible(false);
                    if (!FileSystemUtils.isEmpty(_uid)) {

                        AtakBroadcast.getInstance().sendBroadcast(
                                new Intent("com.atakmap.android.maps.UNFOCUS"));
                        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                                "com.atakmap.android.maps.HIDE_DETAILS"));
                        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                                "com.atakmap.android.maps.HIDE_MENU"));

                        Intent intent = new Intent(CamLockerReceiver.LOCK_CAM);
                        intent.putExtra("uid", _uid);
                        AtakBroadcast.getInstance().sendBroadcast(intent);
                    }
                }
            }, 150);

        } else if (widget == _cancelWidget) {
            Log.d(TAG, "onMapWidgetClick reopenWidget cancel");
            setVisible(false);
        }
    }

    private Icon createIcon(final int icon) {
        Icon.Builder b = new Icon.Builder();
        b.setAnchor(0, 0);
        b.setColor(Icon.STATE_DEFAULT, Color.WHITE);
        b.setSize(42, 42);

        final String uri = "android.resource://"
                + _mapView.getContext().getPackageName()
                + "/" + icon;

        b.setImageUri(Icon.STATE_DEFAULT, uri);
        return b.build();
    }
}
