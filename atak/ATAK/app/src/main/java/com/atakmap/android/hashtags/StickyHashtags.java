
package com.atakmap.android.hashtags;

import com.atakmap.android.hashtags.util.HashtagSet;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.statesaver.StateSaver;
import com.atakmap.android.util.LimitingThread;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.StatementIface;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Manages sticky hashtags
 */
public class StickyHashtags implements Runnable, MapEventDispatchListener {

    private static final String TAG = "StickyHashtags";

    private static final String TABLE_TAGS = "sticky_hashtags";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_TAG = "tag";

    private static final int RUN_INTERVAL = 500;

    private static StickyHashtags _instance;

    public static StickyHashtags getInstance() {
        return _instance;
    }

    private final MapView _mapView;
    private final HashtagSet _stickyTags;
    private final HashtagSet _dbTags;
    private final LimitingThread _thread;

    private boolean _initialized;

    public StickyHashtags(MapView mapView) {
        _instance = this;
        _mapView = mapView;
        _stickyTags = new HashtagSet();
        _dbTags = new HashtagSet();
        _thread = new LimitingThread("StickyHashtags", this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_ADDED, this);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_REMOVED, this);

        _thread.exec();
    }

    public void dispose() {
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_ADDED, this);
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_REMOVED, this);
        _thread.dispose(false);
    }

    @Override
    public void onMapEvent(MapEvent event) {
        String type = event.getType();
        MapItem item = event.getItem();

        if (type.equals(MapEvent.ITEM_ADDED)) {
            // Add sticky hashtags
            String entry = item.getMetaString("entry", null);
            if (!FileSystemUtils.isEquals(entry, "user")
                    || !item.hasMetaValue("archive"))
                return;

            Collection<String> tags = item.getHashtags();
            List<String> stickyTags = getTags();
            tags.addAll(stickyTags);
            item.setHashtags(tags);
        } else if (type.equals(MapEvent.ITEM_REMOVED)) {
            HashtagManager.getInstance().unregisterContent(item);
        }
    }

    @Override
    public void run() {
        if (!_initialized) {
            init();
            HashtagSet tags = loadTags();
            synchronized (_stickyTags) {
                _stickyTags.addAll(tags);
                _dbTags.addAll(tags);
            }
            _initialized = true;
        } else {
            persistTags();
        }
        try {
            Thread.sleep(RUN_INTERVAL);
        } catch (InterruptedException ignore) {
        }
    }

    private void init() {
        synchronized (StateSaver.getStateSaverDatabaseLock()) {
            DatabaseIface db = StateSaver.getInstance().getStateSaverDatabase();
            db.beginTransaction();
            CursorIface c = null;
            try {
                db.execute("CREATE TABLE IF NOT EXISTS " + TABLE_TAGS
                        + " (" + COLUMN_ID
                        + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + COLUMN_TAG + " TEXT)", null);
                db.setTransactionSuccessful();
            } catch (Exception e) {
                Log.e(TAG, "Failed to load sticky hashtags");
            } finally {
                try {
                    if (c != null)
                        c.close();
                } catch (Exception ignored) {
                }
            }
            db.endTransaction();
        }
    }

    /**
     * Set sticky tags
     * @param tags Tags
     */
    public void setTags(Collection<String> tags) {
        synchronized (_stickyTags) {
            _stickyTags.clear();
            _stickyTags.addAll(tags);
        }
        _thread.exec();
    }

    /**
     * Get sticky tags
     * @return Tags
     */
    public List<String> getTags() {
        synchronized (_stickyTags) {
            List<String> tags = new ArrayList<>(_stickyTags);
            Collections.sort(tags, HashtagManager.SORT_BY_NAME);
            return tags;
        }
    }

    private HashtagSet loadTags() {
        HashtagSet tags = new HashtagSet();
        synchronized (StateSaver.getStateSaverDatabaseLock()) {
            DatabaseIface db = StateSaver.getInstance().getStateSaverDatabase();
            db.beginTransaction();
            CursorIface c = null;
            try {
                c = db.query("SELECT " + COLUMN_TAG + " FROM " + TABLE_TAGS,
                        null);
                while (c != null && c.moveToNext())
                    tags.add(c.getString(c.getColumnIndex(COLUMN_TAG)));
                db.setTransactionSuccessful();
            } catch (Exception e) {
                Log.e(TAG, "Failed to load sticky hashtags");
            } finally {
                try {
                    if (c != null)
                        c.close();
                } catch (Exception ignored) {
                }
            }
            db.endTransaction();
        }
        return tags;
    }

    private void persistTags() {
        HashtagSet newTags, addTags, removeTags;
        synchronized (_stickyTags) {
            newTags = new HashtagSet(_stickyTags);
            addTags = new HashtagSet(_stickyTags);
            removeTags = new HashtagSet(_dbTags);
            _dbTags.clear();
            _dbTags.addAll(addTags);
        }
        synchronized (StateSaver.getStateSaverDatabaseLock()) {
            addTags.removeAll(removeTags);
            removeTags.removeAll(newTags);
            DatabaseIface db = StateSaver.getInstance().getStateSaverDatabase();
            db.beginTransaction();
            try {
                // Add new tags
                for (String tag : addTags)
                    insertTagNoSync(db, tag);
                // Remove old tags
                for (String tag : removeTags)
                    removeTagNoSync(db, tag);
                db.setTransactionSuccessful();
            } catch (Exception e) {
                Log.e(TAG, "Failed to persist sticky hashtags", e);
            }
            db.endTransaction();
        }
    }

    private void insertTagNoSync(DatabaseIface db, String tag) {
        StatementIface s = null;
        try {
            s = db.compileStatement("INSERT INTO " + TABLE_TAGS
                    + " (" + COLUMN_TAG + ") VALUES (?)");
            s.bind(1, tag);
            s.execute();
        } catch (Exception e) {
            Log.e(TAG, "Failed to insert tag " + tag, e);
        } finally {
            try {
                if (s != null)
                    s.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void removeTagNoSync(DatabaseIface db, String tag) {
        StatementIface s = null;
        try {
            s = db.compileStatement("DELETE FROM " + TABLE_TAGS
                    + " WHERE " + COLUMN_TAG + " = ?");
            s.bind(1, tag);
            s.execute();
        } catch (Exception e) {
            Log.e(TAG, "Failed to insert tag " + tag, e);
        } finally {
            try {
                if (s != null)
                    s.close();
            } catch (Exception ignored) {
            }
        }
    }
}
