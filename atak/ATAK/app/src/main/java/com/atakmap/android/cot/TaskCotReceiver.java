
package com.atakmap.android.cot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;

import com.atakmap.android.maps.Association;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.comms.NetworkUtils;
import com.atakmap.android.overlay.DefaultMapGroupOverlay;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.comms.CotServiceRemote.ConnectionListener;
import com.atakmap.comms.CotServiceRemote.CotEventListener;

import java.util.HashMap;
import java.util.UUID;

public class TaskCotReceiver extends BroadcastReceiver implements
        MapEventDispatcher.MapEventDispatchListener, ConnectionListener {

    public static final String TAG = "TaskCotReceiver";
    private final HashMap<String, Association> _assocCache = new HashMap<>();

    private static final int TASK_REMOVER_DELAY = 10000;

    private final CotServiceRemote coTRemote;
    private final MapGroup _taskPointGroup;
    private final MapView _mapView;
    private final Context _context;

    private static class CotTaskBuilder {

        private CotPoint _cotPoint = CotPoint.ZERO;
        private String _assetUID = "";
        private String _targetUID = "";
        private String _taskType = "t-s";
        private final String _taskSubjectType = "t-s";
        private final String _taskOrderType = "t-o";
        private String _assetType = "";
        private String _targetType = "";
        private String _sourceUID = "Android.Task";
        private String _notifyString = "";
        private String _remarksSource = "TaskName";
        private String _remarksText = "";

        private CotTaskBuilder() {

        }

        public CotEvent build() {
            CotEvent cotEvent = new CotEvent();
            cotEvent.setUID(_sourceUID);
            cotEvent.setPoint(_cotPoint);
            cotEvent.setHow("h-e");
            cotEvent.setType(_taskType);

            CoordinatedTime time = new CoordinatedTime();
            cotEvent.setTime(time);
            cotEvent.setStart(time);
            cotEvent.setStale(time.addSeconds(10));
            cotEvent.setVersion("2.0");

            // detail
            CotDetail detail = new CotDetail();
            detail.setElementName("detail");
            cotEvent.setDetail(detail);

            // engage
            CotDetail engage = new CotDetail();
            engage.setElementName("engage");
            engage.setAttribute("shooter", _assetUID); // asset to task
            engage.setAttribute("target", _targetUID); // target
            // engageEd.setAttribute("fac", _destUID); //closest friendly

            // link - for asset
            CotDetail linkSurveillance = new CotDetail();
            linkSurveillance.setElementName("link");
            linkSurveillance.setAttribute("relation", _taskSubjectType);
            linkSurveillance.setAttribute("uid", _assetUID);
            linkSurveillance.setAttribute("type", _assetType);

            // link - for target
            CotDetail linkOrder = new CotDetail();
            linkOrder.setElementName("link");
            linkOrder.setAttribute("relation", _taskOrderType);
            linkOrder.setAttribute("uid", _targetUID);
            linkOrder.setAttribute("type", _targetType);

            // request
            CotDetail request = new CotDetail();
            request.setElementName("request");
            request.setAttribute("notify", _notifyString);

            // remarks
            CotDetail remarks = new CotDetail();
            remarks.setElementName("remarks");
            remarks.setAttribute("source", _remarksSource);
            remarks.setAttribute("time", time.toString());
            remarks.setAttribute("version", "0.2");
            remarks.setAttribute("to", "");
            remarks.setAttribute("type", "");
            remarks.setAttribute("comment", "");
            remarks.setAttribute("info", "");
            remarks.setInnerText(_remarksText);

            detail.addChild(engage);
            detail.addChild(linkOrder);
            detail.addChild(linkSurveillance);
            detail.addChild(request);
            if (!_remarksText.isEmpty())
                detail.addChild(remarks);

            return cotEvent;
        }

        public void setAssetUID(String taskAssetUID) {
            _assetUID = taskAssetUID;
        }

        public void setTargetUID(String taskTargetUID) {
            _targetUID = taskTargetUID;
        }

        public void setAssetType(String assetType) {
            _assetType = assetType;
        }

        public void setTargetType(String targetType) {
            _targetType = targetType;
        }

        public void setUID(String sourceUID) {
            _sourceUID = sourceUID;
        }

        public void setTaskType(String taskType) {
            _taskType = taskType;
        }

        public void setPoint(double lat, double lon, double hae, double ce,
                double le) {
            _cotPoint = new CotPoint(lat, lon, hae, ce, le);
        }

        public void setRequestNotify(String notify) {
            _notifyString = notify;
        }

        public void setRemarksSource(String source) {
            _remarksSource = source;
        }

        public void setRemarksText(String text) {
            _remarksText = text;
        }

    }

    @Override
    public void onCotServiceConnected(Bundle fullServiceState) {
    }

    @Override
    public void onCotServiceDisconnected() {
    }

    private void dispatchMessage(final String uid, final String message) {
        _mapView.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(_context, message, Toast.LENGTH_LONG).show();
            }
        });

        Thread thdRemAss = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(TASK_REMOVER_DELAY);
                    MapItem item = _mapView.getMapItem(uid);
                    if (item != null) {
                        try {
                            item.removeFromGroup();
                        } catch (Exception e) {
                            // this will really only happen if there is a problem with deleting the item.
                            // fail silently
                        }
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "error: ", e);
                }
            }
        }, "TaskCotRemoverThread");
        thdRemAss.start();
    }

    private String getRemark(CotEvent event) {
        if (event != null && event.getDetail() != null) {
            for (int i = 0; i < event.getDetail().childCount(); i++) {
                CotDetail child = event.getDetail().getChild(i);
                if (child != null && child.getElementName().equals("remarks")) {
                    if (!child.getAttribute("source")
                            .equals("TaskFromAndroid")) {
                        return child.getInnerText();
                    }
                }
            }
        }
        return "Not Specified";
    }

    public TaskCotReceiver(Context context, MapView view) {
        _mapView = view;
        _context = context;
        _taskPointGroup = new DefaultMapGroup("Task Points");
        _mapView.getMapOverlayManager().addOtherOverlay(
                new DefaultMapGroupOverlay(view, _taskPointGroup));
        coTRemote = new CotServiceRemote();
        coTRemote.setCotEventListener(new CotEventListener() {

            @Override
            public void onCotEvent(CotEvent event, Bundle extra) {

                if (event == null)
                    return;

                Association ass = _assocCache.get(event.getUID());
                if (ass == null || (event.getType() == null))
                    return;
                switch (event.getType()) {
                    case "y-a-w":
                        ass.setColor(Color.BLUE);
                        break;
                    case "y-s-e":
                        ass.setColor(Color.GREEN);
                        break;
                    case "y-c-s":
                        ass.setColor(Color.GRAY);
                        dispatchMessage(event.getUID(), "Task Complete");
                        break;
                    case "y-c-f-r":
                        ass.setColor(Color.RED);
                        dispatchMessage(event.getUID(),
                                "Request rejected because: "
                                        + getRemark(event));
                        break;
                    case "y-c-f-x":
                        ass.setColor(Color.RED);
                        dispatchMessage(event.getUID(),
                                "Request aborted because: "
                                        + getRemark(event));
                        break;
                    case "y-c-f":
                        ass.setColor(Color.RED);
                        dispatchMessage(event.getUID(),
                                "Request failed because: "
                                        + getRemark(event));
                        break;
                }
                // everything else we don't care bout
                _assocCache.put(event.getUID(), ass);
            }

        });

        coTRemote.connect(this);
    }

    public void dispose() {
        coTRemote.disconnect();
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent.hasExtra("subjectUID")
                && (intent.hasExtra("uid") || intent.hasExtra("point"))) {
            _sendTask(intent);
        }
    }

    private void _sendTask(Intent intent) {
        Marker taskSubject = null;
        Marker taskObject = null;
        GeoPoint taskPoint = null;

        String subjectUID = intent.getStringExtra("subjectUID");
        MapItem item = _mapView.getMapItem(subjectUID);
        if (item instanceof Marker) {
            taskSubject = (Marker) item;
        }

        if (intent.hasExtra("uid")) {
            String s = intent.getStringExtra("uid");
            if (s != null) {
                item = _mapView.getMapItem(s);
                taskObject = (Marker) item;
            }
        } else if (intent.hasExtra("point")) {
            try {
                taskPoint = GeoPoint.parseGeoPoint(intent
                        .getStringExtra("point"));
            } catch (Exception ex) {
                Log.e(TAG, "error: ", ex);
            }
        }

        if (taskSubject != null && (taskObject != null || taskPoint != null)) {
            if (taskPoint == null) {
                taskPoint = taskObject.getPoint();
            }
            if (taskObject != null)
                _sendTask(taskSubject, taskObject, taskPoint);
        }
    }

    private void _sendTask(Marker taskSubject, Marker taskObject,
            GeoPoint taskPoint) {
        CotTaskBuilder b = new CotTaskBuilder();

        String subjUID = taskSubject.getUID();
        String subjNotify = taskSubject.getMetaString("notify", null);
        String subjType = taskSubject.getType();
        String taskType = taskSubject.getMetaString("taskType", null);

        String objectUID = taskObject.getUID();
        String objectType = taskObject.getType();

        CotEvent target = new CotEvent();
        target.setUID(objectUID);
        Marker objMarker;
        try {
            objMarker = (Marker) _mapView.getMapItem(objectUID);
            GeoPoint taskLocation = objMarker.getPoint();
            target.setPoint(new CotPoint(taskLocation));
        } catch (Exception e) {
            Log.d(TAG, "error occurred parsing task", e);
            return;
        }

        target.setHow("h-e");
        target.setType(objectType);

        CoordinatedTime time = new CoordinatedTime();
        target.setTime(time);
        target.setStart(time);
        target.setStale(time.addSeconds(10));
        target.setVersion("2.0");

        b.setAssetUID(subjUID);
        b.setAssetType(subjType);
        b.setTargetUID(objectUID);
        b.setTargetType(objectType);
        b.setPoint(taskPoint.getLatitude(), taskPoint.getLongitude(), 0, 10d,
                10d);

        b.setRequestNotify(NetworkUtils.getIP() + ":4242:tcp"); // TODO AS make me configurable
        b.setRemarksSource("TaskFromAndroid");
        String taskUID = subjUID + ".Task." + new CoordinatedTime();
        b.setUID(taskUID);
        b.setTaskType(taskType);

        CotEvent event = b.build();

        CotMapComponent.getExternalDispatcher().dispatchToConnectString(target,
                subjNotify);
        CotMapComponent.getExternalDispatcher().dispatchToConnectString(event,
                subjNotify);

        Marker taskMarker = new Marker(taskPoint, event.getUID());
        taskMarker.setType("t-s");
        taskMarker.setMetaBoolean("removable", true);
        taskMarker.setTitle(taskSubject.getTitle() + " Task");

        _taskPointGroup.addItem(taskMarker);
        _setTaskAssociation(taskUID, taskSubject, taskMarker, Color.YELLOW);
    }

    private void _setTaskAssociation(String taskUID, Marker subjectMarker,
            Marker taskMarker,
            int color) {
        Association taskAssociation = new Association(UUID.randomUUID()
                .toString());
        taskAssociation.setLink(Association.LINK_LINE);
        taskAssociation.setStyle(Association.STYLE_SOLID);
        taskAssociation.setStrokeWeight(3);
        taskAssociation.setColor(color);
        taskAssociation.setFirstItem(subjectMarker);
        taskAssociation.setSecondItem(taskMarker);
        String subjUID = subjectMarker.getUID();
        Association lastTaskAssoc = _assocCache.get(subjUID);
        if (lastTaskAssoc != null) {
            _taskPointGroup.removeItem(lastTaskAssoc);
        }
        _taskPointGroup.addItem(taskAssociation);
        _assocCache.put(taskUID, taskAssociation);
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (event.getType().equals(MapEvent.ITEM_REMOVED)) {
            final String uid = event.getItem().getUID();
            if (uid != null) {
                Association ass = _assocCache.get(uid);
                if (_taskPointGroup.containsItem(ass)) {
                    _taskPointGroup.removeItem(ass);
                    _assocCache.remove(uid);
                }
            }
        }
    }
}
