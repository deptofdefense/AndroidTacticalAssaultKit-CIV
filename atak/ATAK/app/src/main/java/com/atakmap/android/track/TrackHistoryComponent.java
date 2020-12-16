
package com.atakmap.android.track;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.data.ClearContentRegistry;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.GLMapItemFactory;
import com.atakmap.android.track.maps.GLTrackPolyline;
import com.atakmap.android.track.ui.TrackHistoryPrefsFragment;
import com.atakmap.app.R;
import com.atakmap.app.preferences.ToolsPreferenceFragment;

public class TrackHistoryComponent extends AbstractMapComponent {

    private BreadcrumbReceiver breadRec;
    private TrackHistoryDropDown _trackHistoryDropDown;

    @Override
    public void onCreate(final Context context, final Intent intent,
            final MapView view) {

        GLMapItemFactory.registerSpi(GLTrackPolyline.SPI);

        MapGroup mg = view.getRootGroup().findMapGroup(
                BreadcrumbReceiver.TRACK_HISTORY_MAPGROUP);
        if (mg == null) {
            mg = new DefaultMapGroup(
                    BreadcrumbReceiver.TRACK_HISTORY_MAPGROUP);
            mg.setMetaString("overlay", "trackhistory");
            //trackGroup.setMetaBoolean("permaGroup", true);
            view.getRootGroup().addGroup(mg);
        }
        final MapGroup trackGroup = mg;

        // on first start, database creation can be 4 times longer than a normal run.
        Thread t = new Thread() {
            public void run() {
                breadRec = new BreadcrumbReceiver(view, trackGroup);
                DocumentedIntentFilter breadFilter = new DocumentedIntentFilter();
                breadFilter.addAction(BreadcrumbReceiver.TOGGLE_BREAD);
                breadFilter.addAction(BreadcrumbReceiver.COLOR_CRUMB);
                breadFilter.addAction(
                        "com.atakmap.android.location.LOCATION_INIT");
                breadFilter.addAction("com.atakmap.android.bread.DELETE_TRACK");
                breadFilter.addAction(
                        "com.atakmap.android.bread.CREATE_TRACK_SEGMENT");
                breadFilter.addAction(TrackHistoryDropDown.CLEAR_TRACKS);
                breadFilter
                        .addAction(
                                "com.atakmap.android.bread.UPDATE_TRACK_METADATA");
                breadFilter
                        .addAction(TrackHistoryDropDown.EXPORT_TRACK_HISTORY);

                AtakBroadcast.getInstance().registerReceiver(breadRec,
                        breadFilter);

                ClearContentRegistry.getInstance()
                        .registerListener(breadRec.dataMgmtReceiver);

                _trackHistoryDropDown = new TrackHistoryDropDown(view,
                        trackGroup);
                DocumentedIntentFilter dropDownFilter = new DocumentedIntentFilter();
                dropDownFilter.addAction(TrackHistoryDropDown.TRACK_HISTORY);
                dropDownFilter.addAction(TrackHistoryDropDown.DELETE_TRACK);
                dropDownFilter.addAction(TrackHistoryDropDown.TRACK_USERLIST);
                dropDownFilter.addAction(TrackHistoryDropDown.TRACK_SEARCH);
                dropDownFilter.addAction(TrackHistoryDropDown.TRACKSEXPORTED);
                dropDownFilter
                        .addAction(TrackHistoryDropDown.SERVERTRACKSEXPORTED);
                AtakBroadcast.getInstance().registerReceiver(
                        _trackHistoryDropDown,
                        dropDownFilter);
            }
        };
        t.start();
        ToolsPreferenceFragment.register(
                new ToolsPreferenceFragment.ToolPreference(
                        view.getContext().getString(
                                R.string.trackHistoryPreferences),
                        view.getContext().getString(
                                R.string.adj_track_crumb_opts),
                        "atakTrackOptions",
                        view.getContext().getResources().getDrawable(
                                R.drawable.ic_menu_selfhistory),
                        new TrackHistoryPrefsFragment()));
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {

        if (breadRec != null) {
            ClearContentRegistry.getInstance()
                    .unregisterListener(breadRec.dataMgmtReceiver);
            AtakBroadcast.getInstance().unregisterReceiver(breadRec);
            breadRec.dispose();
            breadRec = null;
        }

        if (_trackHistoryDropDown != null) {
            AtakBroadcast.getInstance().unregisterReceiver(
                    _trackHistoryDropDown);
            _trackHistoryDropDown.dispose();
            _trackHistoryDropDown = null;
        }

        GLMapItemFactory.unregisterSpi(GLTrackPolyline.SPI);
    }
}
