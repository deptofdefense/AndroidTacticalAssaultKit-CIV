
package com.atakmap.android.statesaver;

import android.os.Bundle;

import com.atakmap.android.cot.CotMapAdapter;
import com.atakmap.android.importexport.CotEventFactory;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.StatementIface;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StateSaverListener implements
        MapEventDispatcher.MapEventDispatchListener {
    public static final String TAG = "StateSaverListener";

    class Handler implements Runnable {
        Handler() {
        }

        @Override
        public void run() {
            Map<String, EventRecord> events = new HashMap<>();

            boolean endTransactionError = false;

            while (!endTransactionError) {
                synchronized (StateSaverListener.this) {
                    if (StateSaverListener.this.worker != this)
                        break;

                    if (StateSaverListener.this.eventsToProcess.size() < 1) {
                        try {
                            StateSaverListener.this.wait();
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }

                    // pull the queued events out of the
                    events.putAll(StateSaverListener.this.eventsToProcess);
                    StateSaverListener.this.eventsToProcess.clear();
                }

                StringBuilder sb = new StringBuilder();
                EventRecord record;
                String xml;
                String json;
                boolean updateOnly;
                synchronized (StateSaver.dbWriteLock) {
                    try {
                        stateSaverDatabase.beginTransaction();
                    } catch (Exception e) {
                        Log.d(TAG, "begin transaction error", e);
                        return;
                    }
                    try {
                        for (Map.Entry<String, EventRecord> entry : events
                                .entrySet()) {
                            record = entry.getValue();
                            updateOnly = false;
                            switch (record.type) {
                                case EventRecord.TYPE_DELETE_RECORD: {
                                    if (deleteStmt == null)
                                        deleteStmt = stateSaverDatabase
                                                .compileStatement("DELETE FROM "
                                                        + StateSaver.TABLE_COTEVENTS
                                                        + " WHERE uid = ?");

                                    try {
                                        deleteStmt.bind(1, entry.getKey());

                                        deleteStmt.execute();
                                    } finally {
                                        deleteStmt.clearBindings();
                                    }

                                    break;
                                }
                                case EventRecord.TYPE_UPDATE_ONLY_RECORD:
                                    updateOnly = true;
                                case EventRecord.TYPE_CREATE_UPDATE_RECORD: {
                                    if (record.event == null
                                            && (record.json == null
                                                    || record.json
                                                            .length() == 0))
                                        throw new IllegalStateException();

                                    if (record.json != null
                                            && record.json.length() > 0) {
                                        xml = "";
                                        json = record.json;
                                    } else if (record.event != null) {
                                        json = "";
                                        if (sb.length() > 0)
                                            sb.delete(0, sb.length());

                                        record.event.buildXml(sb);
                                        if (sb.length() == 0) // xml creation error
                                            continue;

                                        xml = sb.toString();
                                    } else {
                                        throw new IllegalStateException();
                                    }

                                    final boolean contains;
                                    CursorIface cursor = null;
                                    try {
                                        checkQueryArgs(1);
                                        queryArgs[0] = record.euid;
                                        cursor = stateSaverDatabase
                                                .query("SELECT "
                                                        + StateSaver.COLUMN_ID
                                                        + " FROM "
                                                        + StateSaver.TABLE_COTEVENTS
                                                        + " WHERE uid = ?",
                                                        queryArgs);
                                        contains = cursor.moveToNext();
                                    } finally {
                                        if (cursor != null)
                                            cursor.close();
                                    }

                                    // the item was marked for update-only by
                                    // the 'doNotRecreate' extra, but no entry
                                    // exists -- assume a race and ignore the
                                    // event
                                    if (updateOnly && !contains)
                                        continue;

                                    StatementIface insertUpdateStmt;
                                    if (contains) {
                                        if (updateStmt == null) {
                                            updateStmt = stateSaverDatabase
                                                    .compileStatement("UPDATE "
                                                            +
                                                            StateSaver.TABLE_COTEVENTS
                                                            +
                                                            " SET "
                                                            + StateSaver.COLUMN_TYPE
                                                            + " = ?, "
                                                            + StateSaver.COLUMN_EVENT
                                                            + " = ?, "
                                                            + StateSaver.COLUMN_JSON
                                                            + " = ?, "
                                                            + StateSaver.COLUMN_VISIBLE
                                                            + " = ?, "
                                                            + StateSaver.COLUMN_LAST_UPDATE
                                                            + " = ?, "
                                                            + StateSaver.COLUMN_QUERY_ORDER
                                                            + " = ?" +
                                                            " WHERE uid = ?");
                                        }

                                        insertUpdateStmt = updateStmt;
                                    } else {
                                        if (insertStmt == null) {
                                            insertStmt = stateSaverDatabase
                                                    .compileStatement(
                                                            "INSERT INTO "
                                                                    +
                                                                    StateSaver.TABLE_COTEVENTS
                                                                    +
                                                                    " ("
                                                                    + StateSaver.COLUMN_TYPE
                                                                    + ", "
                                                                    + StateSaver.COLUMN_EVENT
                                                                    + ", "
                                                                    + StateSaver.COLUMN_JSON
                                                                    + ", "
                                                                    + StateSaver.COLUMN_VISIBLE
                                                                    + ", "
                                                                    + StateSaver.COLUMN_LAST_UPDATE
                                                                    + ", "
                                                                    + StateSaver.COLUMN_QUERY_ORDER
                                                                    + ","
                                                                    + StateSaver.COLUMN_UID
                                                                    + ")"
                                                                    +
                                                                    " VALUES (?, ?, ?, ?,  ?, ?, ?)");
                                        }

                                        insertUpdateStmt = insertStmt;
                                    }

                                    try {
                                        int idx = 1;

                                        insertUpdateStmt.bind(idx++,
                                                record.etype);
                                        insertUpdateStmt.bind(idx++, xml);
                                        insertUpdateStmt.bind(idx++, json);
                                        insertUpdateStmt.bind(idx++,
                                                record.visible ? 1 : 0);
                                        insertUpdateStmt.bind(idx++,
                                                record.lastUpdateTime);
                                        insertUpdateStmt
                                                .bind(idx++,
                                                        StateSaver
                                                                .getQueryOrder(
                                                                        record.etype));
                                        insertUpdateStmt.bind(idx++,
                                                record.euid);

                                        insertUpdateStmt.execute();
                                    } finally {
                                        insertUpdateStmt.clearBindings();
                                    }
                                    break;
                                }
                                default:
                                    throw new IllegalStateException();

                            }
                        }
                        events.clear();

                        stateSaverDatabase.setTransactionSuccessful();
                    } finally {
                        try {
                            stateSaverDatabase.endTransaction();
                        } catch (Exception e) {
                            Log.d(TAG, "end transaction error", e);
                            endTransactionError = true;
                        }
                    }
                }
            }
        }
    }

    private final DatabaseIface stateSaverDatabase;
    private StatementIface insertStmt;
    private StatementIface updateStmt;
    private StatementIface deleteStmt;

    /**
     * A record of the events pending to be processed. The keys are UIDs, the values are the events
     * to be serialized. A <code>null</code> event may be specified to signal deletion.
     */
    private final Map<String, EventRecord> eventsToProcess;
    private Handler worker;

    public StateSaverListener(DatabaseIface stateSaverDatabase, MapView view) {
        this.stateSaverDatabase = stateSaverDatabase;
        this.insertStmt = null;
        this.updateStmt = null;
        this.deleteStmt = null;

        deviceUID = view.getSelfMarker().getUID();

        this.eventsToProcess = new HashMap<>();
        this.worker = null;
    }

    @Override
    public void onMapEvent(MapEvent event) {
        if (event.getType().equals(MapEvent.ITEM_PERSIST)) {
            final MapItem mi = event.getItem();
            final Bundle b = event.getExtras();

            if (mi == null) {
                Log.d(TAG, "MapItem passed into the MapEvent was null.");
                return;
            }

            final CotEvent cotEvent;

            /** 
             * Currently a hack to allow for the Enterprise Sync tool 
             * to function with a self provided cot string for persisting
             * this will completely ignore any attributes about the MapItem 
             * an only use the cot string passed in.
             *
             * THIS BEHAVIOR IS CONSIDERED DEPRECATED
             */
            String cot = mi.getMetaString("legacy_cot_event", null);
            String json = null;

            if (mi instanceof Marker) {
                // disabled for now - issue with Circles
                //json = ((Marker)mi).toJSON();
            }
            if (json == null || json.length() == 0) {
                if (cot != null) {
                    // This is the INCORRECT way to handle a MapItem
                    cotEvent = CotEvent.parse(cot);

                    //Log.d(TAG, "cotEvent type=" + cotEvent.getType() + 
                    //           " uid=" + cotEvent.getUID() + " mi.type=" + mi.getType() +
                    //           " mi.uid=" + mi.getUID());
                } else {

                    // if there is no map group, and it was not a legacy
                    // style cursor on target message, go ahead an kick out.
                    if (mi.getGroup() == null) {
                        //Log.d(TAG, "no map group, not persisting: " + 
                        //           mi.getUID() + " " + mi.getType() + 
                        //           " " + mi.getMetaString("callsign", "[no-callsign]"));
                        return;
                    }

                    // This is the CORRECT way to handle a MapItem
                    cotEvent = CotEventFactory.createCotEvent(mi);
                }
            } else {
                cotEvent = null;
            }

            if ((cotEvent != null && cotEvent.isValid())
                    || (json != null && json.length() > 0)) {
                //Log.d(TAG, "saving event: " + cotEvent);
                //Log.d(TAG, "saving event size: " + cotEvent.toString().length());
                //Log.d(TAG, "saving item: " + mi.getUID() + " " + mi.getType() + " " + mi.getMetaString("callsign", "[no title]") + " ", new Exception());    // important for debug of the state saver

                final Bundle bundle = (b != null) ? b : new Bundle();
                bundle.putBoolean("visible", mi.getVisible(true));
                bundle.putLong("lastUpdateTime",
                        mi.getMetaLong("lastUpdateTime", -1));

                if (CotMapAdapter.isAtakSpecialType(mi)) {
                    if (cot != null) {
                        // This is the INCORRECT way to handle a MapItem
                        //Log.d(TAG, "saving a legacy cotEvent: " + cot);
                        onCotEvent(cotEvent.getUID(), cotEvent.getType(),
                                cotEvent, json,
                                new Bundle());
                    } else {
                        onCotEvent(mi.getUID(), mi.getType(), cotEvent, json,
                                bundle);
                    }
                    //Log.d(TAG, "finished saving cot event: " + mi.getUID());
                }

            } else {
                Log.d(TAG,
                        "CotEventFactory failed or was instructed not to process MapItem class: "
                                +
                                mi.getClass()
                                + " uid: "
                                + mi.getUID()
                                + " callsign: "
                                +
                                mi.getMetaString("callsign",
                                        "[not set]")
                                + " result: " + cotEvent);
            }

        } else if (event.getType().equals(MapEvent.ITEM_REMOVED)) // it its ITEM_* item exists
        {
            final String uid = event.getItem().getUID(); // data can never be null
            final String type = event.getItem().getType(); // data can never be null

            //Log.d(TAG, "remove item: " + uid + " " + type + " " + event.getItem().getMetaString("callsign", "[no title]")  + " " + event.getFrom(), new Exception() );   // important for debug of the state saver
            if (uid != null) {
                synchronized (this) {
                    // overwrite the current event with null to indicate
                    // deletion
                    EventRecord record = this.eventsToProcess.get(uid);
                    if (record == null) {
                        eventsToProcess.put(uid, new EventRecord(
                                EventRecord.TYPE_DELETE_RECORD, uid, type,
                                null, null, false, -1));
                    } else {
                        record.type = EventRecord.TYPE_DELETE_RECORD;
                        record.event = null;
                    }

                    if (this.worker == null) {
                        // create the worker thread
                        this.worker = new Handler();
                        try {
                            pool.execute(this.worker);
                        } catch (Exception e) {
                            Log.d(TAG, "rejected execution");
                        }
                    } else {
                        this.notify();
                    }
                }
            }
        }
    }

    private void onCotEvent(String euid, String etype,
            CotEvent event, String json, Bundle extra) {

        // TODO this should spawn its own thread

        if (euid.equals(deviceUID))
            return;

        final String from = extra.getString("from");
        if (from != null
                && (from.equals("StateSaver")
                        && !extra.getBoolean("BareBack"))) {
            return;
        }

        synchronized (this) {
            EventRecord record = this.eventsToProcess.get(euid);

            final boolean visible = extra.getBoolean("visible", true);
            final long lastUpdateTime = extra.getLong("lastUpdateTime", -1);

            if (extra.getBoolean("doNotRecreate")) {
                // The marker was most likely just removed and thus adding it
                // again would create a ghost
                // This fix has a twin in CotMapAdapter to prevent this event
                // from being reloaded as a MapItem
                // Could the opposite happen? Move a map item to a different group,
                // the delete gets processed after the persist...?
                if (record == null) {
                    // the record may or may not exist in the statesaver --
                    // allow it to propagate through to the processing
                    // thread and drop it there if no record exists
                    this.eventsToProcess
                            .put(euid,
                                    record = new EventRecord(
                                            EventRecord.TYPE_UPDATE_ONLY_RECORD,
                                            euid,
                                            etype, event, json, visible,
                                            lastUpdateTime));
                } else if (record.type != EventRecord.TYPE_DELETE_RECORD) {
                    // the pending record is wrap/update or update only;
                    // overwrite the contents while retaining the type
                    record.euid = euid;
                    record.etype = etype;
                    record.event = event;
                    record.json = json;
                    record.visible = visible;
                    record.lastUpdateTime = lastUpdateTime;
                } else { // record.type == EventRecord.TYPE_DELETE_RECORD
                    // the record is marked for deletion, ignore the update
                    return;
                }
            } else if (record == null) {
                // overwrite the event for the UID with the latest
                this.eventsToProcess.put(euid,
                        record = new EventRecord(
                                EventRecord.TYPE_CREATE_UPDATE_RECORD, euid,
                                etype,
                                event, json, visible, lastUpdateTime));
            } else {
                record.type = EventRecord.TYPE_CREATE_UPDATE_RECORD;
                record.event = event;
                record.euid = euid;
                record.etype = etype;
                record.json = json;
                record.visible = visible;
                record.lastUpdateTime = lastUpdateTime;
            }

            if (this.worker == null) {
                // start the worker thread
                this.worker = new Handler();
                try {
                    pool.execute(this.worker);
                } catch (Exception e) {
                    Log.d(TAG, "rejected execution");
                }
            } else {
                // notify the worker thread to start processing
                this.notify();
            }
        }
    }

    public void dispose() {
        Log.d(TAG, "shutdown");
        synchronized (StateSaver.getStateSaverDatabaseLock()) {
            synchronized (this) {
                if (this.worker != null) {
                    this.worker = null;
                    this.notify();
                }
            }
            if (this.deleteStmt != null) {
                this.deleteStmt.close();
                this.deleteStmt = null;
            }
            if (this.insertStmt != null) {
                this.insertStmt.close();
                this.insertStmt = null;
            }
            if (this.updateStmt != null) {
                this.updateStmt.close();
                this.updateStmt = null;
            }
        }
        pool.shutdownNow();
    }

    private void checkQueryArgs(int num) {
        if (this.queryArgs == null || this.queryArgs.length < num)
            this.queryArgs = new String[num];
    }

    private final String deviceUID;
    private final ExecutorService pool = Executors
            .newSingleThreadExecutor(new NamedThreadFactory(
                    "StateSaverListenerPool"));
    private String[] queryArgs;

    /**************************************************************************/

    private static class EventRecord {
        final static int TYPE_CREATE_UPDATE_RECORD = 0;
        final static int TYPE_UPDATE_ONLY_RECORD = 1;
        final static int TYPE_DELETE_RECORD = 2;

        public CotEvent event;
        public String euid;
        public String etype;
        public String json;
        public boolean visible;
        public long lastUpdateTime;
        public int type;

        EventRecord(int type, String euid, String etype, CotEvent event,
                String json, boolean visible, long lastUpdateTime) {
            this.type = type;
            this.euid = euid;
            this.etype = etype;
            this.event = event;
            this.json = json;
            this.visible = visible;
            this.lastUpdateTime = lastUpdateTime;
        }
    }
}
